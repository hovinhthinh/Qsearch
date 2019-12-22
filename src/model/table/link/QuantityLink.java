package model.table.link;

import model.quantity.Quantity;

public class QuantityLink {
    public Quantity quantity;
    public String text;

    public QuantityLink(String text, double value, String unit, String resolution) {
        this.text = text;
        this.quantity = new Quantity(value, unit, resolution);
    }
}
