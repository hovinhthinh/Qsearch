package model.quantity;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import model.quantity.kg.KgUnit;
import nlp.NLP;
import util.Constants;

// These are extracted from IllinoisQuantifier.
public class Quantity {
    public double value;
    public Double value2; // value2 is used for range [value-value2], used in quantity constraint
    public String unit;
    public String resolution;

    // these properties is for cached calls
    private transient KgUnit kgUnit;
    private transient String searchDomain, domain;
    private transient Double scale;
    private transient String string;

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

    public KgUnit getKgUnit() {
        if (kgUnit != null) {
            return kgUnit;
        }
        return kgUnit = QuantityDomain.getKgUnitFromUnitStr(unit);
    }

    public double getScale() {
        if (scale != null) {
            return scale;
        }
        KgUnit kgu = getKgUnit();
        String siDomain = kgu.getSIDomain();
        return scale = (kgu.conversionToSI != null
                && (!QuantityDomain.USE_NARROW_SEARCH_DOMAINS || QuantityDomain.Domain.NARROW_SEARCH_DOMAINS.contains(siDomain))
                ? kgu.conversionToSI : 1.0);
    }

    public String getDomain() {
        if (domain != null) {
            return domain;
        }
        return domain = getKgUnit().getDomain();
    }

    public String getSearchDomain() {
        if (searchDomain != null) {
            return searchDomain;
        }
        KgUnit kgu = getKgUnit();
        String siDomain = kgu.getSIDomain();
        return searchDomain = (kgu.conversionToSI != null
                && (!QuantityDomain.USE_NARROW_SEARCH_DOMAINS || QuantityDomain.Domain.NARROW_SEARCH_DOMAINS.contains(siDomain))
                ? siDomain : QuantityDomain.Domain.DIMENSIONLESS);
    }

    public boolean matchesSearchDomain(String domain) {
        if (domain.equals(QuantityDomain.Domain.ANY)) {
            return true;
        }
        return getSearchDomain().equals(domain);
    }

    // 1% diff is considered equals
    // only works for quantities of the same concept.
    public int compareTo(Quantity o) {
        double thisConvertedValue = value * getScale();
        double otherConvertedValue = o.value * o.getScale();

        double maxDiff = Math.max(Math.abs(thisConvertedValue), Math.abs(otherConvertedValue)) * 0.01;
        double diff = Math.abs(thisConvertedValue - otherConvertedValue);
        return diff <= maxDiff ? 0 : (thisConvertedValue < otherConvertedValue ? -1 : 1);
    }

    // get the string representation after converting to unit of target quantity
    // assume that the two quantities are of the same concept
    // return null if no conversion is required
    public String getQuantityConvertedStr(Quantity targetQuantity) {
//        if (!getSearchDomain().equals(targetQuantity.getSearchDomain())) {
//            throw new RuntimeException("Two quantities are of different concepts");
//        }
        double scale = getScale() / targetQuantity.getScale();
        if (Math.abs(scale - 1.0) <= Constants.EPS) {
            return null;
        }
        String suffix = " (" + targetQuantity.unit + ")";
        double convertedValue = scale * value;
        if (Math.abs(convertedValue) >= 1e9) {
            return String.format("%.1f", convertedValue / 1e9) + " billion" + suffix;
        } else if (convertedValue >= 1e6) {
            return String.format("%.1f", convertedValue / 1e6) + " million" + suffix;
        } else if (convertedValue >= 1e5) {
            return String.format("%.0f", convertedValue / 1e3) + " thousand" + suffix;
        } else {
            return String.format("%.2f", convertedValue) + suffix;
        }
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
            // expand to the left
            while (span.start > 0 && tokenizedText.charAt(span.start - 1) != ' ') {
                --span.start;
            }
            // expansion to the right is disabled
//            while (span.end < tokenizedText.length() - 1 && tokenizedText.charAt(span.end + 1) != ' ') {
//                ++span.end;
//            }

            q.units = NLP.stripSentence(q.units);
            q.phrase = tokenizedText.substring(span.start, span.end + 1);

            // Rule-based filters
            if (q.phrase.equals("'s")) {
                return false;
            }

            // Extend to get a non-dimensionless unit
            if (q.units.isEmpty() || q.phrase.endsWith(" " + q.units)) {
                int unitStart = span.end - q.units.length() + 1;
                if (q.units.isEmpty()) {
                    ++unitStart;
                }
                int spanEnd = span.end;

                // extend as far as possible
                boolean extended = false;
                int nExtendedTokens = 0;
                for (int i = spanEnd + 2; i <= tokenizedText.length(); ++i) {
                    if (i == tokenizedText.length() || tokenizedText.charAt(i) == ' ') {
                        ++nExtendedTokens;
                        String extendedUnit = tokenizedText.substring(unitStart, i);
                        if (extendedUnit.substring(q.units.length()).trim().equals("in")) {
                            // not extend this token, as it could be a preposition
                            break;
                        }
                        if (!QuantityDomain.getDomainOfUnit(extendedUnit).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
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
                        if (!QuantityDomain.getDomainOfUnit(shrunkUnit).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
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
