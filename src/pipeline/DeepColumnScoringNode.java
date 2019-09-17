package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import org.json.JSONArray;
import pipeline.deep.DeepScoringClient;
import util.FileUtils;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DeepColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(DeepColumnScoringNode.class.getName());
    private static final String YAGO_TYPE_COMPACT_PATH = "./resources/yagoTransitiveTypeCompact.tsv.gz";
    private static final HashSet<String> BLOCKED_GENERAL_TYPES = new HashSet<>(Arrays.asList(
            "owl thing",
            "physical entity",
            "object",
            "whole",
            "yagolegalactorgeo",
            "yagolegalactor",
            "yagopermanentlylocatedentity",
            "living thing",
            "organism",
            "causal agent",
            "person",
            "people",
            "people associated with buildings and structures",
            "people associated with places",
            "abstraction",
            "yagogeoentity",
            "artifact",
            "european people",
            "objects",
            "physical objects"
    ));

    private static final HashMap<String, List<String>> entity2Types;

    static {
        LOGGER.info(String.format("loading yago type compact from %s", YAGO_TYPE_COMPACT_PATH));
        entity2Types = new HashMap<>();
        for (String line : FileUtils.getLineStream(YAGO_TYPE_COMPACT_PATH, "UTF-8")) {
            String[] arr = line.split("\t");
            List<String> types = new JSONArray(arr[1]).toList().stream().map(x -> x.toString()).collect(Collectors.toList());
            entity2Types.put(arr[0], types);
        }
    }

    private double minConf;
    private DeepScoringClient scoringClient;

    public DeepColumnScoringNode(double minConf) {
        this.minConf = minConf;
        this.scoringClient = new DeepScoringClient();
    }

    private static final List<String> getSpecificTypes(String entity) { // entity: <Cris_Ronaldo>
        List<String> l = entity2Types.get(entity);
        if (l == null) {
            return null;
        }
        return l.stream().filter(x -> (!BLOCKED_GENERAL_TYPES.contains(x))).collect(Collectors.toList());
    }

    @Override
    public boolean process(Table table) {
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

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
                    List<String> types = getSpecificTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">");
                    if (types == null) {
                        continue;
                    }
                    double score = Math.max(
                            Collections.max(scoringClient.getScores(types, table.getCombinedHeader(pivotCol))),
                            Collections.max(scoringClient.getScores(types, table.header[table.nHeaderRow - 1][pivotCol].text))
                    );
                    entityLinkingConf = (entityLinkingConf == -1 ? score : Math.min(entityLinkingConf, score));
                }

                double totalConf = Math.max(headerLinkingConf, entityLinkingConf);
                if (totalConf >= minConf) {
                    if (targetCol == -1 || totalConf > linkingConf) {
                        targetCol = col;
                        linkingConf = totalConf;
                    }
                }
            }

            table.quantityToEntityColumn[pivotCol] = targetCol;
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println(entity2Types.get("<Cristiano_Ronaldo>"));
    }
}
