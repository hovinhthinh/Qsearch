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
                        q.phrase = q.phrase.trim();
                        for (Pattern p : QuantityResolution.RANGE_SIGNAL) {
                            Matcher m = Pattern.compile(p.pattern()
                                    + "\\s+"
                                    // this is added to handle bad extraction from Illinois quantifier:
                                    // Ex: query 'companies with annual profit between 1 and 10 billion usd':
                                    // returns only 'billion usd', we need to handle '10'
                                    // TODO: we need to remove this after upgrading Illinois quantifier
                                    + "(\\d+(\\.\\d+)?\\s+)?"
                                    + Pattern.quote(q.phrase)).matcher(constraintString);
                            if (m.find()) {
                                Matcher m1 = p.matcher(m.group());
                                m1.find();
                                String v1Span = m1.group();

                                // now handle the bad extraction from Illinois quantifier:
                                // TODO: remove after upgrading Illinois quantifier
                                String midSpan = m.group();
                                midSpan = midSpan.substring(0, midSpan.length() - q.phrase.length()).replaceFirst(p.pattern(), "").trim();
                                if (!midSpan.isEmpty()) {
                                    c.quantity.value = getQuantityFromStr(midSpan + " " + q.phrase).value;
                                } // DONE

                                v1Span = v1Span.substring(v1Span.indexOf(' '), v1Span.lastIndexOf(' ')).trim();
                                edu.illinois.cs.cogcomp.quant.standardize.Quantity q1 = getQuantityFromStr(v1Span);
                                if (q1 != null) {
                                    c.quantity.value2 = c.quantity.value;
                                    c.quantity.value = q1.value;

                                    Double mul;
                                    if (getMultiplier(q1) == null && (mul = getMultiplier(q)) != null) {
                                        c.quantity.value *= mul;
                                    }

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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println(parseFromString("from 1 to 5 billion").toString());
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
        public static final int APPROXIMATE_THRESHOLD = 1000;
        public static final double APPROXIMATE_RATE = 0.01;
        public static final LinkedHashSet<String> UPPER_BOUND_SIGNAL = new LinkedHashSet<>(Arrays.asList(
                "less than", "within", "below", "lesser", "lower than", "under", "at most", "up to", "smaller than", "<", "<="
        ));
        public static final LinkedHashSet<String> LOWER_BOUND_SIGNAL = new LinkedHashSet<>(Arrays.asList(
                "more than", "above", "higher than", "above", "at least", "over", "greater than", ">", ">="
        ));
        public static final LinkedHashSet<String> EXACT_SIGNAL = new LinkedHashSet<>(Arrays.asList(
                "exactly", "exact"
        ));
        public static final ArrayList<Pattern> RANGE_SIGNAL = new ArrayList<Pattern>() {{
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
