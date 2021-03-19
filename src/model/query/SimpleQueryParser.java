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
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.Pair;
import util.Triple;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleQueryParser {
    public static final Logger LOGGER = Logger.getLogger(SimpleQueryParser.class.getName());

    public static final int SOURCE_CODE_TEXT = 1;
    public static final int SOURCE_CODE_TABLE = 2;

    private static final HashSet<String> TYPE_SEPARATOR =
            new HashSet<>(Arrays.asList("that", "which", "where", "when", "who", "whom", "with", "whose"));
    private static final HashSet<String> BLOCKED_TYPE_FOLLOWED_TOKEN =
            new HashSet<>(Arrays.asList("in", "of", "from"));

    private static final int SUGGESTING_THRESHOLD = 20;
    private static final double MIN_SUGGESTING_CONF = 0.85;
    private static final double MIN_SUGGESTING_HEADWORD_CONF = 0.85;

    private static final Pattern MULTIPLIER_OPTIMIZE_PATTERN =
            Pattern.compile("(?i)(\\$\\s*|\\b)\\d+(\\.\\d+)?(k|m|b| bio| mio)(\\s*\\$|\\b)");

    private static final Pattern RANGE_OPTIMIZE_PATTERN =
            Pattern.compile("\\d+(\\.\\d+)?\\s?-\\s?\\d+(\\.\\d+)?");

    public static Map<String, String> TYPE_SUGGESTION_MAP = Stream.of(new Object[][]{
            {"cricket player", "cricketer"},
            {"nba player", "national basketball association player"},
            {"rail way", "railway"},
            {"rail station", "railway station"},
            {"optical telescope", "telescope"},
            {"football club", "football club"},
            {"basketball club", "basketball team"},
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (String) data[1]));

    public static Map<String, String> TYPE_SUGGESTION_EXPAND_MAP = Stream.of(new Object[][]{
            {"british", "the united kingdom"},
            {"american", "the united state"},
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (String) data[1]));

    // Triple<preprocessed query, original resolution signal, altered resolution signal>
    public static Triple<String, String, String> preprocess(String query) {
        query = NLP.stripSentence(query);
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
            String subLC = sub.toLowerCase();
            String subChanged = null;
            if (subLC.endsWith("k")) {
                subChanged = sub.substring(0, sub.length() - 1) + " thousand";
                hasChange = true;
            } else if (subLC.endsWith("m")) {
                if (useMillionMul || sub.endsWith("M")) {
                    subChanged = sub.substring(0, sub.length() - 1) + " million";
                    hasChange = true;
                } else {
                    subChanged = sub;
                }
            } else if (subLC.endsWith(" mio")) {
                subChanged = sub.substring(0, sub.length() - 4) + " million";
                hasChange = true;
            } else if (subLC.endsWith("b")) {
                subChanged = sub.substring(0, sub.length() - 1) + " billion";
                hasChange = true;
            } else if (subLC.endsWith(" bio")) {
                subChanged = sub.substring(0, sub.length() - 4) + " billion";
                hasChange = true;
            }
            query = query.substring(0, start) + " " + subChanged + " " + query.substring(end);
        } while (hasChange);

        query = NLP.stripSentence(query).toLowerCase();

        // RULE HERE. TODO (Use comparative adjective dictionary instead)
        if (query.contains(" taller than ")) {
            query = query.replace(" taller than ", " more than ") + " in height";
        }
        if (query.contains(" longer than ")) {
            query = query.replace(" longer than ", " more than ") + " in length";
        }
        if (query.contains(" faster than ")) {
            query = query.replace(" faster than ", " more than ") + " in speed";
        }
        if (query.contains(" slower than ")) {
            query = query.replace(" slower than ", " less than ") + " in speed";
        }
        //

        if (query.length() > 0 && Arrays.asList('.', ',', ';', '?').contains(query.charAt(query.length() - 1))) {
            query = query.substring(0, query.length() - 1);
        }
        query = query.replaceFirst("^(what|where|which|who) ", "");
        query = query.replaceFirst("^(am|is|are|was|were|be) ", "");

        int lastPos = -1;
        String lastOperator = null;
        // optimize resolution code, map to standard resolution code (except for RANGE)
        for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
            if (query.startsWith(operator + " ")) {
                if (operator.length() + 1 > lastPos) {
                    lastPos = operator.length() + 1;
                    lastOperator = operator;
                }
            } else {
                int p = query.indexOf(" " + operator + " ");
                if (p != -1 && p + operator.length() + 2 > lastPos) {
                    lastPos = p + operator.length() + 2;
                    lastOperator = operator;
                }
            }
        }
        String originalResolutionSignal = null;
        String alteredResolutionSignal = null;
        if (lastPos != -1) {
            originalResolutionSignal = lastOperator;
            alteredResolutionSignal = QuantityConstraint.QuantityResolution.ALL_SIGNALS.get(lastOperator).first;
            query = (lastPos == lastOperator.length() + 1 ? "" :
                    query.substring(0, lastPos - lastOperator.length() - 2) + " ")
                    + alteredResolutionSignal + " "
                    + query.substring(lastPos);
        }
        // optimize for RANGE "-"
        Matcher m = RANGE_OPTIMIZE_PATTERN.matcher(query);
        if (m.find()) {
            String str = m.group();
            query = query.replace(str, str.replace("-", query.contains("between") ? " and " : " to "));
        }

        return new Triple<>(NLP.stripSentence(query), originalResolutionSignal, alteredResolutionSignal);
    }

    // rule-based, because the fast stemming might return head with 2 words. The good stemming is too slow to use.
    public static String fixHeadWord(String head) {
        if (head.equals("football team")) {
            return "team";
        }
        return head;
    }

    public static List<ArrayList<String>> expandRawType(String rawType) {
        List<String> types = new ArrayList<>();
        types.add(rawType);

        rawType = " " + rawType + " ";
        for (Map.Entry<String, String> e : TYPE_SUGGESTION_EXPAND_MAP.entrySet()) {
            String t = " " + e.getKey() + " ";
            if (rawType.contains(t)) {
                types.add(rawType.replace(t, " " + e.getValue() + " "));
            }
        }

        return types.stream().map(t -> NLP.splitSentence(t)).collect(Collectors.toList());
    }

    // find closest related type in the YAGO dictionary, by comparing headword similarity and full string similarity
    // returns Pair<suggestType, typeContext>
    public synchronized static Pair<String, String> suggestATypeFromRaw(String rawType, int typeSuggestionCode) {
        double similarityScore = -1;
        String stemmedRawType = NLP.fastStemming(rawType.toLowerCase(), Morpha.noun);

        String mostSimilarType = TYPE_SUGGESTION_MAP.get(stemmedRawType);
        if (mostSimilarType != null) {
            LOGGER.info(String.format("Suggested type: %s --> %s (conf: N/A)", stemmedRawType, mostSimilarType));
            return new Pair<>(mostSimilarType, "");
        }

        List<ArrayList<String>> inputType = expandRawType(stemmedRawType);
        String inputTypeHead = fixHeadWord(NLP.getHeadWord(stemmedRawType, true));
        ArrayList<Pair<String, Integer>> typeToFreq = typeSuggestionCode == SOURCE_CODE_TEXT
                ? server.text.handler.TypeSuggestionHandler.typeToFreq
                : (typeSuggestionCode == SOURCE_CODE_TABLE ? server.table.handler.TypeSuggestionHandler.typeToFreq : null);
        for (Pair<String, Integer> p : typeToFreq) {
            if (p.second < SUGGESTING_THRESHOLD) {
                continue;
            }
            String suggestTypeHead = fixHeadWord(NLP.getHeadWord(p.first, true));
            double headDist = Glove.cosineDistance(suggestTypeHead, inputTypeHead);
            if (headDist < 0 || headDist > 1 - MIN_SUGGESTING_HEADWORD_CONF) {
                continue;
            }
            ArrayList<String> suggestType = NLP.splitSentence(p.first);

            // restrict size difference
//            if (Math.abs(inputType.size() - suggestType.size()) > 1) {
//                continue;
//            }
            double sim = -1;
            for (ArrayList<String> t : inputType) {
                sim = Math.max(sim, ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(t, suggestType)
                        * ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(suggestType, t));
            }
            if (sim > similarityScore) {
                similarityScore = sim;
                mostSimilarType = p.first;
            }
        }
        if (similarityScore >= MIN_SUGGESTING_CONF) {
            LOGGER.info(String.format("Suggested type: %s --> %s (conf: %.3f)", stemmedRawType, mostSimilarType, similarityScore));
            String typeContext = "";
            // film music composer --> composer
            // fine-grained types cannot be detected, so that these extra tokens are put into context.
            // only applied for tables.
            if (typeSuggestionCode == SOURCE_CODE_TABLE) {
                String remaining = (" " + stemmedRawType + " ").replace(" " + mostSimilarType + " ", " ").trim();
                if (!remaining.equals(stemmedRawType)) {
                    typeContext = remaining;
                }
            }
            return new Pair<>(mostSimilarType, typeContext);
        }
        return null;
    }

    // suggestion code = 0 means no suggestion.
    public synchronized static Triple<String, String, String> parse(String rawQuery, int typeSuggestionCode) {
        rawQuery = NLP.stripSentence(rawQuery);
        Triple<String, String, String> prep = preprocess(rawQuery);
        String preprocessedQuery = prep.first;
        LOGGER.info("Preprocessed_query: " + preprocessedQuery);
        Triple<String, String, String> result = new Triple<>();
        try {
            ArrayList<PostaggedToken> tagged = new ArrayList<>(JavaConversions.seqAsJavaList(Static.getOpenIe().postagger().postagTokenized(
                    Static.getOpenIe().tokenizer().tokenize(preprocessedQuery))));
            // type
            String typeFromTypeSuggestionSystem = null;

            StringBuilder rawTokenizedSb = new StringBuilder();
            StringBuilder postag = new StringBuilder();
            for (int i = 0; i < tagged.size(); ++i) {
                PostaggedToken w = tagged.get(i);
                if (rawTokenizedSb.length() > 0) {
                    rawTokenizedSb.append(" ");
                }
                postag.append(new Pair(w.string(), w.postag())).append(" ");
                rawTokenizedSb.append(w.string());
                String rawTokenizedSbStr = rawTokenizedSb.toString();
                int typeFreq = typeSuggestionCode == SOURCE_CODE_TEXT
                        ? server.text.handler.TypeSuggestionHandler.getTypeFreq(NLP.fastStemming(rawTokenizedSbStr, Morpha.noun))
                        : (typeSuggestionCode == SOURCE_CODE_TABLE
                        ? server.table.handler.TypeSuggestionHandler.getTypeFreq(NLP.fastStemming(rawTokenizedSbStr, Morpha.noun))
                        : -1);
                if (typeSuggestionCode != 0 && typeFreq >= SUGGESTING_THRESHOLD) {
                    boolean goodFollowedToken = true;
                    if (i < tagged.size() - 1) {
                        PostaggedToken followedToken = tagged.get(i + 1);
                        if (followedToken.postag().startsWith("NN") ||
                                (followedToken.postag().equals("IN") && BLOCKED_TYPE_FOLLOWED_TOKEN.contains(followedToken.string()))) {
                            goodFollowedToken = false;
                        }
                    }
                    if (goodFollowedToken) {
                        typeFromTypeSuggestionSystem = rawTokenizedSbStr;
                    }
                }
            }
            LOGGER.info("Postag: " + postag);
            String rawTokenized = rawTokenizedSb.toString();

            String typeRawStr;
            ArrayList<String> contextTokensFromType = null;
            if (typeFromTypeSuggestionSystem != null) {
                typeRawStr = result.first = typeFromTypeSuggestionSystem;
            } else {
                StringBuilder type = new StringBuilder();
                boolean hasNoun = false;

                int index = -1;
                loop:
                for (PostaggedToken t : tagged) {
                    ++index;
                    if (index > 0 && (t.postag().startsWith("VB") || TYPE_SEPARATOR.contains(t.string())
                            || (hasNoun && (t.postag().startsWith("RB") || t.postag().startsWith("JJ"))))) {
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
                if (typeSuggestionCode != 0) {
                    Pair<String, String> suggestedType = suggestATypeFromRaw(result.first, typeSuggestionCode);
                    if (suggestedType != null) {
                        result.first = suggestedType.first;
                        contextTokensFromType = NLP.splitSentence(suggestedType.second);
                    }
                }
            }
            if (typeRawStr == null) {
                return null;
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
                    // shrink to get a non-dimensional unit
                    if (constraint.domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {

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
                            if (!newConstraint.domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
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
            if (contextTokensFromType != null) {
                context.addAll(contextTokensFromType);
            }
            StringBuilder cSb = new StringBuilder();
            for (String s : context) {
                if (NLP.BLOCKED_STOPWORDS.contains(s) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(s)) {
                    continue;
                }
                cSb.append(" ").append(s);
            }

            // RULE HERE. TODO (Use comparative adjective dictionary instead)
            if (cSb.length() == 0 && rawQuery.toLowerCase().contains(" higher than ")) {
                cSb.append("height");
            }

            result.second = cSb.toString().trim();

            if (prep.second != null) {
                result.third = result.third.replace(prep.third, prep.second);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized static Triple<String, String, String> parse(String rawQuery) {
        return parse(rawQuery, 0);
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

        System.out.println(parse("technology companies with more than 10M Euro annual profit"));
        System.out.println(parse("sprinters who ran 200m in less than 25 s"));
        System.out.println(parse("companies with profit in 2018 under 100b usd"));
        System.out.println(parse("games with number of players no less than 100 million in 2018"));
        System.out.println(parse("technology companies with annual profit from 100 to 200b usd"));
        System.out.println(parse("celebrities with worth between 1 and 5b usd"));
        System.out.println(parse("cars of germany that costs less than 30 thousand euros"));
        System.out.println(parse("Politician with more than 10 million Euros tax evasion charges "));
        System.out.println(parse("skyscraper higher than 100m"));
        System.out.println(parse("skyscraper taller than 100m"));
        System.out.println(parse("bridges longer than 100m"));
        System.out.println(parse("skyscraper with cost higher than 100m$"));
    }
}
