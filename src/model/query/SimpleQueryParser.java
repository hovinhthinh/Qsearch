package model.query;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.driver.Quantifier;
import edu.knowitall.tool.postag.PostaggedToken;
import model.quantity.QuantityConstraint;
import nlp.NLP;
import nlp.Static;
import scala.collection.JavaConversions;
import server.table.handler.TypeSuggestionHandler;
import uk.ac.susx.informatics.Morpha;
import util.Triple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleQueryParser {
    public static final Logger LOGGER = Logger.getLogger(SimpleQueryParser.class.getName());
    private static final HashSet<String> TYPE_SEPARATOR =
            new HashSet<>(Arrays.asList("that", "which", "where", "when", "who", "whom", "with", "whose"));
    // (\b|\$)\d+(\.\d+)?\s*(k|m|b)(\b|\$)
    private static final Pattern QUANTITY_TO_OPTIMIZE_PATTERN =
            Pattern.compile("(\\b|\\$)\\d+(\\.\\d+)?\\s*(k|m|b)(\\b|\\$)");
    private static Quantifier QUANTIFIER = new Quantifier();

    private static String preprocess(String query) {
        query = NLP.stripSentence(query).toLowerCase();
        if (query.length() > 0 && (query.charAt(query.length() - 1) == '.' || query.charAt(query.length() - 1) == ','
                || query.charAt(query.length() - 1) == ';') || query.charAt(query.length() - 1) == '?') {
            query = query.substring(0, query.length() - 1);
        }
        query = query.replaceFirst("^(what|where|which|who) ", "");
        query = query.replaceFirst("^(am|is|are|was|were|be) ", "");

        int nQuantity = 0;
        for (QuantSpan span : QUANTIFIER.getSpans(query, true)) { // get the last one.
            if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                ++nQuantity;
            }
        }
        // optimize k, m, b for quantities, only when nQuantity <= 1 quantity.
        if (nQuantity <= 1) {
            Matcher matcher = QUANTITY_TO_OPTIMIZE_PATTERN.matcher(query);
            // process the first one only.
            if (matcher.find()) {
                String sub = matcher.group();
                sub = sub.trim();
                int start = matcher.start(), end = matcher.end();
                if (sub.charAt(0) == '$') {
                    sub = sub.substring(1);
                    ++start;
                }
                if (sub.charAt(sub.length() - 1) == '$') {
                    sub = sub.substring(0, sub.length() - 1);
                    --end;
                }
                double num = Double.parseDouble(sub.substring(0, sub.length() - 1).trim());
                if (sub.contains("k")) {
                    num *= 1000;
                } else if (sub.contains("m")) {
                    num *= 1000000;
                } else if (sub.contains("b")) {
                    num *= 1000000000;
                }
                query = query.substring(0, start) + " " + String.format("%.0f", num) + " " + query.substring(end);
            }
        }

        return NLP.stripSentence(query);
    }


    public synchronized static Triple<String, String, String> parse(String rawQuery, boolean useTypeSuggestion) {
        rawQuery = preprocess(rawQuery);
        LOGGER.info("Preprocessed_query: " + rawQuery);
        Triple<String, String, String> result = new Triple<>();
        try {
            List<PostaggedToken> tagged = JavaConversions.seqAsJavaList(Static.getOpenIe().postagger().postagTokenized(
                    Static.getOpenIe().tokenizer().tokenize(rawQuery)));

            // type
            String typeFromTypeSuggestionSystem = null;

            StringBuilder rawTokenizedSb = new StringBuilder();
            for (PostaggedToken w : tagged) {
                if (rawTokenizedSb.length() > 0) {
                    rawTokenizedSb.append(" ");
                }
                rawTokenizedSb.append(w.string());
                String rawTokenizedSbStr = rawTokenizedSb.toString();
                if (useTypeSuggestion && (TypeSuggestionHandler.getTypeFreq(rawTokenizedSbStr) >= 100
                        || TypeSuggestionHandler.getTypeFreq(NLP.fastStemming(rawTokenizedSbStr, Morpha.noun)) >= 100)) {
                    typeFromTypeSuggestionSystem = rawTokenizedSbStr;
                }
            }
            String rawTokenized = rawTokenizedSb.toString();

            if (typeFromTypeSuggestionSystem != null) {
                result.first = typeFromTypeSuggestionSystem;
            } else {
                StringBuilder type = new StringBuilder();
                boolean hasNoun = false;

                loop:
                for (PostaggedToken t : tagged) {
                    if (t.postag().startsWith("VB") || TYPE_SEPARATOR.contains(t.string())
                            || (hasNoun && (t.postag().startsWith("RB") || t.postag().startsWith("JJ")))) {
                        if (!hasNoun || type.length() == 0) {
                            return null;
                        }
                        result.first = type.toString();
                        break loop;
                    }
                    if (type.length() > 0) {
                        type.append(" ");
                    }
                    type.append(t.string());
                    if (t.postag().startsWith("NN")) {
                        hasNoun = true;
                    } else if (t.string().equals("in") || t.string().equals("of")) {
                        hasNoun = false;
                    }
                }
            }
            // quantity
            for (QuantSpan span : QUANTIFIER.getSpans(rawTokenized, true)) { // get the last one.
                if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                    String qStr = rawTokenized.substring(span.start, span.end).trim();
                    String passed = rawTokenized.substring(0, span.start).trim();
                    int startToken = 0;
                    if (!passed.isEmpty()) {
                        startToken = NLP.splitSentence(passed).size();
                        // FIX_FOR:"NFor further information , please contact : Virtue PR & Marketing
                        // Communications P.O
                        // Box : 191931 Dubai , United Arab Emirates Tel : +97144508835"
                        if (rawTokenized.charAt(span.start) != ' ' && rawTokenized.charAt(span.start - 1) != ' ') {
                            --startToken;
                        }
                    }
                    StringBuilder qSb = new StringBuilder();
                    for (int i = startToken; i < startToken + NLP.splitSentence(qStr).size(); ++i) {
                        qSb.append(" ").append(tagged.get(i).string());
                    }
                    result.third = qSb.toString().trim();
                }
            }
            if (result.third == null) {
                return null;
            }
            String preModifiedQuantity = result.third;

            loop:
            for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS) {
                String[] candidates = new String[]{"not " + operator, "no " + operator, operator};
                for (String c : candidates) {
                    String newQstr = NLP.stripSentence(c + " " + result.third.replace(operator, ""));
                    if (rawTokenized.contains(newQstr)) {
                        result.third = newQstr;
                        rawTokenized = rawTokenized.replace(newQstr, "");
                        break loop;
                    }
                }
            }
            // context
            ArrayList<String> context = NLP.splitSentence(
                    rawTokenized.replace(result.first, "").replace(preModifiedQuantity, ""));
            StringBuilder cSb = new StringBuilder();
            for (String s : context) {
                if (NLP.BLOCKED_STOPWORDS.contains(s)) {
                    continue;
                }
                cSb.append(" ").append(s);
            }
            result.second = cSb.toString().trim();

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized static Triple<String, String, String> parse(String rawQuery) {
        return parse(rawQuery, true);
    }

    public static void main(String[] args) {
//        System.out.println(preprocess("Companies with revenue over 1B USD. "));
//        System.out.println(preprocess("Companies with revenue over $1  K USD. "));
//        System.out.println(preprocess("Companies with revenue over 1 m USD. "));
//        System.out.println(preprocess("Companies with revenue over $1.2B USD. "));
//        System.out.println(preprocess("Companies with revenue over 1.123 k$ USD. "));
//        System.out.println(preprocess("Companies with revenue over $1  kK USD. "));
//        System.out.println(preprocess("Companies with revenue over $1mm USD. "));
        Triple t = parse("electric cars with range more than 50 km");
        System.out.println(t.first);
        System.out.println(t.second);
        System.out.println(t.third);
    }
}
