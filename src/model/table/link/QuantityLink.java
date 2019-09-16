package model.table.link;

import model.quantity.Quantity;

public class QuantityLink {
    // TODO: for now, we do not link these fields to a specific unit corpus. This should be done soon.
    public Quantity quantity;
    public String text;

    public QuantityLink(String text, double value, String unit, String resolution) {
        this.text = text;
        this.quantity = new Quantity(value, unit, resolution);
    }
}
