package model.table;

import model.table.link.EntityLink;
import model.table.link.QuantityLink;

import java.util.ArrayList;

public class Cell {
    public String text; // surface text
    public ArrayList<EntityLink> entityLinks;
    public ArrayList<QuantityLink> quantityLinks;

    private transient String disambiguatedText = null;

    public String getDisambiguatedText() {
        if (disambiguatedText != null) {
            return disambiguatedText;
        }
        disambiguatedText = this.text;
        for (EntityLink l : entityLinks) {
            disambiguatedText = disambiguatedText.replace(l.text, "<" + l.target.substring(l.target.lastIndexOf(":") + 1) + ">");
        }
        for (QuantityLink l : quantityLinks) {
            disambiguatedText = disambiguatedText.replace(l.text, l.quantity.toString(1));
        }
        return disambiguatedText;
    }

    // returns null if either:
    // - has no or more than 1 entity links
    // - the surface text of cell has extra words bot belonging to the entity
    public EntityLink getRepresentativeEntityLink() {
        if (entityLinks.size() != 1) {
            return null;
        }
        return text.equals(entityLinks.get(0).text) ? entityLinks.get(0) : null;
    }

    // returns null if either:
    // - has no or more than 1 quantity links
    // - the surface text of cell has extra words bot belonging to the entity
    public QuantityLink getRepresentativeQuantityLink() {
        if (quantityLinks.size() != 1) {
            return null;
        }
        return text.equals(quantityLinks.get(0).text) ? quantityLinks.get(0) : null;
    }

}
