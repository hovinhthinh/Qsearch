package server.table.handler.search;

import model.quantity.QuantityConstraint;
import org.apache.commons.codec.digest.DigestUtils;
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
//        return DigestUtils.md5Hex(typeConstraint + "\t" + contextConstraint + "\t" + quantityConstraint.toString());
        return (typeConstraint.replace(' ', '-')
                + "_" + contextConstraint.replace(' ', '-')
                + "_" + quantityConstraint.phrase.replace(' ', '-')).replace('/', '-');
    }
}
