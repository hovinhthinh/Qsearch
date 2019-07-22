package model.text.tag;

public class EntityTag extends Tag {
    public String id; // id in KB
    public double confidence; // -1 means that not provided

    public EntityTag(int beginIndex, int endIndex, String id, double confidence) {
        super(beginIndex, endIndex);
        PLACEHOLDER = Placeholder.ENTITY;
        this.id = id;
        this.confidence = confidence;
    }

    public EntityTag(int beginIndex, int endIndex, String id) {
        this(beginIndex, endIndex, id, -1);
    }

}
