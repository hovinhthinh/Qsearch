package model.table;

import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import model.table.link.TimeLink;
import pipeline.PriorBasedEntityTaggingNode;

import java.util.ArrayList;

public class Cell {
    public String text; // surface text
    public ArrayList<EntityLink> entityLinks;
    public ArrayList<QuantityLink> quantityLinks;
    public ArrayList<TimeLink> timeLinks;

    private transient boolean calledELink = false;
    private transient EntityLink repELink = null;

    private transient boolean calledQLink = false;
    private transient QuantityLink repQLink = null;

    private transient boolean calledTLink = false;
    private transient TimeLink repTLink = null;

    private transient String disambiguatedText = null;

    public String getDisambiguatedText() {
        return getDisambiguatedText(false);
    }

    public String getDisambiguatedText(boolean disambiguateSingleLink) {
//        if (disambiguatedText != null) {
//            return disambiguatedText;
//        }
        disambiguatedText = this.text;
        for (EntityLink l : entityLinks) {
            disambiguatedText = disambiguatedText.replace(l.text, "<" + l.target.substring(l.target.lastIndexOf(":") + 1) + ">");
            if (disambiguateSingleLink) {
                return disambiguatedText;
            }
        }
        for (TimeLink l : timeLinks) {
            disambiguatedText = disambiguatedText.replace(l.text, "<" + l.temporalStr + ">");
            if (disambiguateSingleLink) {
                return disambiguatedText;
            }
        }
        for (QuantityLink l : quantityLinks) {
            disambiguatedText = disambiguatedText.replace(l.text, l.quantity.toString(2));
            if (disambiguateSingleLink) {
                return disambiguatedText;
            }
        }
        return disambiguatedText;
    }

    // returns null if either:
    // - has no or more than 1 entity links
    // - the number of token of surface entity text less than REPRESENTATIVE_THRESHOLD of whole cell
    public EntityLink getRepresentativeEntityLink() {
        if (calledELink) {
            return repELink;
        }
        calledELink = true;

        if (entityLinks.size() != 1) {
            return null;
        }

        // the second check is actually done by PriorEntityTagger, however we double check here, in case the entity is not
        // tagged using this tagger (e.g. WIKIPEDIA corpus has it own annotation)
        String[] arr = text.split(" ");
        int repLength = arr.length;
        for (String s : arr) {
            if (s.equals("(") || s.equals(")") || s.equals("\"") || s.equals("'")) {
                --repLength;
            }
        }
        return repELink = (entityLinks.get(0).text.split(" ").length >= repLength * PriorBasedEntityTaggingNode.REPRESENTATIVE_THRESHOLD
                ? entityLinks.get(0) : null);
    }

    // returns null if either:
    // - has no or more than 1 quantity links
    // - the surface text of cell has extra words bot belonging to the entity
    // - exception: +, - before the quantity (indicates inc/dec-rease); e.g. + 30 %
    public QuantityLink getRepresentativeQuantityLink() {
        if (calledQLink) {
            return repQLink;
        }
        calledQLink = true;

        if (quantityLinks.size() != 1) {
            return null;
        }
        int quantityPos = text.indexOf(quantityLinks.get(0).text);
        if (quantityPos == -1) {
            return null;
        }
        String before = text.substring(0, quantityPos).trim();
        String after = text.substring(quantityPos + quantityLinks.get(0).text.length()).trim();
        if (before.contains(" ") || after.contains(" ")) {
            return null;
        }
        if (before.isEmpty() || before.equals("+") || before.equals("-") || before.equals("≈")) {
            return repQLink = quantityLinks.get(0);
        }
        return null;
    }

    // returns null if either:
    // - has no or more than 1 time links
    // - the surface text of cell has extra words bot belonging to the time
    public TimeLink getRepresentativeTimeLink() {
        if (calledTLink) {
            return repTLink;
        }
        calledTLink = true;

        if (timeLinks.size() == 1 && text.equals(timeLinks.get(0).text)) {
            return repTLink = timeLinks.get(0);
        } else {
            return null;
        }
    }

    public void resetCachedRepresentativeLink() {
        calledELink = calledQLink = calledTLink = false;
    }
}
