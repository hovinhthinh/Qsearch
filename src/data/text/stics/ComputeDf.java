package data.text.stics;

import model.context.IDF;
import nlp.NLP;
import org.json.JSONException;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Deprecated
public class ComputeDf {
    public static void main(String[] args) {
//        args = "/home/hvthinh/datasets/STICS/news-en-documents_20181120.tar.gz ./tmp".split("\\s++");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        int nDoc = 0;
        HashMap<String, Integer> word2doc = new HashMap<>();
        for (String line : stream) {
            try {
                JSONObject json = new JSONObject(line);
                JSONObject aida = json.getJSONObject("aida");
                String originalText = aida.getString("originalText");
                StringBuilder sb = new StringBuilder(originalText.toLowerCase());
                for (int i = 0; i < sb.length(); ++i) {
                    if (!Character.isLetterOrDigit(sb.charAt(i))) {
                        sb.setCharAt(i, ' ');
                    }
                }
                String[] tokens = NLP.fastStemming(NLP.stripSentence(sb.toString()), Morpha.any).split(
                        "\\s++");
                ++nDoc;
                HashSet<String> tokenSet = new HashSet<>(Arrays.asList(tokens));
                for (String token : tokenSet) {
                    word2doc.put(token, word2doc.getOrDefault(token, 0) + 1);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        out.println(IDF._N_DOC_NAME + " " + nDoc);
        for (Map.Entry<String, Integer> e : word2doc.entrySet()) {
            out.println(e.getKey() + " " + e.getValue());
        }
        out.close();
    }
}
