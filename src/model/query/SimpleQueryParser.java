package model.query;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import edu.knowitall.tool.postag.PostaggedToken;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.NLP;
import nlp.Static;
import scala.collection.JavaConversions;
import server.text.handler.TypeSuggestionHandler;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
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
    private static final Pattern QUANTITY_TO_OPTIMIZE_PATTERN =
            Pattern.compile("(\\$\\s*|\\b)\\d+(\\.\\d+)?(k|m|b)(\\s*\\$|\\b)");

    public static String preprocess(String query) {
        query = NLP.stripSentence(query).toLowerCase();
        if (query.length() > 0 && Arrays.asList('.', ',', ';', '?').contains(query.charAt(query.length() - 1))) {
            query = query.substring(0, query.length() - 1);
        }
        query = query.replaceFirst("^(what|where|which|who) ", "");
        query = query.replaceFirst("^(am|is|are|was|were|be) ", "");

        // optimize resolution code
        for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
            int p = query.indexOf(" " + operator + " ");
            if (p != -1) {
                query = query.substring(0, p)
                        + " " + QuantityConstraint.QuantityResolution.ALL_SIGNALS.get(operator) + " "
                        + query.substring(p + operator.length() + 2);
                break;
            }
        }

        // optimize multiplier: process the first one only.
        Matcher matcher = QUANTITY_TO_OPTIMIZE_PATTERN.matcher(query);
        if (matcher.find()) {
            String sub = matcher.group();
            sub = sub.trim();
            int start = matcher.start(), end = matcher.end();
            boolean useMillionMul = false;
            if (sub.charAt(0) == '$') {
                sub = sub.substring(1).trim();
                ++start;
                useMillionMul = true;
            }
            if (sub.charAt(sub.length() - 1) == '$') {
                sub = sub.substring(0, sub.length() - 1).trim();
                --end;
                useMillionMul = true;
            }
            String num = sub.substring(0, sub.length() - 1);
            String mul = "";
            if (sub.contains("k")) {
                mul = " thousand";
            } else if (sub.contains("m")) {
                mul = useMillionMul ? " million" : "m";
            } else if (sub.contains("b")) {
                mul = " billion";
            }
            query = query.substring(0, start) + " " + num + mul + " " + query.substring(end);
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
                if (useTypeSuggestion && (TypeSuggestionHandler.getTypeFreq(rawTokenizedSbStr) >= 50
                        || TypeSuggestionHandler.getTypeFreq(NLP.fastStemming(rawTokenizedSbStr, Morpha.noun)) >= 50)) {
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
            // get the last one, or the last one right after a comparison signal.
            String lastQuantityAfterSignal = null;
            for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(rawTokenized + " .", true, null)) {
                if (span.object instanceof Quantity) {
                    String qStr = rawTokenized.substring(span.start, span.end + 1).trim();
                    boolean signalAdded = false;
                    loop:
                    for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
                        String[] candidates = new String[]{"not " + operator, "no " + operator, operator};
                        for (String c : candidates) {
                            String newQstr = NLP.stripSentence(c + " " + qStr.replace(operator, ""));
                            if (rawTokenized.contains(newQstr)) {
                                qStr = newQstr;
                                signalAdded = true;
                                break loop;
                            }
                        }
                    }

                    QuantityConstraint constraint = QuantityConstraint.parseFromString(qStr);
                    if (constraint == null) {
                        continue;
                    }
                    String constraintStr = constraint.toString();
                    // optimize quantity string
                    ArrayList<String> arr = NLP.splitSentence(qStr);
                    int start = 0, end = arr.size();
                    while (start < end
                            && (constraint = QuantityConstraint.parseFromString(String.join(" ", arr.subList(start + 1, end)))) != null
                            && constraint.toString().equals(constraintStr)) {
                        ++start;
                    }
                    while (start < end
                            && (constraint = QuantityConstraint.parseFromString(String.join(" ", arr.subList(start, end - 1)))) != null
                            && constraint.toString().equals(constraintStr)) {
                        --end;
                    }

                    constraint = QuantityConstraint.parseFromString(String.join(" ", arr.subList(start, end)));
                    // shrink to get a (fine-grained) non-dimensional unit
                    if (constraint.fineGrainedDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {

                        int shrinkedEnd = -1;
                        int lastValidDimensionlessEnd = end;
                        for (int i = end - 1; i > start; --i) {
                            QuantityConstraint newConstraint = QuantityConstraint.parseFromString(String.join(" ", arr.subList(start, i)));
                            if (newConstraint == null
                                    || newConstraint.resolutionCode != constraint.resolutionCode
                                    || newConstraint.quantity.value != constraint.quantity.value) {
                                break;
                            }
                            lastValidDimensionlessEnd = i;
                            if (!newConstraint.fineGrainedDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                                shrinkedEnd = i;
                                break;
                            }
                        }
                        if (shrinkedEnd != -1) {
                            end = shrinkedEnd; // shrink to first non-dimensionless
                        } else {
                            end = lastValidDimensionlessEnd; // shrink to empty unit
                        }
                    }

                    result.third = String.join(" ", arr.subList(start, end));
                    if (signalAdded) {
                        lastQuantityAfterSignal = result.third;
                    }

                }
            }

            if (lastQuantityAfterSignal != null) {
                result.third = lastQuantityAfterSignal;
            }

            if (result.third == null) {
                return null;
            }

            // context
            ArrayList<String> context = NLP.splitSentence(
                    rawTokenized.replace(result.first, " ").replace(result.third, " "));
            StringBuilder cSb = new StringBuilder();
            for (String s : context) {
                if (NLP.BLOCKED_STOPWORDS.contains(s) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(s)) {
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
        String[] files = new String[]{
                "eval/text/exp_2/inputs/FINANCE.txt",
                "eval/text/exp_2/inputs/SPORTS.txt",
                "eval/text/exp_2/inputs/TECHNOLOGY.txt",
                "eval/text/exp_2/inputs/TRANSPORT.txt"
        };
        for (String file : files) {
            for (String line : FileUtils.getLineStream(file, "UTF-8")) {
                String[] arr = line.split("\t");
                Triple<String, String, String> t = parse(arr[0]);
                System.out.println(String.format("[Parsed] %s -- %s", arr[0], t));
            }
        }
        System.out.println("--------------------------------------------------------------------------------");

        System.out.println(parse("technology companies with more than $100b annual profit"));
        System.out.println(parse("sprinters who ran 200m in less than 25 s"));
        System.out.println(parse("companies with profit in 2018 under 100b usd"));
        System.out.println(parse("games with number of players less than 100 million in 2018"));
        System.out.println(parse("which hybrid cars have range on battery more than 50 km?"));
    }
}
