package model.text.tag;

// Tag in sentence.
public abstract class Tag {
    public String PLACEHOLDER = Placeholder.ABSTRACT;

    public int beginIndex, endIndex; // begin (inclusive), end (exclusive)

    public Tag(int beginIndex, int endIndex) {
        this.beginIndex = beginIndex;
        this.endIndex = endIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Tag)) {
            return false;
        }
        Tag t = (Tag) obj;
        return this.beginIndex == t.beginIndex && this.endIndex == t.endIndex;
    }
}
