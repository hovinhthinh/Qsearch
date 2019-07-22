package model.text;

public class Token {
    public int indexInSentence = -1; // 0-based;
    public String str;
    public String POS;

    @Override
    public String toString() {
        return str;
    }
}
