package tmp;

import data.wikipedia.Wikipedia;
import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import model.table.Link;
import model.table.Table;
import nlp.Static;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.json.JSONObject;
import util.FileUtils;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MapQfactInTextToTable {

    HashMap<String, ArrayList<JSONObject>> entity2facts = new HashMap<>();

    public void importSeedQfactsFromText(String inputFile) {
        String lastEntity = null;
        ArrayList<JSONObject> entityFacts = null;
        for (String line : FileUtils.getLineStream(inputFile, "UTF-8")) {
            int pos = line.indexOf("\t");
            String entity = line.substring(0, pos);
            JSONObject data = new JSONObject(line.substring(pos + 1));
            if (lastEntity != null && entity.equals(lastEntity)) {
                entityFacts.add(data);
            } else {
                if (lastEntity != null) {
                    entity2facts.put(lastEntity, entityFacts);
                }
                lastEntity = entity;
                entityFacts = new ArrayList<>();
                entityFacts.add(data);
            }
        }
        if (lastEntity != null) {
            entity2facts.put(lastEntity, entityFacts);
        }
    }

    Quantity extractQuantityFromMention(String mention) {
        ArrayList<Quantity> qs = new ArrayList<>();

        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(mention, true)) {
            if (span.object instanceof Quantity) {
                qs.add((Quantity) span.object);
            }
        }
        if (qs.size() == 1) {
            return qs.get(0);
        }
        return null;
    }

    String getLinkedDataFromText(Table t) {
        if (t.nColumn < 2) {
            return null;
        }

        int goodRow = 0;

        StringBuilder linkedData = new StringBuilder();
        loop:
        for (int r = 0; r < t.nDataRow; ++r) {
            ArrayList<Link> ls = new ArrayList<>();
            for (int ec = 0; ec < 2; ++ec) {
                ls.addAll(Arrays.asList(t.data[r][ec].links));
            }

            for (Link el : ls) {
                ArrayList<JSONObject> linkedQuantities = entity2facts.get("<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">");
                if (linkedQuantities == null) {
                    continue;
                }
                for (int qc = 1; qc < t.nColumn; ++qc) {
                    Quantity q = extractQuantityFromMention(t.data[r][qc].text);
                    if (q == null) {
                        continue;
                    }
                    for (JSONObject o : linkedQuantities) {
                        model.quantity.Quantity qt = model.quantity.Quantity.fromQuantityString(o.getString("quantity"));
                        double maxDiff = Math.max(Math.abs(qt.value), Math.abs(q.value)) * 0.05;
                        if (Math.abs(qt.value - q.value) < maxDiff) {
                            ++goodRow;
                            linkedData.append(el.target.substring(el.target.lastIndexOf(":") + 1)).append(" ==> ").append(o.toString()).append("\r\n");
                            continue loop;
                        }
                    }
                }
            }
        }
        return goodRow >= 3 ? linkedData.toString() : null;
    }

    public void run(String[] args) throws Exception {
        importSeedQfactsFromText("/GW/D5data-11/hvthinh/yagoImport5361304505893531106_pos+neg.gz");
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
//                JSchUtils.getFileInputStreamFromServer
                new FileInputStream
                        ("/GW/D5data/hvthinh/TabEL/tables.json.gz")), StandardCharsets.UTF_8)) {
            Table t = Wikipedia.parseFromJSON(line);
            String linkedData;
            if ((linkedData = getLinkedDataFromText(t)) != null) {
                System.out.println(t.getTableContentPrintable(true));
                System.out.println(linkedData);
                System.out.println("------------------------------------------------------------");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new MapQfactInTextToTable().run(args);
    }
}
