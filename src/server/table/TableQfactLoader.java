package server.table;

import config.Configuration;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import util.FileUtils;
import util.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class TableQfactLoader {
    public static final double LINKING_THRESHOLD = -1; // 0.70;

    private static boolean LOADED = false;
    private static ArrayList<QfactLight[]> QFACTS;

    public static ArrayList<QfactLight[]> load() {
        return load(true);
    }

    public static synchronized ArrayList<QfactLight[]> load(boolean loadExplainStr) {
        if (LOADED) {
            return QFACTS;
        }
        ArrayList<QfactLight> qFacts = new ArrayList<>();
        String wikiFile = Configuration.get("chroniclemap.table.annotation_file.wikipedia");
        String tablemFile = Configuration.get("chroniclemap.table.annotation_file.tablem");

        for (String file : Arrays.asList(tablemFile, wikiFile))
            for (String line : FileUtils.getLineStream(file, "UTF-8")) {
                Table table = Gson.fromJson(line, Table.class);
                // for all Qfacts
                for (int qCol = 0; qCol < table.nColumn; ++qCol) {
                    if (!table.isNumericColumn[qCol] || (LINKING_THRESHOLD != -1 && table.quantityToEntityColumnScore[qCol] < LINKING_THRESHOLD)) {
                        continue;
                    }

                    for (int row = 0; row < table.nDataRow; ++row) {
                        QuantityLink ql = table.data[row][qCol].getRepresentativeQuantityLink();
                        if (ql == null) {
                            continue;
                        }
                        EntityLink el = table.data[row][table.quantityToEntityColumn[qCol]].getRepresentativeEntityLink();
                        if (el == null) {
                            continue;
                        }

                        QfactLight f = new QfactLight();
                        f.tableId = table._id;
                        f.linkingScore = table.quantityToEntityColumnScore[qCol];
                        f.entity = el.target;
                        f.entitySpan = el.text;

                        f.quantity = ql.quantity.toString();
                        f.quantitySpan = ql.text;

                        f.headerContext = table.getCombinedHeader(qCol);
                        f.headerUnitSpan = table.getHeaderUnitSpan(qCol);

                        f.row = row;
                        f.qCol = qCol;
                        f.eCol = table.quantityToEntityColumn[qCol];

                        if (loadExplainStr) {
                            f.explainQfactIds = table.QfactMatchingStr[row][qCol];
                        }

                        qFacts.add(f);
                    }
                }
            }
        Collections.sort(qFacts, (o1, o2) -> o1.entity.compareTo(o2.entity));

        QFACTS = new ArrayList<>();
        for (int i = 0; i < qFacts.size(); ++i) {
            String entity = qFacts.get(i).entity;
            int j = i;
            while (j < qFacts.size() - 1 && (qFacts.get(j + 1)).entity.equals(entity)) {
                ++j;
            }
            QFACTS.add(qFacts.subList(i, j + 1).toArray(new QfactLight[0]));
            i = j;
        }
        LOADED = true;
        return QFACTS;
    }

    public static void main(String[] args) {
        load();
    }
}
