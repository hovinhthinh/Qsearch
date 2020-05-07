package pipeline.text;

import model.text.Sentence;

public class SentenceLengthFiltering extends FilteringNode {
    private int minimumNumberOfSentenceTokens, maximumNumberOfSentenceTokens;

    public SentenceLengthFiltering(int minimumNumberOfSentenceTokens, int maximumNumberOfSentenceTokens) {
        this.minimumNumberOfSentenceTokens = minimumNumberOfSentenceTokens;
        this.maximumNumberOfSentenceTokens = maximumNumberOfSentenceTokens;
    }

    @Override
    public boolean isFiltered(Sentence sent) {
        return sent.tokens.size() < minimumNumberOfSentenceTokens || sent.tokens.size() > maximumNumberOfSentenceTokens;
    }
}
