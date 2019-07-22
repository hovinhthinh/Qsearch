package model.quantity;

// These are extracted from IllinoisQuantifier.
public class Quantity {
    // TODO: for now, we do not link these fields to a specific unit corpus. This should be done soon.
    public double value;
    public String unit;
    public String resolution;

    public Quantity(double value, String unit, String resolution) {
        this.value = value;
        this.unit = unit;
        this.resolution = resolution;
    }

    public static Quantity fromQuantityString(String quantityString) {
        try {
            if (quantityString.charAt(0) != '(' || quantityString.charAt(quantityString.length() - 1) != ')') {
                return null;
            }
            quantityString = quantityString.substring(1, quantityString.length() - 1);
            int firstSep = quantityString.indexOf(";");
            int secondSep = quantityString.lastIndexOf(";");
            if (firstSep == secondSep) {
                return null;
            }
            return new Quantity(Double.parseDouble(quantityString.substring(0, firstSep)),
                    quantityString.substring(firstSep + 1, secondSep), quantityString.substring(secondSep + 1));
        } catch (Exception e) {
            // Return null if cannot parse.
            return null;
        }
    }

    // quantityString: "(<value>;<unit>;<resolution>)"
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(String.format("%.3f", value)).append(";")
                .append(unit).append(";")
                .append(resolution).append(")");
        return sb.toString();
    }

    public String toString(int nFixed) {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(String.format("%." + nFixed + "f", value)).append(";")
                .append(unit).append(";")
                .append(resolution).append(")");
        return sb.toString();
    }
}
