package model.table;

public class Cell {
    public String text; // surface text
    public Link[] links;

    public String getDisambiguatedText() {
        String text = this.text;
        for (Link l : links) {
            text = text.replace(l.text, "<" + l.target.substring(l.target.lastIndexOf(":") + 1) + ">");
        }
        return text;
    }
}
