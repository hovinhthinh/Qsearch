package model.text;

import model.text.tag.EntityTag;
import model.text.tag.QuantityTag;
import model.text.tag.TimeTag;

import java.util.ArrayList;

public class QuantitativeFact {
    public EntityTag entityTag;
    public QuantityTag quantityTag;

    public ArrayList<EntityTag> contextEntityTags;
    public ArrayList<TimeTag> contextTimeTags;

    public boolean negated;

    // For now, these are normal context tokens. In training data, entity contexts are stored in contextEntityTags,
    // however in decoding time, all entity contexts are treated the same, and hence stored here.
    public ArrayList<Token> contextTokens;

    // A 0 <= conf <= 1 denotes the confidence from the OpenIE tagging node.
    public double conf;

    public QuantitativeFact() {
        entityTag = null;
        quantityTag = null;
        contextEntityTags = new ArrayList<>();
        contextTimeTags = new ArrayList<>();
        contextTokens = new ArrayList<>();
        negated = false;
        conf = -1;
    }

    public int getContextLength() {
        return contextEntityTags.size() + contextTimeTags.size() + contextTokens.size();
    }
}
