package model.quantity;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.driver.Quantifier;
import model.query.SimpleQueryParser;
import nlp.NLP;
import util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Not working: "higher than 1 million euros"
public class QuantityConstraint {
    private static transient ThreadLocal<Quantifier> QUANTIFIER_LOCAL = ThreadLocal.withInitial(() -> new Quantifier() {{
        initialize(null);
    }});
    public Quantity quantity; // From Illinois Quantifier.
    public QuantityResolution.Value resolutionCode;
    public boolean resolutionCodeExplicitlyGiven;
    public String domain, searchDomain;
    public String phrase;

    // return first one if available, null otherwise
    private static edu.illinois.cs.cogcomp.quant.standardize.Quantity getQuantityFromStr(String str) {
        try {
            String text = "This quantity is " + str + " .";
            for (QuantSpan span : QUANTIFIER_LOCAL.get().getSpans(text, true, null)) {
                if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                    model.quantity.Quantity.fixQuantityFromIllinois(span, text);
                    return (edu.illinois.cs.cogcomp.quant.standardize.Quantity) span.object;
                }
            }
        } catch (IndexOutOfBoundsException | AnnotatorException e) {
        }
        return null;
    }

    private static Double getMultiplier(edu.illinois.cs.cogcomp.quant.standardize.Quantity q) {
        String surface = q.phrase.toLowerCase();
        if (surface.contains("thousand")) {
            return 1e3;
        } else if (surface.contains("million")) {
            return 1e6;
        } else if (surface.contains("billion")) {
            return 1e9;
        } else {
            return null;
        }
    }

    public static QuantityConstraint parseFromString(String constraintString) {
        constraintString = SimpleQueryParser.preprocess(constraintString);

        constraintString = String.join(" ", NLP.tokenize(constraintString));
        QuantityConstraint c = new QuantityConstraint();
        c.phrase = constraintString;

        try {
            String text = "This quantity is " + constraintString + " .";
            loop:
            for (QuantSpan span : QUANTIFIER_LOCAL.get().getSpans(text, true, null)) { // get last one
                if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                    model.quantity.Quantity.fixQuantityFromIllinois(span, text);
                    edu.illinois.cs.cogcomp.quant.standardize.Quantity q =
                            (edu.illinois.cs.cogcomp.quant.standardize.Quantity) span.object;
                    q.phrase = q.phrase.trim();

                    for (String operator : QuantityConstraint.QuantityResolution.ALL_SIGNALS.keySet()) {
                        if (q.phrase.endsWith(operator)) {
                            continue loop;
                        }
                    }

                    c.quantity = new Quantity(q.value, NLP.stripSentence(q.units), "external"); // not use resolution from IllinoisQuantifier.
                    c.searchDomain = QuantityDomain.getSearchDomain(c.quantity);
                    c.domain = QuantityDomain.getDomain(c.quantity);

                    // all signals except RANGE
                    for (String operator : QuantityResolution.ALL_SIGNALS.keySet()) {
                        String phrase = q.phrase.replaceFirst("^.*" + Pattern.quote(operator), "").trim();
                        Matcher m = Pattern.compile(Pattern.quote(operator) + "\\s+"
                                // this is added to handle bad extraction from Illinois quantifier:
                                // Ex: query 'teams who won UEFA champions league more than 2 times':
                                // returns only 'times', we need to handle '2'
                                // TODO: we need to remove this after upgrading Illinois quantifier
                                + "(\\d+(\\.\\d+)?\\s+)?"
                                + Pattern.quote(phrase)).matcher(constraintString);
                        if (m.find()) {
                            c.resolutionCode = QuantityResolution.ALL_SIGNALS.get(operator).second;
                            c.resolutionCodeExplicitlyGiven = true;

                            // now handle the bad extraction from Illinois quantifier:
                            // TODO: remove after upgrading Illinois quantifier
                            String midSpan = m.group();
                            midSpan = midSpan.substring(0, midSpan.length() - phrase.length()).replaceFirst("^.*" + Pattern.quote(operator), "").trim();
                            c.quantity.value = getQuantityFromStr(midSpan + " " + phrase).value;
                            // DONE

                            break loop;
                        }
                    }

                    // RANGE
                    for (Pattern p : QuantityResolution.RANGE_SIGNAL) {
                        Matcher m = Pattern.compile(p.pattern() + "\\s+"
                                // this is added to handle bad extraction from Illinois quantifier:
                                // Ex: query 'companies with annual profit between 1 and 10 billion usd':
                                // returns only 'billion usd', we need to handle '10'
                                // TODO: we need to remove this after upgrading Illinois quantifier
                                + "(\\d+(\\.\\d+)?\\s+)?"
                                + Pattern.quote(q.phrase)).matcher(constraintString);
                        if (m.find()) {
                            c.resolutionCode = QuantityResolution.Value.RANGE;
                            c.resolutionCodeExplicitlyGiven = true;

                            Matcher m1 = p.matcher(m.group());
                            m1.find();
                            String v1Span = m1.group();

                            // now handle the bad extraction from Illinois quantifier:
                            // TODO: remove after upgrading Illinois quantifier
                            String midSpan = m.group();
                            midSpan = midSpan.substring(0, midSpan.length() - q.phrase.length()).replaceFirst(p.pattern(), "").trim();
                            c.quantity.value = getQuantityFromStr(midSpan + " " + q.phrase).value;
                            // DONE

                            v1Span = v1Span.substring(v1Span.indexOf(' '), v1Span.lastIndexOf(' ')).trim();
                            edu.illinois.cs.cogcomp.quant.standardize.Quantity q1 = getQuantityFromStr(v1Span);

                            c.quantity.value2 = c.quantity.value;
                            c.quantity.value = q1.value;

                            Double mul;
                            if (getMultiplier(q1) == null && (mul = getMultiplier(q)) != null) {
                                c.quantity.value *= mul;
                            }

                            break loop;
                        }
                    }

                    // implicit resolution here
                    c.resolutionCode = Math.abs(c.quantity.value) <= QuantityResolution.APPROXIMATE_THRESHOLD
                            ? QuantityResolution.Value.EXACT
                            : QuantityResolution.Value.APPROXIMATE;
                    c.resolutionCodeExplicitlyGiven = false;
                }
            }
        } catch (IndexOutOfBoundsException | AnnotatorException e) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c.quantity != null ? c : null;
    }

    public static void main(String[] args) {
        System.out.println(parseFromString("2018 less than 100 billion usd"));
        System.out.println(parseFromString("from 1 to 5 billion").toString());
        System.out.println(parseFromString("from 140-201 km").toString());
        System.out.println(parseFromString("between 100 million and 200 billion mpg").toString());
        System.out.println(parseFromString("more than 30 km long").toString());
        System.out.println(parseFromString("less than 30 thousand euros"));
        System.out.println(parseFromString("more than $100 million dollars").match(
                Quantity.fromQuantityString("(22600000.000;US$;=)")));
        System.out.println(parseFromString("from 140 to 201 km").match(Quantity.fromQuantityString("(150000;m;=)")));
    }

    public boolean match(Quantity q) {
        if (!QuantityDomain.quantityMatchesSearchDomain(q, searchDomain)) {
            return false;
        }

        if (resolutionCode == QuantityResolution.Value.DOMAIN_ONLY) {
            return true;
        }

        double qValue = q.value * QuantityDomain.getScale(q);
        double thisQValue = quantity.value * QuantityDomain.getScale(quantity);
        if (resolutionCode == QuantityResolution.Value.UPPER_BOUND) {
            return qValue <= thisQValue;
        } else if (resolutionCode == QuantityResolution.Value.LOWER_BOUND) {
            return qValue >= thisQValue;
        } else if (resolutionCode == QuantityResolution.Value.EXACT) {
            return Math.abs(qValue - thisQValue) <= 1e-3;
        } else if (resolutionCode == QuantityResolution.Value.APPROXIMATE) {
            return Math.abs(qValue - thisQValue) <= Math.abs(thisQValue * QuantityResolution.APPROXIMATE_RATE);
        } else if (resolutionCode == QuantityResolution.Value.RANGE) {
            double thisQValue2 = quantity.value2 * QuantityDomain.getScale(quantity);
            return qValue >= thisQValue && qValue <= thisQValue2;
        } else {
            throw new RuntimeException("Unknown resolution.");
        }
    }

    @Override
    public String toString() {
        return "<" + searchDomain
                + ":" + (resolutionCodeExplicitlyGiven ? "explicit" : "implicit") + "-" + resolutionCode
                + ":" + quantity.toString()
                + ">";
    }

    public static class QuantityResolution {
        public static final int APPROXIMATE_THRESHOLD = 1000;
        public static final double APPROXIMATE_RATE = 0.01;
        public static final ArrayList<String> UPPER_BOUND_SIGNAL = new ArrayList<>(Arrays.asList(
                "less than", "below", "fewer than", "lower than", "under", "at most", "up to", "smaller than", "within", "<", "<="
        ));
        public static final ArrayList<String> LOWER_BOUND_SIGNAL = new ArrayList<>(Arrays.asList(
                "more than", "larger than", "bigger than", "above", "higher than", "above", "at least", "over", "greater than", ">", ">="
        ));
        public static final ArrayList<String> EXACT_SIGNAL = new ArrayList<>(Arrays.asList(
                "exactly", "exact"
        ));
        public static final ArrayList<String> APPROXIMATE_SIGNAL = new ArrayList<>(Arrays.asList(
                "approximately", "approximate", "about", "nearly", "almost", "around"
        ));
        public static final List<Pattern> RANGE_SIGNAL = Arrays.asList(
                Pattern.compile("\\bbetween\\b.*?\\band\\b"),
                Pattern.compile("\\bfrom\\b.*?\\bto\\b")
        );

        // TODO: RANGE, OTHER_THAN

        // map signal to Pair<standard signal and resolution>
        public static LinkedHashMap<String, Pair<String, Value>> ALL_SIGNALS = new LinkedHashMap<>();

        static {
            // handle negate signal:
            for (String s : UPPER_BOUND_SIGNAL) {
                if (s.startsWith("no ") || s.startsWith("not ")) {
                    continue;
                }
                LOWER_BOUND_SIGNAL.add("no " + s);
                LOWER_BOUND_SIGNAL.add("not " + s);
                ALL_SIGNALS.put("no " + s, null); // pre-add key, so that these are handled first.
                ALL_SIGNALS.put("not " + s, null); // pre-add key, so that these are handled first.
            }
            for (String s : LOWER_BOUND_SIGNAL) {
                if (s.startsWith("no ") || s.startsWith("not ")) {
                    continue;
                }
                UPPER_BOUND_SIGNAL.add("no " + s);
                UPPER_BOUND_SIGNAL.add("not " + s);
                ALL_SIGNALS.put("no " + s, null); // pre-add key, so that these are handled first.
                ALL_SIGNALS.put("not " + s, null); // pre-add key, so that these are handled first.
            }

            //
            for (String signal : UPPER_BOUND_SIGNAL) {
                ALL_SIGNALS.put(signal, new Pair<>(UPPER_BOUND_SIGNAL.get(0), Value.UPPER_BOUND));
            }
            for (String signal : LOWER_BOUND_SIGNAL) {
                ALL_SIGNALS.put(signal, new Pair<>(LOWER_BOUND_SIGNAL.get(0), Value.LOWER_BOUND));
            }
            for (String signal : EXACT_SIGNAL) {
                ALL_SIGNALS.put(signal, new Pair<>(EXACT_SIGNAL.get(0), Value.EXACT));
            }
            for (String signal : APPROXIMATE_SIGNAL) {
                ALL_SIGNALS.put(signal, new Pair<>(APPROXIMATE_SIGNAL.get(0), Value.APPROXIMATE));
            }
            // RANGE is handled differently
        }

        // Now handle only these resolutions.
        public enum Value {
            LOWER_BOUND, UPPER_BOUND, EXACT, APPROXIMATE, RANGE,
            DOMAIN_ONLY // This a special constraint, which matches only the domain.
        }
    }
}
