package model.query;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import edu.knowitall.tool.postag.PostaggedToken;
import model.context.ContextEmbeddingMatcher;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.Glove;
import nlp.NLP;
import nlp.Static;
import scala.collection.JavaConversions;
import server.text.handler.TypeSuggestionHandler;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.Pair;
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

    private static final int SUGGESTING_THRESHOLD = 50;
    private static final double MIN_SUGGESTING_CONF = 0.85;

    private static final Pattern MULTIPLIER_OPTIMIZE_PATTERN =
            Pattern.compile("(\\$\\s*|\\b)\\d+(\\.\\d+)?(k|m|b)(\\s*\\$|\\b)");

    private static final Pattern RANGE_OPTIMIZE_PATTERN =
            Pattern.compile("\\d+(\\.\\d+)?-\\d+(\\.\\d+)?");

    public static String preprocess(String query) {
        query = NLP.stripSentence(query).toLowerCase();
        if (query.length() > 0 && Arrays.asList('.', ',', ';', '?').contains(query.charAt(query.length() - 1))) {
            query = query.substring(0, query.length() - 1);
        }
        query = query.replaceFirst("^(what|where|which|who) ", "");
        query = query.replaceFirst("^(am|is|are|was|were|be) ", "");

        int lastPos = -1;
        String lastOperator = null;
        // optimize resolution code, map to standard resolution code (except for RANGE)
        for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
            int p = query.indexOf(" " + operator + " ");
            if (p != -1 && p + operator.length() + 2 > lastPos) {
                lastPos = p + operator.length() + 2;
                lastOperator = operator;
            }
        }
        if (lastPos != -1) {
            query = query.substring(0, lastPos - lastOperator.length() - 2)
                    + " " + QuantityConstraint.QuantityResolution.ALL_SIGNALS.get(lastOperator).first + " "
                    + query.substring(lastPos);
        }
        // optimize for RANGE "-"
        Matcher m = RANGE_OPTIMIZE_PATTERN.matcher(query);
        if (m.find()) {
            String str = m.group();
            query = query.replace(str, str.replace("-", query.contains("between") ? " and " : " to "));
        }

        // optimize multiplier
        Matcher matcher;
        boolean hasChange;
        do {
            if (!(matcher = MULTIPLIER_OPTIMIZE_PATTERN.matcher(query)).find()) {
                break;
            }
            hasChange = false;
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
                hasChange = true;
            } else if (sub.contains("m")) {
                if (useMillionMul) {
                    mul = " million";
                    hasChange = true;
                } else {
                    mul = "m";
                }
            } else if (sub.contains("b")) {
                mul = " billion";
                hasChange = true;
            }
            query = query.substring(0, start) + " " + num + mul + " " + query.substring(end);
        } while (hasChange);

        return NLP.stripSentence(query);
    }

    public synchronized static String suggestATypeFromRaw(String rawType) {
        String mostSimilarType = null;
        double similarityScore = -1;
        rawType = NLP.fastStemming(rawType, Morpha.noun);
        ArrayList<String> inputType = NLP.splitSentence(rawType);
        String inputTypeHead = NLP.getHeadWord(rawType, true);
        for (Pair<String, Integer> p : TypeSuggestionHandler.typeToFreq) {
            if (p.second < SUGGESTING_THRESHOLD) {
                continue;
            }
            String suggestTypeHead = NLP.getHeadWord(p.first, true);
            double headDist = Glove.cosineDistance(suggestTypeHead, inputTypeHead);
            if (headDist < 0 || headDist > 1 - MIN_SUGGESTING_CONF) {
                continue;
            }
            ArrayList<String> suggestType = NLP.splitSentence(p.first);
            double sim = ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(inputType, suggestType)
                    * ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(suggestType, inputType);
            if (sim > similarityScore) {
                similarityScore = sim;
                mostSimilarType = p.first;
            }
        }
        if (similarityScore >= MIN_SUGGESTING_CONF) {
            LOGGER.info(String.format("Suggested type: %s --> %s (conf: %.3f)", rawType, mostSimilarType, similarityScore));
            return mostSimilarType;
        }
        return null;
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
            StringBuilder postag = new StringBuilder();
            for (PostaggedToken w : tagged) {
                if (rawTokenizedSb.length() > 0) {
                    rawTokenizedSb.append(" ");
                }
                postag.append(new Pair(w.string(), w.postag())).append(" ");
                rawTokenizedSb.append(w.string());
                String rawTokenizedSbStr = rawTokenizedSb.toString();
                if (useTypeSuggestion && TypeSuggestionHandler.getTypeFreq(NLP.fastStemming(rawTokenizedSbStr, Morpha.noun)) >= SUGGESTING_THRESHOLD) {
                    typeFromTypeSuggestionSystem = rawTokenizedSbStr;
                }
            }
            LOGGER.info("Postag: " + postag);
            String rawTokenized = rawTokenizedSb.toString();

            String typeRawStr;
            if (typeFromTypeSuggestionSystem != null) {
                typeRawStr = result.first = typeFromTypeSuggestionSystem;
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

                typeRawStr = result.first;
                // find the most similar type in the type suggestion system.
                if (useTypeSuggestion) {
                    String suggestedType = suggestATypeFromRaw(result.first);
                    if (suggestedType != null) {
                        result.first = suggestedType;
                    }
                }
            }
            // quantity
            // get the last one, or the last one right after a comparison signal.
            String lastQuantityAfterSignal = null;
            String text = rawTokenized + " .";
            main_loop:
            for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(text, true, null)) {
                if (span.object instanceof Quantity) {
                    model.quantity.Quantity.fixQuantityFromIllinois(span, text);
                    String qStr = rawTokenized.substring(span.start, span.end + 1).trim();
                    for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
                        if (qStr.endsWith(operator)) {
                            continue main_loop;
                        }
                    }

                    boolean signalAdded = false;
                    loop:
                    for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
                        String phrase = qStr.replaceFirst("^.*" + Pattern.quote(operator), "").trim();
                        Matcher m = Pattern.compile(Pattern.quote(operator) + "\\s+"
                                // this is added to handle bad extraction from Illinois quantifier:
                                // Ex: query 'teams who won UEFA champions league more than 2 times':
                                // returns only 'times', we need to handle '2'
                                // TODO: we need to remove this after upgrading Illinois quantifier
                                + "(\\d+(\\.\\d+)?\\s+)?"
                                + Pattern.quote(phrase)).matcher(rawTokenized);
                        if (m.find()) {
                            qStr = m.group();
                            signalAdded = true;
                            break loop;
                        }
                    }

                    if (!signalAdded) {
                        for (Pattern p : QuantityConstraint.QuantityResolution.RANGE_SIGNAL) {
                            Matcher m = Pattern.compile(p.pattern() + "\\s+"
                                    // this is added to handle bad extraction from Illinois quantifier:
                                    // Ex: query 'companies with annual profit between 1 and 10 billion usd':
                                    // returns only 'billion usd', we need to handle '10'
                                    // TODO: we need to remove this after upgrading Illinois quantifier
                                    + "(\\d+(\\.\\d+)?\\s+)?"
                                    + Pattern.quote(qStr)).matcher(rawTokenized);
                            if (m.find()) {
                                qStr = m.group();
                                signalAdded = true;
                                break;
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
                    rawTokenized.replace(typeRawStr, " ").replace(result.third, " "));
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
        System.out.println(parse("games with number of players no less than 100 million in 2018"));
        System.out.println(parse("technology companies with annual profit from 100 to 200b usd"));
        System.out.println(parse("celebrities with worth between 1 and 5b usd"));
        System.out.println(parse("cars of germany that costs less than 30 thousand euros"));
        System.out.println(parse("Politician with more than 10 million Euros tax evasion charges "));
    }
}
