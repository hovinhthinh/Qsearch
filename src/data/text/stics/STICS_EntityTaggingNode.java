package data.text.stics;

import model.text.Paragraph;
import model.text.Sentence;
import model.text.tag.EntityTag;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pipeline.text.TaggingNode;

import java.util.ArrayList;
import java.util.Collections;

public class STICS_EntityTaggingNode implements TaggingNode {
    private double minimumEntityConfidence;

    public STICS_EntityTaggingNode(double minimumEntityConfidence) {
        this.minimumEntityConfidence = minimumEntityConfidence;
    }

    public STICS_EntityTaggingNode() {
        this(Double.NEGATIVE_INFINITY);
    }

    private void tag(Sentence sent, String[] nameTokens, String id, double conf) {
        for (int i = 0; i < sent.tokens.size() - nameTokens.length + 1; ++i) {
            boolean flag = true;
            for (int j = 0; j < nameTokens.length; ++j) {
                if (!nameTokens[j].equals(sent.tokens.get(i + j).str)) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                // Check for any overlap tag and replace if longer.
                // Check for 1 overlap only.
                boolean toBeAdded = true;
                for (int j = 0; j < sent.entityTags.size(); ++j) {
                    EntityTag t = sent.entityTags.get(j);
                    if (t.beginIndex < i + nameTokens.length && i < t.endIndex) {
                        if (t.endIndex - t.beginIndex >= nameTokens.length) {
                            toBeAdded = false;
                        } else {
                            Collections.swap(sent.entityTags, j, sent.entityTags.size() - 1);
                            sent.entityTags.remove(sent.entityTags.size() - 1);
                        }
                        break;
                    }
                }
                if (toBeAdded) {
                    sent.entityTags.add(new EntityTag(i, i + nameTokens.length, id, conf));
                }

                i += nameTokens.length - 1;
            }
        }
    }

    @Override
    public boolean process(Paragraph paragraph) {
        for (Sentence sent : paragraph.sentences) {
            sent.entityTags = new ArrayList<>();
        }

        JSONArray arr = (JSONArray) paragraph.attributes.get("MENTIONS");
        for (int i = 0; i < arr.length(); ++i) {
            String[] nameTokens;
            String id;
            double conf = -1;
            try {
                JSONObject o = arr.getJSONObject(i);
                nameTokens = NLP.stripSentence(o.getString("name")).split("\\s++");
                o = o.getJSONObject("bestEntity");
                id = o.getString("kbIdentifier");
                conf = Double.parseDouble(o.getString("disambiguationScore"));
                if (conf < minimumEntityConfidence) {
                    continue;
                }
            } catch (JSONException e) {
                continue;
            }
            for (Sentence sent : paragraph.sentences) {
                tag(sent, nameTokens, id, conf);
            }
        }
        return true;
    }
}
