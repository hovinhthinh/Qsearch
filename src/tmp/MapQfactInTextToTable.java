package tmp;

import data.table.wikipedia.WIKIPEDIA;
import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import model.table.link.EntityLink;
import model.table.Table;
import nlp.Static;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.json.JSONObject;
import util.FileUtils;
import util.Pair;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        ArrayList<Pair<String, JSONObject>> linkedQuantitiesPerColumn[] = new ArrayList[t.nColumn];
        for (int i = 0; i < t.nColumn; ++i) {
            linkedQuantitiesPerColumn[i] = new ArrayList<>();
        }

        loop:
        for (int r = 0; r < t.nDataRow; ++r) {
            ArrayList<EntityLink> ls = new ArrayList<>();
            int entityColumn = t.isNumericColumn[0] ? 1 : 0;
            ls.addAll(t.data[r][entityColumn].entityLinks);

            if (ls.size() != 1 || !ls.get(0).text.equals(t.data[r][entityColumn].text)) {
                continue loop;
            }

            EntityLink el = ls.get(0);
            ArrayList<JSONObject> linkedQuantities = entity2facts.get("<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">");
            if (linkedQuantities == null) {
                continue;
            }
            for (int qc = 1; qc < t.nColumn; ++qc) {
                Quantity q = extractQuantityFromMention(t.data[r][qc].text);
                if (q == null) {
                    continue;
                }
                QuantityConstraint qConstraint = new QuantityConstraint();
                qConstraint.quantity = new model.quantity.Quantity(q.value, q.units, q.bound);
                qConstraint.resolutionCode = QuantityConstraint.QuantityResolution.Value.APPROXIMATE;
                qConstraint.domain = QuantityDomain.getDomain(qConstraint.quantity);

                // check money domain.
                if (!qConstraint.domain.equals(QuantityDomain.Domain.MONEY)) {
                    continue;
                }

                for (JSONObject o : linkedQuantities) {
                    model.quantity.Quantity qt = model.quantity.Quantity.fromQuantityString(o.getString("quantity"));
                    if (qConstraint.match(qt)) {
                        ++goodRow;
                        linkedQuantitiesPerColumn[qc].add(new Pair("<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">", o));
                    }
                }
            }
        }
        int max = 0;
        int maxIndex = 0;
        for (int i = 0; i < t.nColumn; ++i) {
            if (linkedQuantitiesPerColumn[i].size() > max) {
                max = linkedQuantitiesPerColumn[i].size();
                maxIndex = i;
            }
        }
        if (max < 3) {
            return null;
        }
        StringBuilder linkedData = new StringBuilder();
        for (Pair<String, JSONObject> p : linkedQuantitiesPerColumn[maxIndex]) {
            linkedData.append("> ").append(p.first).append(" ==> ").append(p.second.toString()).append("\r\n\r\n");
        }
        return linkedData.toString();
    }

    public void run(String[] args) throws Exception {
        importSeedQfactsFromText("/GW/D5data-11/hvthinh/yagoImport5361304505893531106_pos+neg.gz");
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
//                JSchUtils.getFileInputStreamFromServer
                new FileInputStream
                        ("/GW/D5data/hvthinh/TabEL/tables.json.gz")), StandardCharsets.UTF_8)) {
            Table t = WIKIPEDIA.parseFromJSON(line);
            String linkedData;
            if ((linkedData = getLinkedDataFromText(t)) != null) {
                System.out.println(t.getTableContentPrintable(true, true, true));
                System.out.println(linkedData);
                System.out.println("------------------------------------------------------------");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new MapQfactInTextToTable().run(args);
    }
}
