package server.table.handler.search;

import model.quantity.QuantityConstraint;
import org.apache.commons.codec.digest.DigestUtils;
import storage.table.ResultInstance;

import java.util.ArrayList;

public class SearchResult {
    public String verdict; // "OK" is good, otherwise the error message.
    public String typeConstraint;
    public String contextConstraint;
    public QuantityConstraint quantityConstraint;
    public String evalDomain; // For evaluation
    public String matchingModel;
    public int numResults;
    public ArrayList<ResultInstance> topResults = new ArrayList<>();

    public String encode() {
        return DigestUtils.md5Hex(typeConstraint + "\t" + contextConstraint + "\t" + quantityConstraint.toString());
    }
}
