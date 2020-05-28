package model.quantity;

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
}
