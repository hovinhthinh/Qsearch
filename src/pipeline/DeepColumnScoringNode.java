package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import nlp.YagoType;
import pipeline.deep.DeepScoringClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DeepColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(DeepColumnScoringNode.class.getName());
    public static final int MIN_MAX_INFERENCE = 0;
    public static final int TYPE_SET_INFERENCE = 1;

    private int inferenceMode;
    private DeepScoringClient scoringClient;

    public DeepColumnScoringNode(int inferenceMode) {
        this.inferenceMode = inferenceMode;
        this.scoringClient = new DeepScoringClient();
    }

    public DeepColumnScoringNode() {
        this(TYPE_SET_INFERENCE);
    }

    @Override
    public boolean process(Table table) {
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

        table.quantityToEntityColumnScore = new double[table.nColumn];
        Arrays.fill(table.quantityToEntityColumnScore, -1.0);

        boolean result = false;
        // loop for quantity columns.
        for (int pivotCol = 0; pivotCol < table.nColumn; ++pivotCol) {
            if (!table.isNumericColumn[pivotCol]) {
                continue;
            }
            int targetCol = -1;
            double linkingConf = -1;
            // loop for entity columns.
            for (int col = 0; col < table.nColumn; ++col) {
                if (table.isNumericColumn[col]) {
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
            }

            table.quantityToEntityColumn[pivotCol] = targetCol;
            table.quantityToEntityColumnScore[pivotCol] = linkingConf;
        }
        return result;
    }

    private double inferMinMax(Table table, int qCol, int eCol) {
        // header conf: max from combined columns and last column only.
        double headerLinkingConf = Math.max(
                scoringClient.getScore(table.getCombinedHeader(eCol), table.getCombinedHeader(qCol)),
                scoringClient.getScore(table.header[table.nHeaderRow - 1][eCol].text, table.header[table.nHeaderRow - 1][qCol].text));
        // entity conf: min from each detected entity.
        double entityLinkingConf = -1;
        for (int i = 0; i < table.nDataRow; ++i) {
            EntityLink e = table.data[i][eCol].getRepresentativeEntityLink();
            if (e == null) {
                continue;
            }
            List<String> types = YagoType.getSpecificTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">").stream().map(o -> o.first).collect(Collectors.toList());
            if (types == null) {
                continue;
            }
            ArrayList<Double> srcs = scoringClient.getScores(types, table.getCombinedHeader(qCol));
            srcs.addAll(scoringClient.getScores(types, table.header[table.nHeaderRow - 1][qCol].text));
            if (srcs.isEmpty()) {
                continue;
            }
            double score = Collections.max(srcs);
            entityLinkingConf = (entityLinkingConf == -1 ? score : Math.min(entityLinkingConf, score));
        }

        return Math.max(headerLinkingConf, entityLinkingConf);
    }

    private double inferTypeSet(Table table, int qCol, int eCol) {
        throw new RuntimeException("Not implemented");
    }
}
