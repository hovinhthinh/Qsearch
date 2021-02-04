package pipeline.text;

import model.quantity.QuantityDomain;
import model.text.Paragraph;
import model.text.QuantitativeFact;
import model.text.Sentence;
import model.text.tag.EntityTag;
import model.text.tag.QuantityTag;
import model.text.tag.Tag;
import model.text.tag.TimeTag;
import org.junit.Assert;
import pipeline.text.deep.FactExtractionLabelingClient;

import java.util.ArrayList;

public class DeepTaggingNode implements TaggingNode {
    private FactExtractionLabelingClient client;
    private double minimumLabelingConfidence;
    private String taggingDomain;

    public DeepTaggingNode(String modelPath, String quantityTaggingDomain,
                           double minimumLabelingConfidence) {
        client = new FactExtractionLabelingClient(modelPath);
        this.minimumLabelingConfidence = minimumLabelingConfidence;
        this.taggingDomain = quantityTaggingDomain;
    }

    public DeepTaggingNode(String modelPath, String quantityTaggingDomain) {
        this(modelPath, quantityTaggingDomain, Double.NEGATIVE_INFINITY);
    }

    public DeepTaggingNode(String modelPath) {
        this(modelPath, QuantityDomain.Domain.ANY, Double.NEGATIVE_INFINITY);
    }

    public void tag(Sentence sent) {
        // Build the position maps between original and reduced sentence.
        ArrayList<Integer> originalToReduced = new ArrayList<>();
        int nHidden = 0;
        loop:
        for (int i = 0; i < sent.tokens.size(); ++i) {
            boolean isHidden = false;
            for (Tag t : sent.timeTags) {
                if (i > t.beginIndex && i < t.endIndex) {
                    isHidden = true;
                    break;
                }
            }
            if (!isHidden) {
                for (Tag t : sent.quantityTags) {
                    if (i > t.beginIndex && i < t.endIndex) {
                        isHidden = true;
                        break;
                    }
                }
            }
            if (isHidden) {
                ++nHidden;
            }
            originalToReduced.add(i - nHidden);
        }
        ArrayList<Integer> reducedToOriginal = new ArrayList<>();
        for (int i = 0; i < originalToReduced.size(); ++i) {
            if (i == 0 || (!originalToReduced.get(i).equals(originalToReduced.get(i - 1)))) {
                reducedToOriginal.add(i);
            }
        }

        // Now process.
        sent.quantitativeFacts = new ArrayList<>();
        sent.negativeQuantitativeFacts = new ArrayList<>();

        String textFeatureString = sent.getTextFeatureString();
        String entityFeatureString = sent.getEntityFeatureString();
        for (QuantityTag qtag : sent.quantityTags) {
            if (!QuantityDomain.quantityMatchesSearchDomain(qtag.quantity, taggingDomain)) {
                continue;
            }
            try {
                StringBuilder clientInput = new StringBuilder();
                clientInput.append(originalToReduced.get(qtag.beginIndex)).append(" ").append(textFeatureString)
                        .append(" ||| ").append(entityFeatureString);
                String clientOutput = client.label(clientInput.toString());

                // Decode output and fill to sent.quantitativeFacts
                int p = clientOutput.lastIndexOf(" ");

                QuantitativeFact qfact = new QuantitativeFact();
                qfact.conf = Double.parseDouble(clientOutput.substring(p + 1));
                if (qfact.conf < minimumLabelingConfidence) {
                    continue;
                }
                String[] tags = clientOutput.substring(0, p).split(" ");

                Assert.assertTrue(tags.length == reducedToOriginal.size());
                loop:
                for (int i = 0; i < tags.length; ++i) {
                    if (tags[i].equals("E")) {
                        for (EntityTag et : sent.entityTags) {
                            if (originalToReduced.get(et.beginIndex) == i) {
                                qfact.entityTag = et;
                                continue loop;
                            }
                        }
                    } else if (tags[i].equals("X")) {
                        int originalPos = reducedToOriginal.get(i);
                        for (TimeTag tt : sent.timeTags) {
                            if (tt.beginIndex == originalPos) {
                                qfact.contextTimeTags.add(tt);
                                continue loop;
                            }
                        }
                        qfact.contextTokens.add(sent.tokens.get(originalPos));
                    }
                }

                qfact.quantityTag = qtag;
                sent.quantitativeFacts.add(qfact);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean process(Paragraph paragraph) {
        for (Sentence sent : paragraph.sentences) {
            tag(sent);
        }
        return true;
    }
}
