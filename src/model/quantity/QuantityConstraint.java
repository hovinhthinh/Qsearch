package model.quantity;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.driver.Quantifier;
import model.query.SimpleQueryParser;
import nlp.NLP;

import java.util.Arrays;
import java.util.HashSet;


// Not working: "higher than 1 million euros"
public class QuantityConstraint {
    private static transient ThreadLocal<Quantifier> QUANTIFIER_LOCAL = ThreadLocal.withInitial(() -> new Quantifier() {{
        initialize(null);
    }});
    public Quantity quantity; // From Illinois Quantifier.
    public QuantityResolution.Value resolutionCode;
    public String domain;
    public String fineGrainedDomain;
    public String phrase;

    public static QuantityConstraint parseFromString(String constraintString) {
        constraintString = SimpleQueryParser.preprocess(constraintString);

        constraintString = String.join(" ", NLP.tokenize(constraintString));
        QuantityConstraint c = new QuantityConstraint();
        c.phrase = constraintString;

        try {
            boolean flag = false;
            for (QuantSpan span : QUANTIFIER_LOCAL.get().getSpans("This quantity is " + constraintString + " .", true, null)) { // get last one
                if (span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity) {
                    edu.illinois.cs.cogcomp.quant.standardize.Quantity q =
                            (edu.illinois.cs.cogcomp.quant.standardize.Quantity) span.object;
                    c.quantity = new Quantity(q.value, NLP.stripSentence(q.units), "external"); // not use resolution from
                    // IllinoisQuantifier.
                    c.resolutionCode = QuantityResolution.getResolution(constraintString, c.quantity);
                    c.domain = QuantityDomain.getDomain(c.quantity);
                    c.fineGrainedDomain = QuantityDomain.getFineGrainedDomain(c.quantity);
                    flag = true;
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
        System.out.println(parseFromString("more than 30 km long").toString());
        System.out.println(parseFromString("more than $100 million dollars").match(Quantity.fromQuantityString(
                "(22600000.000;US$;=)")));
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
        } else {
            throw new RuntimeException("Unknown resolution.");
        }
    }

    @Override
    public String toString() {
        return "<" + domain + ":" + resolutionCode + ":" + quantity.toString() + ">";
    }

    public static class QuantityResolution {
        private static final int APPROXIMATE_THRESHOLD = 1000;
        private static final double APPROXIMATE_RATE = 0.01;
        private static final HashSet<String> UPPER_BOUND_SIGNAL = new HashSet<>(Arrays.asList(
                "within", "less than", "below", "lesser", "lower than", "under", "at most", "up to", "smaller than", "<", "<="
        ));
        private static final HashSet<String> LOWER_BOUND_SIGNAL = new HashSet<>(Arrays.asList(
                "more than", "above", "higher than", "above", "at least", "over", "greater than", ">", ">="
        ));
        private static final HashSet<String> EXACT_SIGNAL = new HashSet<>(Arrays.asList(
                "exact", "exactly"
        ));
        // TODO: RANGE, OTHER_THAN
        public static HashSet<String> ALL_SIGNALS = new HashSet<>();

        static {
            ALL_SIGNALS.addAll(UPPER_BOUND_SIGNAL);
            ALL_SIGNALS.addAll(LOWER_BOUND_SIGNAL);
            ALL_SIGNALS.addAll(EXACT_SIGNAL);
        }

        public static Value getResolution(String constraintString, Quantity quantity) {
            constraintString = NLP.stripSentence(constraintString).toLowerCase();
            for (String s : UPPER_BOUND_SIGNAL) {
                if (constraintString.contains(s)) {
                    return (constraintString.contains("not ") || constraintString.contains("no ")) ?
                            Value.LOWER_BOUND : Value.UPPER_BOUND;
                }
            }
            for (String s : LOWER_BOUND_SIGNAL) {
                if (constraintString.contains(s)) {
                    return (constraintString.contains("not ") || constraintString.contains("no ")) ?
                            Value.UPPER_BOUND : Value.LOWER_BOUND;
                }
            }
            for (String s : EXACT_SIGNAL) {
                if (constraintString.contains(s)) {
                    return Value.EXACT;
                }
            }

            return Math.abs(quantity.value) <= APPROXIMATE_THRESHOLD ? Value.EXACT : Value.APPROXIMATE;
        }
        // anything else is approximate

        // Now handle only these resolutions.
        public enum Value {
            LOWER_BOUND, UPPER_BOUND, EXACT, APPROXIMATE
        }

    }
}
