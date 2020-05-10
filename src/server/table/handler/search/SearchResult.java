package server.table.handler.search;

import model.quantity.QuantityConstraint;
import org.apache.commons.codec.digest.DigestUtils;
import server.table.ResultInstance;

import java.util.ArrayList;

public class SearchResult {
    public String verdict; // "OK" is good, otherwise the error message.
    public String typeConstraint;
    public String contextConstraint;
    public QuantityConstraint quantityConstraint;
    public String evalDomain; // For evaluation
    public int numResults;
    public ArrayList<ResultInstance> topResults;

    public String encode() {
        return DigestUtils.md5Hex(typeConstraint + "\t" + contextConstraint + "\t" + quantityConstraint.toString());
    }
}
