package tmp;

import data.wikipedia.Wikipedia;
import model.table.Cell;
import model.table.Link;
import model.table.Table;
import nlp.NLP;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.JSchUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class ReadTabEL {
    static HashMap<String, String[]> entity2Types = new HashMap<>();

    public static void importYago(String input) {
        String e = null;
        ArrayList<String> ts = new ArrayList<>();
        for (String line : FileUtils.getLineStream(input)) {
            String[] arr = line.split("\t");
            if (arr.length != 4 || !arr[2].equals("rdf:type")) {
                continue;
            }
            String entity = arr[1];
            String type = NLP.stripSentence(arr[3].replaceAll("[^A-Za-z0-9]", " ")).toLowerCase();
            type = NLP.fastStemming(type, Morpha.noun);
            if (type.startsWith("wikicat ")) {
                type = type.substring(8);
            }
            if (type.startsWith("wordnet ")) {
                type = type.substring(type.indexOf(" ") + 1, type.lastIndexOf(" "));
            }
            if (e != null && !e.equals(entity)) {
                entity2Types.put(e, ts.toArray(new String[0]));
                ts.clear();
            }
            e = entity;
            ts.add(type);
        }
        if (ts.size() > 0) {
            entity2Types.put(e, ts.toArray(new String[0]));
        }
    }

    public static boolean entityHasTypeOf(String entity, String type) {
        String[] types = entity2Types.get(entity);
        if (types == null) {
            return false;
        }
        for (String t : types) {
            if (NLP.getHeadWord(t, true).equals(type)) {
                return true;
            }
        }
        return false;
    }


    // args: <query> <top>
    public static void main(String[] args) throws Exception {

        System.out.println("Loading Yago...");
        importYago("/GW/D5data-10/hvthinh/yagoTransitiveType.tsv");

        String queryType = NLP.stripSentence(NLP.fastStemming(args[0].toLowerCase(), Morpha.noun));
        int top = Integer.parseInt(args[1]);

        Scanner in = new Scanner(System.in);

        int count = 0;

        System.out.println("Looping");
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
                JSchUtils.getFileInputStreamFromServer
//                        new FileInputStream
        ("/GW/D5data/hvthinh/TabEL/tables.json.gz")), StandardCharsets.UTF_8)) {
            JSONObject o = new JSONObject(line);
//            System.out.println("-----Link: " + "https://en.wikipedia.org/wiki/" + URLEncoder.encode(o.getString("pgTitle").replaceAll("\\s", "_")));
            Table t = Wikipedia.parseFromJSON(line);
            boolean ok = false;
            loop:
            for (Cell[] r : t.data) {
                for (Cell c : r) {
                    for (Link l : c.links) {
                        if (entityHasTypeOf("<" + l.target + ">", queryType)) {
                            ok = true;
                            break loop;
                        }
                    }
                }
            }

            if (ok) {
                System.out.println(t.getTableContentPrintable());
                --top;
                if (top == 0) {
                    break;
                }
            }
        }
        JSchUtils.stop();


    }
}
