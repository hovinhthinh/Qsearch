package model.quantity;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import nlp.NLP;

// These are extracted from IllinoisQuantifier.
public class Quantity {
    public double value;
    public Double value2; // value2 is used for range [value-value2]
    public String unit;
    public String resolution;

    // these two properties is for cached calls
    public transient String domain, fineGrainedDomain;
    public transient Double scale, fineGrainedScale;
    public transient String string;

    public Quantity(double value, String unit, String resolution) {
        this(value, null, unit, resolution);
    }

    public Quantity(double value, Double value2, String unit, String resolution) {
        this.value = value;
        this.value2 = value2;
        this.unit = unit;
        this.resolution = resolution;
    }

    public static Quantity fromQuantityString(String quantityString) {
        try {
            if (quantityString.charAt(0) != '(' || quantityString.charAt(quantityString.length() - 1) != ')') {
                return null;
            }
            quantityString = quantityString.substring(1, quantityString.length() - 1);
            int firstSep = quantityString.indexOf(';');
            int secondSep = quantityString.lastIndexOf(';');
            if (firstSep == secondSep) {
                return null;
            }
            if (quantityString.charAt(0) == '[') {
                int comma = quantityString.indexOf(',');
                return new Quantity(Double.parseDouble(quantityString.substring(1, comma)), Double.parseDouble(quantityString.substring(comma + 1, firstSep - 1)),
                        quantityString.substring(firstSep + 1, secondSep), quantityString.substring(secondSep + 1));
            } else {
                return new Quantity(Double.parseDouble(quantityString.substring(0, firstSep)),
                        quantityString.substring(firstSep + 1, secondSep), quantityString.substring(secondSep + 1));
            }
        } catch (Exception e) {
            // Return null if cannot parse.
            return null;
        }
    }

    // quantityString: "(<value>;<unit>;<resolution>)"
    @Override
    public String toString() {
        if (string != null) {
            return string;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(value2 == null ? String.format("%.3f", value) : String.format("[%.3f,%.3f]", value, value2))
                .append(";").append(unit).append(";")
                .append(resolution).append(")");
        return (string = sb.toString());
    }

    public String toString(int nFixed) {
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(value2 == null ? String.format("%." + nFixed + "f", value) : String.format("[%." + nFixed + "f,%." + nFixed + "f]", value, value2))
                .append(";").append(unit).append(";")
                .append(resolution).append(")");
        return sb.toString();
    }

    // 1% diff is considered equals
    // only works for quantities of the same concept.
    public int compareTo(Quantity o) {
        double thisConvertedValue = value * QuantityDomain.getScale(this);
        double otherConvertedValue = o.value * QuantityDomain.getScale(o);

        double maxDiff = Math.max(Math.abs(thisConvertedValue), Math.abs(otherConvertedValue)) * 0.01;
        double diff = Math.abs(thisConvertedValue - otherConvertedValue);
        return diff <= maxDiff ? 0 : (thisConvertedValue < otherConvertedValue ? -1 : 1);
    }

    public static boolean fixQuantityFromIllinois(QuantSpan span, String tokenizedText) {
        if (!(span.object instanceof edu.illinois.cs.cogcomp.quant.standardize.Quantity)) {
            return false;
        }
        edu.illinois.cs.cogcomp.quant.standardize.Quantity q = (edu.illinois.cs.cogcomp.quant.standardize.Quantity) span.object;
        try {
            while (span.start <= span.end && tokenizedText.charAt(span.start) == ' ') {
                ++span.start;
            }
            while (span.start <= span.end && tokenizedText.charAt(span.end) == ' ') {
                --span.end;
            }

            q.units = NLP.stripSentence(q.units);
            q.phrase = tokenizedText.substring(span.start, span.end + 1);

            // Extend to get a non-dimensionless unit
            if (q.phrase.endsWith(" " + q.units)) {
                int unitStart = span.end - q.units.length() + 1;
                int spanEnd = span.end;

                // extend as far as possible
                boolean extended = false;
                int nExtendedTokens = 0;
                for (int i = spanEnd + 2; i <= tokenizedText.length(); ++i) {
                    if (i == tokenizedText.length() || tokenizedText.charAt(i) == ' ') {
                        ++nExtendedTokens;
                        String extendedUnit = tokenizedText.substring(unitStart, i);
                        if (!QuantityDomain.getFineGrainedDomainOfUnit(extendedUnit).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                            q.units = extendedUnit;
                            span.end = i - 1;
                            q.phrase = tokenizedText.substring(span.start, span.end + 1);
                            extended = true;
                        }
                        if (nExtendedTokens == 2) { // Extends at most 2 tokens.
                            break;
                        }
                    }
                }
                if (extended) {
                    return true;
                }

                // shrink to the first
                for (int i = spanEnd + 1; i > unitStart; --i) {
                    if (i == spanEnd + 1 || tokenizedText.charAt(i) == ' ') {
                        String shrunkUnit = tokenizedText.substring(unitStart, i);
                        if (!QuantityDomain.getFineGrainedDomainOfUnit(shrunkUnit).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                            q.units = shrunkUnit;
                            span.end = i - 1;
                            q.phrase = tokenizedText.substring(span.start, span.end + 1);
                            return true;
                        }
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            return false;
        }

        return true;
    }
}
