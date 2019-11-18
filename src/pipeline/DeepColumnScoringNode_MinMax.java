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

@Deprecated
public class DeepColumnScoringNode_MinMax implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(DeepColumnScoringNode_MinMax.class.getName());

    private double minConf;
    private DeepScoringClient scoringClient;

    public DeepColumnScoringNode_MinMax(double minConf) {
        this.minConf = minConf;
        this.scoringClient = new DeepScoringClient();
    }

    @Override
    public boolean process(Table table) {
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

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

                // header conf: max from combined columns and last column only.
                double headerLinkingConf = Math.max(
                        scoringClient.getScore(table.getCombinedHeader(col), table.getCombinedHeader(pivotCol)),
                        scoringClient.getScore(table.header[table.nHeaderRow - 1][col].text, table.header[table.nHeaderRow - 1][pivotCol].text));
                // entity conf: min from each detected entity.
                double entityLinkingConf = -1;
                for (int i = 0; i < table.nDataRow; ++i) {
                    EntityLink e = table.data[i][col].getRepresentativeEntityLink();
                    if (e == null) {
                        continue;
                    }
                    List<String> types = YagoType.getSpecificTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">");
                    if (types == null) {
                        continue;
                    }
                    ArrayList<Double> srcs = scoringClient.getScores(types, table.getCombinedHeader(pivotCol));
                    srcs.addAll(scoringClient.getScores(types, table.header[table.nHeaderRow - 1][pivotCol].text));
                    if (srcs.isEmpty()) {
                        continue;
                    }
                    double score = Collections.max(srcs);
                    entityLinkingConf = (entityLinkingConf == -1 ? score : Math.min(entityLinkingConf, score));
                }

                double totalConf = Math.max(headerLinkingConf, entityLinkingConf);
                if (totalConf >= minConf) {
                    if (targetCol == -1 || totalConf > linkingConf) {
                        targetCol = col;
                        linkingConf = totalConf;
                        result = true;
                    }
                }
            }

            table.quantityToEntityColumn[pivotCol] = targetCol;
        }
        return result;
    }
}
