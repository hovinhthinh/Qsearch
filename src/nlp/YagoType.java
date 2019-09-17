package nlp;

import org.json.JSONArray;
import util.FileUtils;

import java.util.*;
import java.util.logging.Logger;

public class YagoType {
    public static final Logger LOGGER = Logger.getLogger(YagoType.class.getName());
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

    private static final HashMap<String, int[]> entity2Types;
    private static final ArrayList<String> index2Type;

    static {
        LOGGER.info(String.format("loading yago type compact from %s", YAGO_TYPE_COMPACT_PATH));
        entity2Types = new HashMap<>();
        index2Type = new ArrayList<>();
        HashMap<String, Integer> type2Index = new HashMap<>();

        for (String line : FileUtils.getLineStream(YAGO_TYPE_COMPACT_PATH, "UTF-8")) {
            String[] arr = line.split("\t");
            JSONArray types = new JSONArray(arr[1]);
            int[] typesInt = new int[types.length()];
            for (int i = 0; i < types.length(); ++i) {
                String type = types.getString(i);
                Integer index = type2Index.get(type);
                if (index == null) {
                    type2Index.put(type, typesInt[i] = type2Index.size());
                    index2Type.add(type);
                } else {
                    typesInt[i] = index;
                }
            }
            entity2Types.put(arr[0], typesInt);
        }
        index2Type.trimToSize();
    }

    public static final List<String> getSpecificTypes(String entity) { // entity: <Cris_Ronaldo>
        int[] l = entity2Types.get(entity);
        if (l == null) {
            return null;
        }
        List<String> types = new ArrayList<>(l.length);
        for (int i : l) {
            String t = index2Type.get(i);
            if (!BLOCKED_GENERAL_TYPES.contains(t)) {
                types.add(t);
            }
        }
        return types;
    }


    public static void main(String[] args) {
        System.out.println(getSpecificTypes("<Cristiano_Ronaldo>"));
    }
}
