package data.text.wikipedia;

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

public class WIKIPEDIA_EntityTaggingNode implements TaggingNode {

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

        JSONObject arr = (JSONObject) paragraph.attributes.get("MENTIONS");
        String[] nameTokens;

        // No given confidence, use everything (1.0)
        for (String id : arr.keySet()) {
            Object o = arr.get(id);
            try {
                if (o instanceof String) {
                    for (Sentence sent : paragraph.sentences) {
                        tag(sent, NLP.stripSentence((String) o).split("\\s++"), id, 1.0);
                    }
                } else {
                    JSONArray ao = (JSONArray) o;
                    for (int i = 0; i < ao.length(); ++i) {
                        nameTokens = NLP.stripSentence(ao.getString(i)).split("\\s++");
                        for (Sentence sent : paragraph.sentences) {
                            tag(sent, nameTokens, id, 1.0);
                        }
                    }
                }
            } catch (JSONException e) {
                continue;
            }
        }
        return true;
    }
}
