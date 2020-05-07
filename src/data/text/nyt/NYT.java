package data.text.nyt;

import model.text.Paragraph;
import org.json.JSONException;
import org.json.JSONObject;
import util.TParser;

public class NYT {
    public static Paragraph parseFromJSON(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            String originalText = json.getString("originalText");
            String source = null;
            String link = TParser.getContent(originalText, " ex-ref=\"", "\"");
            if (link != null) {
                source = "NYT:Link:" + link;
            } else if (json.has("docID")) {
                source = "NYT:docID:" + json.getString("docID");
            }

            String cleanedText = json.getString("cleanedText");
            Paragraph paragraph = Paragraph.fromText(cleanedText, source);

            paragraph.attributes.put("ORIGINAL_TEXT", cleanedText);
            paragraph.attributes.put("MENTIONS", json.getJSONArray("mentions"));

            return paragraph;
        } catch (JSONException e) {
            return null;
        }
    }
}
