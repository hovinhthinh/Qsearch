package nlp;

import org.json.JSONArray;
import util.FileUtils;
import util.Pair;

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
    private static final ArrayList<Pair<String, Double>> index2Type; // store type & itf (inverse type frequency)

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
                    index2Type.add(new Pair(type, 1.0));
                } else {
                    typesInt[i] = index;
                    index2Type.get(index).second += 1.0;
                }
            }
            entity2Types.put(arr[0], typesInt);
        }
        index2Type.trimToSize();
        // Compute itf
        for (int i = 0; i < index2Type.size(); ++i) {
            Pair<String, Double> typeAndFreq = index2Type.get(i);
            // (Robertson version)
//            typeAndFreq.second = Math.max(0.0001, Math.log10((entity2Types.size() - typeAndFreq.second + 0.5) / (typeAndFreq.second + 0.5)));
            // normal
            typeAndFreq.second = Math.max(0.0001, Math.log(entity2Types.size() / (typeAndFreq.second + 1.0)));
        }
        LOGGER.info(String.format("loaded total %d types", index2Type.size()));

    }

    // return list of types and their ITF.
    public static final List<Pair<String, Double>> getTypes(String entity, boolean specificOnly) { // entity: <Cris_Ronaldo>
        int[] l = entity2Types.get(entity);
        if (l == null) {
            return null;
        }
        List<Pair<String, Double>> types = new ArrayList<>(l.length);
        for (int i : l) {
            Pair<String, Double> t = index2Type.get(i);
            if (!specificOnly || !BLOCKED_GENERAL_TYPES.contains(t.first)) {
                types.add(t);
            }
        }
        return types;
    }

    // return list of types and their ITF.
    public static final List<Pair<String, Double>> getTypes(String entity) { // entity: <Cris_Ronaldo>
        return getTypes(entity, false);
    }

    public static final boolean entityExists(String entity) { // entity: <Cris_Ronaldo>
        return entity2Types.containsKey(entity);
    }


    public static void main(String[] args) {
        System.out.println(getTypes("<Barack_Obama>"));
    }
}
