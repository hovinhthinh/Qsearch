package model.text.tag;

import model.quantity.Quantity;

public class QuantityTag extends Tag {
    // TODO: for now, we do not link these fields to a specific unit corpus. This should be done soon.
    public Quantity quantity;

    public QuantityTag(int beginIndex, int endIndex, double value, String unit, String resolution) {
        super(beginIndex, endIndex);
        PLACEHOLDER = Placeholder.QUANTITY;
        this.quantity = new Quantity(value, unit, resolution);
    }
}
