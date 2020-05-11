package server.table;

import model.quantity.QuantityDomain;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import org.junit.Assert;
import storage.table.index.TableIndex;
import util.FileUtils;
import util.Gson;

import java.util.*;

class TableIndexLoader {
    private HashMap<String, TableIndex> tableId2Index;

    public TableIndexLoader(Set<String> tableIds) {
        tableId2Index = new HashMap<>();

        String wikiFile = "/GW/D5data-12/hvthinh/TabQs/to_be_indexed/wiki.gz";
        String tablemFile = "/GW/D5data-12/hvthinh/TabQs/to_be_indexed/tablem.gz";
        for (String file : Arrays.asList(tablemFile, wikiFile))
            for (String line : FileUtils.getLineStream(file, "UTF-8")) {
                TableIndex index = Gson.fromJson(line, TableIndex.class);
                // omit some information
                index.tableText = null;
                if (tableIds.contains(index.table._id)) {
                    tableId2Index.put(index.table._id, index);
                }
            }
    }

    public TableIndex getTableIndex(String tableId) {
        return tableId2Index.get(tableId);
    }
}

public class TableQfactLoader {
    public static final double LINKING_THRESHOLD = 0.70;

    private static boolean LOADED = false;
    private static ArrayList<QfactLight> QFACTS;

    public static synchronized ArrayList<QfactLight> load() {
        if (LOADED) {
            return QFACTS;
        }
        QFACTS = new ArrayList<>();
        String wikiFile = "/GW/D5data-12/hvthinh/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.tables+id_annotation+linking.gz";
        String tablemFile = "/GW/D5data-11/hvthinh/TABLEM/all/all+id.annotation+linking.gz";

        HashSet<String> tableIds = new HashSet<>();

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
                        tableIds.add(f.tableId = table._id);
                        f.entity = el.target;
                        f.entitySpan = el.text;

                        f.quantity = ql.quantity.toString();
                        f.quantitySpan = ql.text;
                        f.domain = QuantityDomain.getFineGrainedDomain(ql.quantity);

                        f.headerContext = table.getCombinedHeader(qCol);
                        f.headerUnitSpan = table.getHeaderUnitSpan(qCol);

                        f.row = row;
                        f.qCol = qCol;
                        f.eCol = table.quantityToEntityColumn[qCol];

                        QFACTS.add(f);
                    }
                }
            }
        Collections.sort(QFACTS, (o1, o2) -> o1.entity.compareTo(o2.entity));

        TableIndexLoader loader = new TableIndexLoader(tableIds);
        for (QfactLight f : QFACTS) {
            f.tableIndex = loader.getTableIndex(f.tableId);
            Assert.assertTrue(f.tableIndex != null);
        }
        LOADED = true;
        return QFACTS;
    }

    public static void main(String[] args) {
    }
}
