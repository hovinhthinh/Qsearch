package server.text.handler.search;

import model.quantity.QuantityConstraint;
import server.text.ResultInstance;

import java.util.ArrayList;

public class SearchResult implements Cloneable {
    public String verdict; // "OK" is good, otherwise the error message.
    public String fullQuery; // optional
    public String typeConstraint;
    public String contextConstraint;
    public QuantityConstraint quantityConstraint;
    public String evalDomain; // For evaluation
    public String matchingModel;
    public int numResults;
    public ArrayList<ResultInstance> topResults = new ArrayList<>();

    public String encode() {
        return evalDomain + "_" + (typeConstraint.replace(' ', '-')
                + "_" + contextConstraint.replace(' ', '-')
                + "_" + quantityConstraint.phrase.replace(' ', '-')).replace('/', '-');
    }


    // below are recall-based metrics computed in case groundtruth is provided.
    public Double RR, AP, RECALL, RECALL_10;

    // below are pagination-info
    public int nResultsPerPage;
    public int nPage;

    public int pageIdx, startIdx; // 0-based index

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
