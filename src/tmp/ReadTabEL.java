package tmp;

import com.google.gson.Gson;
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

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Scanner;

public class ReadTabEL {
    static HashMap<String, String[]> entity2Types = new HashMap<>();

    static Gson gson = new Gson();

    public static void importYago(String input) {
        for (String line : FileUtils.getLineStream(input)) {
            String[] arr = line.split("\t");
            entity2Types.put(arr[0], gson.fromJson(arr[1], String[].class));
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
        importYago("/GW/D5data-10/hvthinh/yagoTransitiveTypeCompact.tsv");
        String queryType = NLP.stripSentence(NLP.fastStemming(args[0].toLowerCase(), Morpha.noun));
        int top = Integer.parseInt(args[1]);

        Scanner in = new Scanner(System.in);

        System.out.println("Looping");
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
//                JSchUtils.getFileInputStreamFromServer
                        new FileInputStream
        ("/GW/D5data/hvthinh/TabEL/tables.json.gz")), StandardCharsets.UTF_8)) {
            JSONObject o = new JSONObject(line);
//            System.out.println("-----Link: " + "https://en.wikipedia.org/wiki/" + URLEncoder.encode(o.getString("pgTitle").replaceAll("\\s", "_")));
            Table t = Wikipedia.parseFromJSON(line);
            boolean ok = false;
            loop:
            for (Cell[] r : t.data) {
                for (Cell c : r) {
                    for (Link l : c.links) {
                        if (entityHasTypeOf("<" + l.target.substring(19) + ">", queryType)) {
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
