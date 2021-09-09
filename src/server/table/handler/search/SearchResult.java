package server.table.handler.search;

import model.quantity.QuantityConstraint;
import org.json.JSONArray;
import pipeline.table.QfactTaxonomyGraph;
import server.table.QfactLight;
import server.table.ResultInstance;
import storage.table.index.TableIndex;
import storage.table.index.TableIndexStorage;

import java.util.ArrayList;
import java.util.HashMap;

public class SearchResult implements Cloneable {
    public static final transient HashMap<Integer, QfactTaxonomyGraph.EntityTextQfact> BG_TEXT_QFACT_MAP = QfactTaxonomyGraph.loadBackgroundTextQfactMap();

    public String verdict; // "OK" is good, otherwise the error message.
    public String fullQuery; // optional
    public String typeConstraint;
    public String contextConstraint;
    public QuantityConstraint quantityConstraint;
    public String evalDomain; // For evaluation
    public int numResults;
    public ArrayList<ResultInstance> topResults;

    public HashMap<String, TableIndex> tableId2Index;

    public String encode() {
        return evalDomain + "_" + (typeConstraint.replace(' ', '-')
                + "_" + contextConstraint.replace(' ', '-')
                + "_" + quantityConstraint.phrase.replace(' ', '-')).replace('/', '-');
    }

    // below are recall-based metrics computed in case groundtruth is provided.
    public Double RR, AP, RECALL, RECALL_10, AP_10;

    // below are pagination-info
    public int nResultsPerPage;
    public int nPage;

    public int pageIdx, startIdx; // 0-based index

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public void populateTableIndexesFromTopResults() {
        tableId2Index = new HashMap<>();
        for (ResultInstance ri : topResults) {
            for (ResultInstance.SubInstance si : ri.subInstances) {
                if (!tableId2Index.containsKey(si.qfact.tableId)) {
                    tableId2Index.put(si.qfact.tableId, TableIndexStorage.get(si.qfact.tableId));
                }
                if (si.qfact.explainQfactIds != null && !si.qfact.explainQfactIds.equals("null")) {
                    try {
                        si.qfact = (QfactLight) si.qfact.clone(); // IMPORTANT!
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }

                    JSONArray bgQfactIds = new JSONArray(si.qfact.explainQfactIds);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < bgQfactIds.length(); ++i) {
                        if (sb.length() > 0) {
                            sb.append("\r\n");
                        }
                        sb.append(BG_TEXT_QFACT_MAP.get(bgQfactIds.getInt(i)).toString());
                    }
                    si.qfact.explainStr = sb.toString();
                }
            }
        }
    }
}
