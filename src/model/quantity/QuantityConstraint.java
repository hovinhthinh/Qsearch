package model.quantity;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.driver.Quantifier;
import model.query.SimpleQueryParser;
import nlp.NLP;
import util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    public String domain;
    public String fineGrainedDomain;
    public String phrase;

    // return first one if available, null otherwise
    private static edu.illinois.cs.cogcomp.quant.standardize.Quantity getQuantityFromStr(String str) {
        try {
            for (QuantSpan span : QUANTIFIER_LOCAL.get().getSpans("This quantity is " + str + " .", true, null)) {
                if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                    return (edu.illinois.cs.cogcomp.quant.standardize.Quantity) span.object;
                }
            }
        } catch (IndexOutOfBoundsException | AnnotatorException e) {
        }
        return null;
    }

    public static QuantityConstraint parseFromString(String constraintString) {
        constraintString = SimpleQueryParser.preprocess(constraintString);

        constraintString = String.join(" ", NLP.tokenize(constraintString));
        QuantityConstraint c = new QuantityConstraint();
        c.phrase = constraintString;

        try {
            boolean flag = false;
            loop:
            for (QuantSpan span : QUANTIFIER_LOCAL.get().getSpans("This quantity is " + constraintString + " .", true, null)) { // get last one
                if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                    edu.illinois.cs.cogcomp.quant.standardize.Quantity q =
                            (edu.illinois.cs.cogcomp.quant.standardize.Quantity) span.object;
                    c.quantity = new Quantity(q.value, NLP.stripSentence(q.units), "external"); // not use resolution from
                    // IllinoisQuantifier.
                    Pair<QuantityResolution.Value, Boolean> resolution = QuantityResolution.getResolution(constraintString, c.quantity);
                    c.resolutionCode = resolution.first;
                    c.resolutionCodeExplicitlyGiven = resolution.second;
                    c.domain = QuantityDomain.getDomain(c.quantity);
                    c.fineGrainedDomain = QuantityDomain.getFineGrainedDomain(c.quantity);

                    if (c.resolutionCode == QuantityResolution.Value.RANGE) {
                        // parse the first value
                        c.quantity.value2 = c.quantity.value;
                        q.phrase = q.phrase.trim();
                        for (Pattern p : QuantityResolution.RANGE_SIGNAL) {
                            Matcher m = Pattern.compile(p.pattern() + "\\s+" + Pattern.quote(q.phrase)).matcher(constraintString);
                            if (m.find()) {
                                String v1Span = m.group();
                                v1Span = v1Span.substring(0, v1Span.length() - q.phrase.length()).trim();
                                v1Span = v1Span.substring(v1Span.indexOf(' '), v1Span.lastIndexOf(' ')).trim();
                                edu.illinois.cs.cogcomp.quant.standardize.Quantity v1 = getQuantityFromStr(v1Span);
                                if (v1 != null) {
                                    c.quantity.value = v1.value;
                                    flag = true;
                                    break loop;
                                }
                            }
                        }
                    } else {
                        flag = true;
                    }
                }
            }
            if (flag) {
                return c;
            }
        } catch (IndexOutOfBoundsException | AnnotatorException e) {
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println(parseFromString("from 140-201 km").toString());
        System.out.println(parseFromString("between 100 million and 200 billion mpg").toString());
        System.out.println(parseFromString("more than 30 km long").toString());

        System.out.println(parseFromString("more than $100 million dollars").match(
                Quantity.fromQuantityString("(22600000.000;US$;=)")));
        System.out.println(parseFromString("from 140 to 201 km").match(Quantity.fromQuantityString("(150000;m;=)")));
    }

    public boolean match(Quantity q) {
        if (!domain.equals(QuantityDomain.getDomain(q))) {
            return false;
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
        return "<" + domain
                + ":" + (resolutionCodeExplicitlyGiven ? "explicit" : "implicit") + "-" + resolutionCode
                + ":" + quantity.toString()
                + ">";
    }

    public static class QuantityResolution {
        private static final int APPROXIMATE_THRESHOLD = 1000;
        private static final double APPROXIMATE_RATE = 0.01;
        private static final LinkedHashSet<String> UPPER_BOUND_SIGNAL = new LinkedHashSet<>(Arrays.asList(
                "less than", "within", "below", "lesser", "lower than", "under", "at most", "up to", "smaller than", "<", "<="
        ));
        private static final LinkedHashSet<String> LOWER_BOUND_SIGNAL = new LinkedHashSet<>(Arrays.asList(
                "more than", "above", "higher than", "above", "at least", "over", "greater than", ">", ">="
        ));
        private static final LinkedHashSet<String> EXACT_SIGNAL = new LinkedHashSet<>(Arrays.asList(
                "exactly", "exact"
        ));
        private static final ArrayList<Pattern> RANGE_SIGNAL = new ArrayList<Pattern>() {{
            add(Pattern.compile("\\bbetween\\b.*?\\band\\b"));
            add(Pattern.compile("\\bfrom\\b.*?\\bto\\b"));
        }};

        // TODO: RANGE, OTHER_THAN

        // map signal to standard signal
        public static HashMap<String, String> ALL_SIGNALS = new HashMap<>();

        static {
            for (LinkedHashSet<String> signalSet : Arrays.asList(UPPER_BOUND_SIGNAL, LOWER_BOUND_SIGNAL, EXACT_SIGNAL)) {
                String mainSignal = signalSet.iterator().next();
                signalSet.forEach(o -> ALL_SIGNALS.put(o, mainSignal));
            }
        }

        // return resolution code + is explicitly given.
        public static Pair<Value, Boolean> getResolution(String constraintString, Quantity quantity) {
            constraintString = NLP.stripSentence(constraintString).toLowerCase();
            for (String s : UPPER_BOUND_SIGNAL) {
                if (constraintString.contains(s)) {
                    return new Pair<>(
                            (constraintString.contains("not ") || constraintString.contains("no ")) ?
                                    Value.LOWER_BOUND : Value.UPPER_BOUND,
                            true);
                }
            }
            for (String s : LOWER_BOUND_SIGNAL) {
                if (constraintString.contains(s)) {
                    return new Pair<>(
                            (constraintString.contains("not ") || constraintString.contains("no ")) ?
                                    Value.UPPER_BOUND : Value.LOWER_BOUND,
                            true);
                }
            }
            for (String s : EXACT_SIGNAL) {
                if (constraintString.contains(s)) {
                    return new Pair<>(Value.EXACT, true);
                }
            }
            for (Pattern p : RANGE_SIGNAL) {
                if (p.matcher(constraintString).find()) {
                    return new Pair<>(Value.RANGE, true);
                }
            }

            return new Pair<>(
                    Math.abs(quantity.value) <= APPROXIMATE_THRESHOLD ? Value.EXACT : Value.APPROXIMATE,
                    false);
        }
        // anything else is approximate

        // Now handle only these resolutions.
        public enum Value {
            LOWER_BOUND, UPPER_BOUND, EXACT, APPROXIMATE, RANGE
        }
    }
}
