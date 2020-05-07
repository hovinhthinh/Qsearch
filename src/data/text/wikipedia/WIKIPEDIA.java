package data.text.wikipedia;

import model.text.Paragraph;
import org.json.JSONException;
import org.json.JSONObject;

public class WIKIPEDIA {
    public static Paragraph parseFromJSON(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            String source = "WIKIPEDIA:Link:" + json.getString("source");

            String cleanedText = json.getString("content");
            Paragraph paragraph = Paragraph.fromText(cleanedText, source);

            paragraph.attributes.put("ORIGINAL_TEXT", cleanedText);
            paragraph.attributes.put("MENTIONS", json.getJSONObject("entities"));

            return paragraph;
        } catch (JSONException e) {
            return null;
        }
    }
}
