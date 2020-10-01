package server.table.handler.search;

import model.quantity.QuantityConstraint;
import server.table.ResultInstance;
import storage.table.index.TableIndex;

import java.util.ArrayList;
import java.util.HashMap;

public class SearchResult {
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
    public Double RR, AP, RECALL, RECALL_10;
}
