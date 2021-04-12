package model.text.tag;

public class TimeTag extends Tag {
    public boolean isRelative; // need a reference time to fully resolve the time.
    // start (inclusive), end (inclusive) time values (unix time).
    // The granularity is day.
    public long rangeFrom, rangeTo;

    public TimeTag(int beginIndex, int endIndex, long rangeFrom, long rangeTo) {
        super(beginIndex, endIndex);
        PLACEHOLDER = Placeholder.TIME;

        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.isRelative = false;
    }

    public TimeTag(int beginIndex, int endIndex, boolean isRelative) {
        super(beginIndex, endIndex);
        PLACEHOLDER = Placeholder.TIME;

        this.rangeFrom = -1;
        this.rangeTo = -1;
        this.isRelative = isRelative;
    }
}
