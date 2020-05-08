package model.text;

import data.table.background.qfact_text.OpenIETaggingNodeTabQs;
import model.text.tag.*;
import nlp.NLP;
import nlp.Static;
import org.apache.commons.lang.StringEscapeUtils;
import scala.collection.JavaConversions;
import util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Sentence {
    // Default written time of the document containing this sentence.
    public String referTime = null;

    // Source where this sentence is from (e.g. a html link or a docid).
    public String source = null;

    public int indexInParagraph = -1; // 0-based;
    public ArrayList<Token> tokens = new ArrayList<>();
    // Tagging components.
    public ArrayList<TimeTag> timeTags = null;
    public ArrayList<EntityTag> entityTags = null;
    public ArrayList<QuantityTag> quantityTags = null;
    // Quantitative facts.
    public ArrayList<QuantitativeFact> quantitativeFacts;

    // Unlinked quantities.
    public ArrayList<QuantitativeFact> negativeQuantitativeFacts; // EXPERIMENTAL.

    public static Sentence fromText(String text) {
        Sentence sent = new Sentence();
        text = NLP.stripSentence(text);

        List<edu.knowitall.tool.tokenize.Token> tokens = null;
        do {
            tokens = JavaConversions.seqAsJavaList(Static.getOpenIe().tokenizer().tokenize(Static.getOpenIe().clean(text)));
            int numTokens = 0;
            sent.tokens.clear();
            StringBuilder sb = new StringBuilder();
            for (edu.knowitall.tool.tokenize.Token t : tokens) {
                Token newToken = new Token();
                newToken.indexInSentence = numTokens++;
                newToken.str = t.string();
                sent.tokens.add(newToken);
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(newToken.str);
            }
            if (sb.toString().equals(text)) {
                break;
            } else {
                text = sb.toString();
            }
        } while (true);
        return sent;
    }

    @Override
    public String toString() {
        return getSubTokensString(0, tokens.size());
    }

    public String getSubTokensString(int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; ++i) {
            if (i > from) {
                sb.append(" ");
            }
            sb.append(tokens.get(i).str);
        }
        return sb.toString();

    }

    public List<String> getPrintingTrainingDataForTabQs() {
        List<String> result = new ArrayList<>();

        for (QuantitativeFact qfact : quantitativeFacts) {
            if (qfact.entityTag == null) {
                continue;
            }
            String entity = null;

            if (qfact.entityTag.id.startsWith("YAGO3:")) { // STICS
                entity = StringEscapeUtils.unescapeJava(qfact.entityTag.id.substring(6));
            } else if (qfact.entityTag.id.startsWith("YAGO:")) { // NYT
                entity = "<" + StringEscapeUtils.unescapeJava(qfact.entityTag.id.substring(5)) + ">";
            } else if (qfact.entityTag.id.startsWith("<") && qfact.entityTag.id.endsWith(">")) { // WIKI
                entity = "<" + StringEscapeUtils.unescapeJava(qfact.entityTag.id.substring(1, qfact.entityTag.id.length() - 1)) + ">";
            } else {
                throw new RuntimeException("Entity unrecognized");
            }

            StringBuilder sb = new StringBuilder();
            boolean flag = false;
            for (EntityTag t : qfact.contextEntityTags) {
                if (flag) {
                    sb.append(" ");
                }
                flag = true;
//                sb.append("<E>:");
//                sb.append(t.id); // Not using entityID at this time.
                sb.append(getSubTokensString(t.beginIndex, t.endIndex).toLowerCase());
            }
//            if (false) // ignore time tags
            for (TimeTag t : qfact.contextTimeTags) {
                if (flag) {
                    sb.append(" ");
                }
                flag = true;
                //                sb.append("<T>:");
                // Not using time range at this time. (we may ignore time in the context when processing matching).
                sb.append(getSubTokensString(t.beginIndex, t.endIndex).toLowerCase());
            }
            for (Token t : qfact.contextTokens) {
                if (!OpenIETaggingNodeTabQs.ALLOWED_CONTEXT_POSTAGS.contains(t.POS) || NLP.BLOCKED_STOPWORDS.contains(t.str.toLowerCase())) {
                    continue;
                }
                if (flag) {
                    sb.append(" ");
                }
                flag = true;
                sb.append(t.str.toLowerCase());
            }
//            if (!qfact.quantityTag.quantity.unit.isEmpty()) {
//                if (flag) {
//                    sb.append(" ");
//                }
//                flag = true;
//                sb.append(qfact.quantityTag.quantity.unit.toLowerCase());
//            }
            if (!flag) {
                continue;
            }
            String[] contextSplitted = Arrays.stream(sb.toString().split("\\s++")).sorted().distinct().toArray(String[]::new);

            StringBuilder entityAndContext = new StringBuilder();
            entityAndContext.append(entity)
                    .append("\t").append(String.join(" ", contextSplitted))
                    .append("\t").append(qfact.quantityTag.quantity.toString())
                    .append("\t").append(this.toString())
                    .append("\t").append(this.source)
                    .append("\t").append(qfact.entityTag.referSentence);
            result.add(entityAndContext.toString());
        }
        return result;
    }

    public List<String> getPrintingQuantitativeFacts() {
        List<String> result = new ArrayList<>();

        for (QuantitativeFact qfact : quantitativeFacts) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%.6f", qfact.conf) + "\t").append(this.toString()).append("\t");
            sb.append(qfact.entityTag == null ? "null" : qfact.entityTag.id).append("\t{");
            boolean flag = false;
            for (EntityTag t : qfact.contextEntityTags) {
                if (flag) {
                    sb.append(";");
                }
                flag = true;
                sb.append("<E>:");
//                sb.append(t.id); // Not using entityID at this time.
                sb.append(getSubTokensString(t.beginIndex, t.endIndex));
            }
            for (TimeTag t : qfact.contextTimeTags) {
                if (flag) {
                    sb.append(";");
                }
                flag = true;
                sb.append("<T>:");
                // Not using time range at this time. (we may ignore time in the context when processing matching).
                sb.append(getSubTokensString(t.beginIndex, t.endIndex));
            }
            for (Token t : qfact.contextTokens) {
                if (flag) {
                    sb.append(";");
                }
                flag = true;
                sb.append(t.str);
            }
            sb.append("}\t").append(qfact.quantityTag.quantity.toString());
            result.add(sb.toString());
        }
        return result;
    }

    public String getEntityFeatureString() {
        StringBuilder entityFeature = new StringBuilder();
        loop:
        for (int i = 0; i < tokens.size(); ++i) {
            for (TimeTag t : timeTags) {
                if (t.beginIndex < i && t.endIndex > i) {
                    continue loop;
                }
            }
            for (QuantityTag t : quantityTags) {
                if (t.beginIndex < i && t.endIndex > i) {
                    continue loop;
                }
            }
            boolean isEntityToken = false;
            for (EntityTag t : entityTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        entityFeature.append(" B");
                    } else {
                        entityFeature.append(" I");
                    }
                    isEntityToken = true;
                    break;
                }
            }
            if (!isEntityToken) {
                entityFeature.append(" O");
            }
        }
        return entityFeature.toString().trim();
    }

    public String getTextFeatureString() {
        StringBuilder text = new StringBuilder();
        loop:
        for (int i = 0; i < tokens.size(); ++i) {
            for (TimeTag t : timeTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        text.append(" ").append(t.PLACEHOLDER);
                    }
                    continue loop;
                }
            }
            for (QuantityTag t : quantityTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        text.append(" ").append(t.PLACEHOLDER);
                    }
                    continue loop;
                }
            }
            text.append(" ").append(tokens.get(i).str);
        }
        return text.toString().trim();
    }

    private Pair<Integer, String> getQuantityPositionAndLabelStringOfQuantitativeFact(int factIndex) {
        int qpos = -1;
        int qposCount = 0;
        StringBuilder labels = new StringBuilder();
        QuantitativeFact qfact = quantitativeFacts.get(factIndex);
        loop:
        for (int i = 0; i < tokens.size(); ++i) {
            for (TimeTag t : timeTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        ++qposCount;
                        for (Tag x : qfact.contextTimeTags) {
                            if (t.equals(x)) {
                                labels.append(" X");
                                continue loop;
                            }
                        }
                        labels.append(" O");
                    }
                    continue loop;
                }
            }
            for (QuantityTag t : quantityTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        if (t.equals(qfact.quantityTag)) {
                            qpos = qposCount;
//                            labels.append(" QT");
                            labels.append(" O"); // Not using QT for the output
                        } else {
                            labels.append(" O");
                        }
                        ++qposCount;
                    }
                    continue loop;
                }
            }
            ++qposCount;
            for (EntityTag t : entityTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (qfact.entityTag != null && t.equals(qfact.entityTag)) {
                        if (i == qfact.entityTag.beginIndex) {
                            labels.append(" E");
                        } else {
                            labels.append(" O");
                        }
                    } else {
//                            if (i == t.beginIndex) {
//                                for (Tag x : qfact.contextEntityTags) {
//                                    if (t.equals(x)) {
//                                        labels.append(" X");
//                                        continue loop;
//                                    }
//                                }
//                            } else {
//                                labels.append(" O");
//                            }
                        // Here we use tags X for all tokens of context entity.
                        for (Tag x : qfact.contextEntityTags) {
                            if (t.equals(x)) {
                                labels.append(" X");
                                continue loop;
                            }
                        }
                        labels.append(" O");
                    }
                    continue loop;
                }
            }
            for (Token t : qfact.contextTokens) {
                if (t.indexInSentence == i) {
                    labels.append(" X");
                    continue loop;
                }
            }
            labels.append(" O");
        }
        return new Pair(qpos, labels.toString().trim());
    }

    private Pair<Integer, String> getQuantityPositionAndLabelStringOfNegativeQuantitativeFact(int factIndex) {
        int qpos = -1;
        int qposCount = 0;
        StringBuilder labels = new StringBuilder();
        QuantitativeFact qfact = negativeQuantitativeFacts.get(factIndex);
        loop:
        for (int i = 0; i < tokens.size(); ++i) {
            for (TimeTag t : timeTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        ++qposCount;
                        labels.append(" O");
                    }
                    continue loop;
                }
            }
            for (QuantityTag t : quantityTags) {
                if (t.beginIndex <= i && t.endIndex > i) {
                    if (i == t.beginIndex) {
                        if (t.equals(qfact.quantityTag)) {
                            qpos = qposCount;
//                            labels.append(" QT");
                            labels.append(" O"); // Not using QT for the output
                        } else {
                            labels.append(" O");
                        }
                        ++qposCount;
                    }
                    continue loop;
                }
            }
            ++qposCount;
            labels.append(" O");
        }
        return new Pair(qpos, labels.toString().trim());
    }

    // <source>
    // <conf>
    // <full_sentence>
    // <text_1>\t<qfeature_1>\1<entity_feature_1>\t<label_1>
    // <text_2>\t<qfeature_2>\1<entity_feature_2>\t<label_2>
    // ...
    // <this is an empty line>
    // <next_source>
    public List<String> getPrintTestSamples(int maxSentenceLength, int minNumEntity, double minConf) {
        String[] entityFeature = getEntityFeatureString().split(" ");
        String[] textFeature = getTextFeatureString().split(" ");
        String fullString = this.toString();

        List<String> result = new ArrayList<>();
        // Skip quotes.
        if (fullString.contains("\"") || tokens.size() > maxSentenceLength) {
            return result;
        }

        loop:
        for (int i = 0; i < quantitativeFacts.size(); ++i) {
            if (quantitativeFacts.get(i).entityTag == null || Math.exp(quantitativeFacts.get(i).conf) < minConf) {
                continue;
            }
            StringBuilder test = new StringBuilder();
            test
//                    .append(source == null ? "null" : source).append("\r\n")
                    .append(String.format("%.6f", quantitativeFacts.get(i).conf)).append("\r\n").append(fullString).append("\r\n");

            Pair<Integer, String> qposAndLabel = getQuantityPositionAndLabelStringOfQuantitativeFact(i);
            String[] labels = qposAndLabel.second.split(" ");
            int nEntityF = 0;
            for (int j = 0; j < textFeature.length; ++j) {
                if (entityFeature[j].equals("B")) {
                    ++nEntityF;
                }
                String text = textFeature[j].equals(Placeholder.QUANTITY) ? "<Q>" :
                        (textFeature[j].equals(Placeholder.TIME) ? "<T>" : textFeature[j]);
                test.append(text).append("\t");
                test.append(j == qposAndLabel.first ? "<<<" : (entityFeature[j].equals("O") ? "-" : entityFeature[j])).append("\t");
                test.append(labels[j].equals("O") ? "-" : labels[j]).append("\r\n");
            }
            test.append("\r\n");
            if (nEntityF < minNumEntity) {
                break;
            }
            result.add(test.toString());
        }
        return result;
    }

    //<conf> <full_sentence>\t<qpos> <text_feature> ||| <entity_feature> ||| <labels>
    public List<String> getPrintTrainingSamples() {
        String entityFeatureString = getEntityFeatureString();
        String textFeatureString = getTextFeatureString();
        String fullString = this.toString();

        List<String> result = new ArrayList<>();
        for (int i = 0; i < quantitativeFacts.size(); ++i) {
            StringBuilder train = new StringBuilder();
            train.append(String.format("%.6f", quantitativeFacts.get(i).conf)).append(" ").append(fullString).append(
                    "\t");
            Pair<Integer, String> qposAndLabel = getQuantityPositionAndLabelStringOfQuantitativeFact(i);

            train.append(qposAndLabel.first).append(" ").append(textFeatureString).append(" ||| ").append(entityFeatureString).append(" ||| ").append(qposAndLabel.second);
            result.add(train.toString());
        }
        return result;
    }

    //<conf> <full_sentence>\t<qpos> <text_feature> ||| <entity_feature> ||| <labels>
    public List<String> getPrintNegativeTrainingSamples() {
        String entityFeatureString = getEntityFeatureString();
        String textFeatureString = getTextFeatureString();
        String fullString = this.toString();

        List<String> result = new ArrayList<>();
        for (int i = 0; i < negativeQuantitativeFacts.size(); ++i) {
            StringBuilder train = new StringBuilder();
            train.append(String.format("%.6f", negativeQuantitativeFacts.get(i).conf)).append(" ").append(fullString).append(
                    "\t");
            Pair<Integer, String> qposAndLabel = getQuantityPositionAndLabelStringOfNegativeQuantitativeFact(i);

            train.append(qposAndLabel.first).append(" ").append(textFeatureString).append(" ||| ").append(entityFeatureString).append(" ||| ").append(qposAndLabel.second);
            result.add(train.toString());
        }
        return result;
    }

}
