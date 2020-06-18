package storage.table.index;

import net.openhft.chronicle.map.ChronicleMap;
import util.FileUtils;
import util.Gson;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TableIndexStorage {
    public static final String TABLE_INDEX_FILE = "./table.index";

    private static ChronicleMap<String, TableIndex> INDEX = null;

    static {
        try {
            INDEX = ChronicleMap
                    .of(String.class, TableIndex.class)
                    .averageKeySize(20)
                    .averageValueSize(3000)
                    .entries(3500000)
                    .createPersistedTo(new File(TABLE_INDEX_FILE));
        } catch (IOException e) {
        }
    }

    public static void createChronicleMap() {
        INDEX.clear();
        String wikiFile = "/GW/D5data-12/hvthinh/TabQs/to_be_indexed/wiki.gz";
        String tablemFile = "/GW/D5data-12/hvthinh/TabQs/to_be_indexed/tablem.gz";
        for (String file : Arrays.asList(tablemFile, wikiFile))
            for (String line : FileUtils.getLineStream(file, "UTF-8")) {
                TableIndex ti = Gson.fromJson(line, TableIndex.class);
                // omit some information
                ti.tableText = null;
                INDEX.put(ti.table._id, ti);
            }
        INDEX.close();
    }

    public static void main(String[] args) throws IOException {
        createChronicleMap();
    }

    public static TableIndex get(String tableId) {
        return INDEX.get(tableId);
    }
}
