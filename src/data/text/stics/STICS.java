package data.text.stics;

import model.text.Paragraph;
import org.json.JSONException;
import org.json.JSONObject;

public class STICS {
    public static Paragraph parseFromJSON(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            JSONObject aida = json.getJSONObject("aida");
            String originalText = aida.getString("originalText");
            String source = null;
            if (json.has("Link")) {
                source = "STICS:Link:" + json.getString("Link");
            }

            Paragraph paragraph = Paragraph.fromText(originalText, source);

            paragraph.attributes.put("ORIGINAL_TEXT", originalText);
            paragraph.attributes.put("MENTIONS", aida.getJSONArray("mentions"));

            String reference_date = json.has("PublishDate") ?
                    json.getJSONObject("PublishDate").getString("$date") : null;
            if (reference_date != null) {
                reference_date = reference_date.substring(0, reference_date.indexOf("T"));
                paragraph.attributes.put(Paragraph.REFERENCE_DATE_KEY, reference_date);
            }
            return paragraph;
        } catch (JSONException e) {
            return null;
        }
    }
}
