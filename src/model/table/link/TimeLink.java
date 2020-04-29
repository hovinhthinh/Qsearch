package model.table.link;

public class TimeLink {
    public String text;
    public String temporalStr;

    public TimeLink(String text, String temporalStr) {
        this.text = text;
        this.temporalStr = temporalStr;
    }

    @Override
    public String toString() {
        return String.format("(%s : %s)", text, temporalStr);
    }
}
