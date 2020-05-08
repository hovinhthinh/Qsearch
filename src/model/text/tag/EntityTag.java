package model.text.tag;

public class EntityTag extends Tag {
    public String id; // id in KB
    public double confidence; // -1 means that not provided
    public String referSentence; // in case we use coref

    public EntityTag(int beginIndex, int endIndex, String id, double confidence, String referSent) {
        super(beginIndex, endIndex);
        PLACEHOLDER = Placeholder.ENTITY;
        this.id = id;
        this.confidence = confidence;
        this.referSentence = referSent;
    }

    public EntityTag(int beginIndex, int endIndex, String id, double confidence) {
        this(beginIndex, endIndex, id, confidence, null);
    }

    public EntityTag(int beginIndex, int endIndex, String id) {
        this(beginIndex, endIndex, id, -1);
    }

}
