package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import nlp.YagoType;
import pipeline.deep.DeepScoringClient;
import util.Pair;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Link quantity columns to entity columns, return false if there is no quantity column.
public class DeepColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(DeepColumnScoringNode.class.getName());
    public static final int MIN_MAX_INFERENCE = 0;
    public static final int TYPE_SET_INFERENCE = 1;
    public static final int JOINT_INFERENCE = 2;

    private int inferenceMode;
    private DeepScoringClient scoringClient;

    public DeepColumnScoringNode(int inferenceMode) {
        this.inferenceMode = inferenceMode;
        this.scoringClient = new DeepScoringClient();
    }

    public DeepColumnScoringNode() {
        this(JOINT_INFERENCE);
    }

    @Override
    public boolean process(Table table) {
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

        table.quantityToEntityColumnScore = new double[table.nColumn];
        Arrays.fill(table.quantityToEntityColumnScore, -1.0);

        if (inferenceMode == MIN_MAX_INFERENCE || inferenceMode == TYPE_SET_INFERENCE) {
            return directInference(table);
        } else if (inferenceMode == JOINT_INFERENCE) {
            return jointInference(table);
        } else {
            throw new RuntimeException("Not implemented");
        }
    }

    public boolean jointInference(Table table) {
        throw new RuntimeException("Not implemented");
    }

    public boolean directInference(Table table) {
        boolean result = false;
        // loop for quantity columns.

        double[] keyColumnScores = new double[table.nColumn];

        int nNumericCols = 0;
        for (int pivotCol = 0; pivotCol < table.nColumn; ++pivotCol) {
            if (!table.isNumericColumn[pivotCol]) {
                continue;
            }
            ++nNumericCols;
            int targetCol = -1;
            double linkingConf = -1;
            // loop for entity columns.
            for (int col = 0; col < table.nColumn; ++col) {
                if (!table.isEntityColumn[col]) {
                    continue;
                }

                double totalConf = -1;
                if (inferenceMode == MIN_MAX_INFERENCE) {
                    totalConf = inferMinMax(table, pivotCol, col);
                } else if (inferenceMode == TYPE_SET_INFERENCE) {
                    totalConf = inferTypeSet(table, pivotCol, col);
                } else {
                    throw new RuntimeException("Not implemented");
                }
                if (targetCol == -1 || totalConf > linkingConf) {
                    targetCol = col;
                    linkingConf = totalConf;
                    result = true;
                }
                keyColumnScores[col] += totalConf;
            }

            table.quantityToEntityColumn[pivotCol] = targetCol;
            table.quantityToEntityColumnScore[pivotCol] = linkingConf;
        }

        for (int col = 0; col < table.nColumn; ++col) {
            if (!table.isEntityColumn[col]) {
                continue;
            }
            if (nNumericCols > 0) {
                keyColumnScores[col] /= nNumericCols;
            }
            if (table.keyColumn == -1 || keyColumnScores[col] > table.keyColumnScore) {
                table.keyColumn = col;
                table.keyColumnScore = keyColumnScores[col];
            }
        }
        return result;
    }


    private double inferMinMax(Table table, int qCol, int eCol) {
        // header conf: max from combined and last cell only.
        double headerLinkingConf = scoringClient.getScore(table.getCombinedHeader(eCol), table.getCombinedHeader(qCol));
        if (table.nHeaderRow > 1) {
            headerLinkingConf = Math.max(
                    headerLinkingConf,
                    scoringClient.getScore(table.header[table.nHeaderRow - 1][eCol].text, table.header[table.nHeaderRow - 1][qCol].text));
        }
        // entity conf: min from each detected entity.
        double entityLinkingConf = -1;
        for (int i = 0; i < table.nDataRow; ++i) {
            EntityLink e = table.data[i][eCol].getRepresentativeEntityLink();
            if (e == null) {
                continue;
            }
            List<String> types = YagoType.getTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">", true)
                    .stream().map(o -> o.first).collect(Collectors.toList());
            if (types == null) {
                continue;
            }
            ArrayList<Double> scrs = scoringClient.getScores(types, table.getCombinedHeader(qCol));
            if (table.nHeaderRow > 1) {
                scrs.addAll(scoringClient.getScores(types, table.header[table.nHeaderRow - 1][qCol].text));
            }
            if (scrs.isEmpty()) {
                continue;
            }
            double score = Collections.max(scrs);
            entityLinkingConf = (entityLinkingConf == -1 ? score : Math.min(entityLinkingConf, score));
        }

        return Math.max(headerLinkingConf, entityLinkingConf);
    }

    // sum(freq(t) * itf(t) * dist(t,q))
    private double inferTypeSet(Table table, int qCol, int eCol) {
        HashMap<String, Double> type2Freq = new HashMap<>();
        HashMap<String, Double> type2Itf = new HashMap<>();

        for (int i = 0; i < table.nDataRow; ++i) {
            EntityLink e = table.data[i][eCol].getRepresentativeEntityLink();
            if (e == null) {
                continue;
            }
            List<Pair<String, Double>> types = YagoType.getTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">", true);
            for (Pair<String, Double> p : types) {
                type2Itf.put(p.first, p.second);
                type2Freq.put(p.first, type2Freq.getOrDefault(p.first, 0.0) + 1.0 / types.size());
            }
        }
        // Normalize freq.
        double totalFreq = type2Freq.entrySet().stream().collect(Collectors.summingDouble(o -> o.getValue()));
        for (String t : type2Freq.keySet()) {
            type2Freq.put(t, type2Freq.get(t) / totalFreq);
        }

        ArrayList<String> types = type2Freq.entrySet().stream().map(o -> o.getKey()).collect(Collectors.toCollection(ArrayList::new));

        // combined quantity header
        ArrayList<Double> scrs = scoringClient.getScores(types, table.getCombinedHeader(qCol));
        double entityLinkingConf = 0;
        for (int i = 0; i < types.size(); ++i) {
            String t = types.get(i);
            entityLinkingConf += scrs.get(i) * type2Itf.get(t) * type2Freq.get(t);
        }
        // last quantity header
        if (table.nHeaderRow > 1) {
            scrs = scoringClient.getScores(types, table.header[table.nHeaderRow - 1][qCol].text);
            double entityLinkingConfLastHeader = 0;
            for (int i = 0; i < types.size(); ++i) {
                String t = types.get(i);
                entityLinkingConfLastHeader += scrs.get(i) * type2Itf.get(t) * type2Freq.get(t);
            }
            entityLinkingConf = Math.max(entityLinkingConf, entityLinkingConfLastHeader);
        }
        return entityLinkingConf;
    }
}
