package storage.table.index;

import config.Configuration;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.openhft.chronicle.map.ChronicleMap;
import util.FileUtils;
import util.Gson;
import util.ObjectCompressor;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TableIndexStorage {
    public static final String TABLE_INDEX_FILE = Configuration.get("chroniclemap.table.index_file");

    private static ChronicleMap<String, byte[]> INDEX = null;

    private static final int TABLE_INDEX_CACHE_SIZE = 10000;
    private static final Object2ObjectLinkedOpenHashMap<String, TableIndex> TABLE_INDEX_CACHE = new Object2ObjectLinkedOpenHashMap<>(TABLE_INDEX_CACHE_SIZE);

    static {
        try {
            INDEX = ChronicleMap
                    .of(String.class, byte[].class)
                    .averageKeySize(20)
                    .averageValueSize(1500)
                    .entries(3000000)
                    .createPersistedTo(new File(TABLE_INDEX_FILE));
        } catch (IOException e) {
            e.printStackTrace();
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
                INDEX.put(ti.table._id, ObjectCompressor.compressSerializableIntoByteArray(ti));
            }
        INDEX.close();
    }

    public static void main(String[] args) throws IOException {
        createChronicleMap();
    }

    public static TableIndex get(String tableId) {
        TableIndex ti;
        synchronized (TABLE_INDEX_CACHE) {
            ti = TABLE_INDEX_CACHE.getAndMoveToFirst(tableId);
        }
        if (ti != null) {
            return ti;
        }

        ti = ObjectCompressor.decompressByteArrayIntoSerializable(INDEX.get(tableId), TableIndex.class);
        if (ti != null) {
            synchronized (TABLE_INDEX_CACHE) {
                TABLE_INDEX_CACHE.putAndMoveToFirst(tableId, ti);
                if (TABLE_INDEX_CACHE.size() > TABLE_INDEX_CACHE_SIZE) {
                    TABLE_INDEX_CACHE.removeLast();
                }
            }
        }
        return ti;
    }
}
