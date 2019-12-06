package model.table;

import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.NLP;
import pipeline.PriorBasedEntityTaggingNode;

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
            disambiguatedText = disambiguatedText.replace(l.text, l.quantity.toString(2));
        }
        return disambiguatedText;
    }

    // returns null if either:
    // - has no or more than 1 entity links
    // - the number of token of surface entity text less than REPRESENTATIVE_THRESHOLD of whole cell
    public EntityLink getRepresentativeEntityLink() {
        if (entityLinks.size() != 1) {
            return null;
        }

        // the second check is actually done by PriorEntityTagger, however we double check here, in case the entity is not
        // tagged using this tagger (e.g. WIKIPEDIA corpus has it own annotation)
        return entityLinks.get(0).text.split(" ").length >=
                text.split(" ").length * PriorBasedEntityTaggingNode.REPRESENTATIVE_THRESHOLD
                ? entityLinks.get(0) : null;
    }

    // returns null if either:
    // - has no or more than 1 quantity links
    // - the surface text of cell has extra words bot belonging to the entity
    // - exception: +, - before the quantity (indicates inc/dec-rease); e.g. + 30 %
    public QuantityLink getRepresentativeQuantityLink() {
        if (quantityLinks.size() != 1) {
            return null;
        }
        ArrayList<String> replacedText = NLP.tokenize(text.replace(quantityLinks.get(0).text, ""));
        if (replacedText.size() > 2) {
            return null;
        }
        if (replacedText.size() == 0 || replacedText.get(0).equals("-") || replacedText.get(0).equals("+")) {
            return quantityLinks.get(0);
        }
        return null;
    }

}
