package model.text.tag;

public class TimeTag extends Tag {
    // start (inclusive), end (inclusive) time values (unix time).
    // The granularity is day.
    public long rangeFrom, rangeTo;

    public TimeTag(int beginIndex, int endIndex, long rangeFrom, long rangeTo) {
        super(beginIndex, endIndex);
        PLACEHOLDER = Placeholder.TIME;

        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
    }
}
