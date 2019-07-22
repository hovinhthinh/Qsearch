package model.table;

public class Cell {
    public String text; // surface text
    public Link[] links;

    private String disambiguatedText = null;

    public String getDisambiguatedText() {
        if (disambiguatedText != null) {
            return disambiguatedText;
        }
        disambiguatedText = this.text;
        for (Link l : links) {
            disambiguatedText = disambiguatedText.replace(l.text, "<" + l.target.substring(l.target.lastIndexOf(":") + 1) + ">");
        }
        return disambiguatedText;
    }
}
