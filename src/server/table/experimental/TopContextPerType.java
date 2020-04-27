package server.table.experimental;

import util.Concurrent;
import util.FileUtils;
import util.Pair;
import yago.TaxonomyGraph;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Deprecated
public class TopContextPerType {
    static HashMap<String, HashSet<String>> entity2Token;
    static HashMap<String, LinkedList<Integer>> textualizedType2Ids;

    static TaxonomyGraph graph = TaxonomyGraph.getDefaultGraphInstance();

    static {
        ArrayList<EntityQfactHandler.Qfact> qfacts = EntityQfactHandler.qfacts;
        entity2Token = new HashMap<>();
        for (int i = 0; i < qfacts.size(); ++i) {
            int j = i;
            while (j < qfacts.size() - 1 && qfacts.get(j + 1).entity.equals(qfacts.get(j).entity)) {
                ++j;
            }
            String entity = "<" + qfacts.get(i).entity.substring(5) + ">";
            HashSet<String> tokenSet = new HashSet<>();
            for (int k = i; k <= j; ++k) {
                String ctx = qfacts.get(k).context;
                tokenSet.addAll(Arrays.asList(ctx.substring(1, ctx.length() - 1).split(", ")));
            }
            entity2Token.put(entity, tokenSet);
            i = j;
        }

        textualizedType2Ids = new HashMap<>();
        for (int i = 0; i < graph.nTypes; ++i) {
            textualizedType2Ids.putIfAbsent(graph.id2TextualizedType.get(i), new LinkedList<>());
            textualizedType2Ids.get(graph.id2TextualizedType.get(i)).add(i);
        }
    }

    public static ArrayList<Pair<String, Integer>> processType(String textualizedType) {
        List<Integer> tIds = textualizedType2Ids.get(textualizedType);
        if (tIds == null) {
            return null;
        }

        HashMap<String, Integer> map = new HashMap<>();
        for (int eId : graph.getEntitySetForTypes(tIds)) {
            HashSet<String> tokens = entity2Token.get(graph.id2Entity.get(eId));
            if (tokens != null) {
                for (String t : tokens) {
                    map.put(t, map.getOrDefault(t, 0) + 1);
                }
            }
        }

        ArrayList<Pair<String, Integer>> res = map.entrySet().stream()
                .sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                .filter(o -> o.getValue() >= 10)
                .map(o -> new Pair<>(o.getKey(), o.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (res.size() > 100) {
            res.subList(100, res.size()).clear();
        }
        return res;
    }

    public static void main(String[] args) {
        ArrayList<Pair<String, Integer>> typeToFreq = new ArrayList<>();
        for (String line : FileUtils.getLineStream("./data/type_table_wiki+tablem.gz", "UTF-8")) {
            String[] arr = line.split("\t");
            int freq = Integer.parseInt(arr[1]);
            if (freq >= 10) {
                typeToFreq.add(new Pair(arr[0], freq));
            }
        }
        Collections.sort(typeToFreq, (a, b) -> b.second.compareTo(a.second));
        AtomicInteger cnt = new AtomicInteger(0);

        PrintWriter out = FileUtils.getPrintWriter("./data/type_table_wiki+tablem_context.gz");
        Concurrent.runAndWait(() -> {
            int i;
            while ((i = cnt.getAndIncrement()) < typeToFreq.size()) {
                try {
                    ArrayList<String> topContext = processType(typeToFreq.get(i).first).stream().map(o -> o.first).collect(Collectors.toCollection(ArrayList::new));
                    if (topContext.size() > 0) {
                        String output = String.format("%s\t%d\t%s", typeToFreq.get(i).first, typeToFreq.get(i).second, topContext.toString());
                        synchronized (out) {
                            System.out.println(output);
                            out.println(output);
                        }
                    }
                } catch (NullPointerException e) {
                    System.out.println("Err: " + typeToFreq.get(i).first);
                    continue;
                }
            }
        }, 32);

        out.close();
    }
}
