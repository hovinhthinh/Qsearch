package server.table.explain;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import yago.TaxonomyGraph;

public class TypeLiftingRestrictor {
    private static final TaxonomyGraph GRAPH = TaxonomyGraph.getDefaultGraphInstance();

    private int distLimit;
    private Int2IntLinkedOpenHashMap centralType2Distance;

    public TypeLiftingRestrictor(String centralEntity, int distLimit) {
        this.distLimit = distLimit;
        int eId = GRAPH.getEntityId(centralEntity);
        if (eId == -1) {
            throw new RuntimeException("Invalid runtime entity");
        }
        this.centralType2Distance = GRAPH.getType2DistanceMapForEntity(eId);
    }

    public boolean entityIsInProximity(String targetEntity) {
        int eId = GRAPH.getEntityId(targetEntity);
        if (eId == -1) {
            throw new RuntimeException("Invalid target entity");
        }
        Int2IntLinkedOpenHashMap tt2d = GRAPH.getType2DistanceMapForEntity(eId);
        for (Int2IntMap.Entry e : tt2d.int2IntEntrySet()) {
            if (e.getIntValue() >= distLimit) {
                break;
            }
            int distToCentral = centralType2Distance.get(e.getIntKey());
            if (distToCentral != -1 && distToCentral + e.getIntValue() <= distLimit) {
                return true;
            }
        }
        return false;
    }
}