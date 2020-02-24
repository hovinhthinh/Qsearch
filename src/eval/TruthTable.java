package eval;


import com.google.gson.Gson;
import eval.baseline.WordSet;
import misc.WikipediaEntity;
import model.quantity.Quantity;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import model.text.Paragraph;
import model.text.Sentence;
import model.text.tag.QuantityTag;
import nlp.NLP;
import pipeline.text.QuantityTaggingNode;
import pipeline.text.TaggingPipeline;
import util.Constants;
import util.Pair;
import util.Vectors;

import java.util.Arrays;

// For ground truth of column linkings.
public class TruthTable extends Table {
    private static final transient Gson GSON = new Gson();

    @Deprecated
    public int keyColumnGroundTruth = -1;

    public int[] quantityToEntityColumnGroundTruth; // -1 means there is no connection.

    public String[][] bodyEntityTarget; // e.g. <Cristiano_Ronaldo> (with < and > )

    // temporary for evaluation
    public int[][] yusraBodyEntityTarget;

    public static TruthTable fromTable(Table t) {
        TruthTable truth = GSON.fromJson(GSON.toJson(t), TruthTable.class);

        truth.quantityToEntityColumnGroundTruth = new int[truth.nColumn];
        Arrays.fill(truth.quantityToEntityColumnGroundTruth, -1);

        truth.bodyEntityTarget = new String[truth.nDataRow][truth.nColumn];
        truth.yusraBodyEntityTarget = new int[truth.nDataRow][truth.nColumn];

        return truth;
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromPrior() {
        Pair<Integer, Integer> result = getEntityDisambiguationMicroPrecisionInfoFromPrior();
        return result == null ? -1 : ((double) result.first) / result.second;
    }

    // return null means there is no mention.
    public Pair<Integer, Integer> getEntityDisambiguationMicroPrecisionInfoFromPrior() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < bodyEntityTarget.length; ++i) {
            for (int j = 0; j < bodyEntityTarget[i].length; ++j) {
                EntityLink el = data[i][j].getRepresentativeEntityLink();
                if (el == null) {
                    continue;
                }
                ++total;

                String target = el.candidates.get(0).first;
                if (bodyEntityTarget[i][j].equals(target)) {
                    ++nTrue;
                }
                el.target = "YAGO:" + target.substring(1, target.length() - 1);
            }
        }
        if (total == 0) {
            return null;
        }
        return new Pair<>(nTrue, total);
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromTarget() {
        Pair<Integer, Integer> result = getEntityDisambiguationMicroPrecisionInfoFromTarget();
        return result == null ? -1 : ((double) result.first) / result.second;
    }

    // return null means there is no mention.
    public Pair<Integer, Integer> getEntityDisambiguationMicroPrecisionInfoFromTarget() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < bodyEntityTarget.length; ++i) {
            for (int j = 0; j < bodyEntityTarget[i].length; ++j) {
                EntityLink el = data[i][j].getRepresentativeEntityLink();
                if (el == null) {
                    continue;
                }
                ++total;

                String predictedTarget = data[i][j].getRepresentativeEntityLink().target;
                predictedTarget = "<" + predictedTarget.substring(predictedTarget.lastIndexOf(":") + 1) + ">";
                if (predictedTarget.equals(bodyEntityTarget[i][j])) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return null;
        }
        return new Pair<>(nTrue, total);
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromTarget() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == quantityToEntityColumn[i]) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromFirstColumn() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        int eColumn = (hasIndexColumn || isNumericColumn[0]) ? 1 : 0;
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == eColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromYusra() {
        Pair<Integer, Integer> result = getEntityDisambiguationMicroPrecisionInfoFromYusra();
        return result == null ? -1 : ((double) result.first) / result.second;
    }

    // return null means there is no mention.
    public Pair<Integer, Integer> getEntityDisambiguationMicroPrecisionInfoFromYusra() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < bodyEntityTarget.length; ++i) {
            for (int j = 0; j < bodyEntityTarget[i].length; ++j) {
                EntityLink el = data[i][j].getRepresentativeEntityLink();
                if (el == null) {
                    continue;
                }
                ++total;

                if (yusraBodyEntityTarget[i][j] == 1) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return null;
        }
        return new Pair<>(nTrue, total);
    }

    @Deprecated
    public Paragraph surroundingTextAsParagraph;

    @Deprecated
    static TaggingPipeline quantityTaggingPipelineForText = new TaggingPipeline(new QuantityTaggingNode());

    @Deprecated
    public void linkQuantitiesInTableAndText() {
        surroundingTextAsParagraph = Paragraph.fromText(surroundingText, null);
        quantityTaggingPipelineForText.tag(surroundingTextAsParagraph);
    }

    @Deprecated
    public double getRateOfTableQuantitiesFoundInText() {
        int total = 0;
        int nFound = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nDataRow; ++i) {
            for (int j = 0; j < nColumn; ++j) {
                if (hasIndexColumn && j == 0) {
                    continue;
                }
                QuantityLink ql = data[i][j].getRepresentativeQuantityLink();
                if (ql == null) {
                    continue;
                }
                ++total;
                boolean quantityInText = false;
                loop:
                for (Sentence sent : surroundingTextAsParagraph.sentences) {
                    for (QuantityTag qt : sent.quantityTags) {
                        if (qt.quantity.compareTo(ql.quantity) == 0) {
                            quantityInText = true;
                            break loop;
                        }
                    }
                }
                if (quantityInText) {
                    ++nFound;
                    data[i][j].text = "^" + data[i][j].text;
                }
            }
        }
        return total == 0 ? -1 : ((double) nFound) / total;
    }

    // returns only 1 sentence
    @Deprecated
    public Sentence getSentenceContainingQuantity(Quantity q) {
        for (Sentence sent : surroundingTextAsParagraph.sentences) {
            for (QuantityTag qt : sent.quantityTags) {
                if (qt.quantity.compareTo(q) == 0) {
                    return sent;
                }
            }
        }
        return null;
    }

    @Deprecated
    // for quantity column: column header
    // for entity column: column header
    public double getAlignmentPrecisionFromHeaderEmbedding() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;

                // Compute embedding from quantity header
                WordSet set = new WordSet();
                set.addAll(getQuantityDescriptionFromCombinedHeader(i));
                set.stemming();
                double[] qEmb = set.getTfIdfWeightedEmbedding();

                int linkedColumn = -1;
                double linkedScore = Constants.MAX_DOUBLE;
                for (int j = 0; j < nColumn; ++j) {
                    // loop all other columns other than pivot quantity column.
                    if (j == i) {
                        continue;
                    }
                    // Compute score from entity header
                    set = new WordSet();
                    set.addAll(getOriginalCombinedHeader(j));
                    set.stemming();
                    double[] eEmb = set.getTfIdfWeightedEmbedding();

                    double score = Vectors.cosineD(qEmb, eEmb);
                    if (score < linkedScore) {
                        linkedScore = score;
                        linkedColumn = j;
                    }
                }
                if (quantityToEntityColumnGroundTruth[i] == linkedColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    @Deprecated
    // for quantity column: column header + cells content + all sentences from table surrounding text containing quantities
    // for entity column: column header + cells content + wikipage from entities in cells (if available)
    public double getAlignmentPrecisionFromColumnEmbedding() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;

                // Compute embedding from quantity column
                WordSet set = new WordSet();
                set.addAll(getQuantityDescriptionFromCombinedHeader(i));
                for (int r = 0; r < nDataRow; ++r) {
                    set.addAll(data[r][i].text);
                    QuantityLink ql = data[r][i].getRepresentativeQuantityLink();
                    if (ql == null) {
                        continue;
                    }
                    Sentence sent = getSentenceContainingQuantity(ql.quantity);
                    if (sent == null) {
                        continue;
                    }
                    set.addAll(sent.toString());
                }
                set.stemming();
                double[] qEmb = set.getTfIdfWeightedEmbedding();

                int linkedColumn = -1;
                double linkedScore = Constants.MAX_DOUBLE;
                for (int j = 0; j < nColumn; ++j) {
                    // loop all other columns other than pivot quantity column.
                    if (j == i) {
                        continue;
                    }
                    // Compute score from entity column
                    set = new WordSet();
                    set.addAll(getOriginalCombinedHeader(j));
                    for (int r = 0; r < nDataRow; ++r) {
                        set.addAll(data[r][j].text);
                        EntityLink el = data[r][j].getRepresentativeEntityLink();
                        if (el == null) {
                            continue;
                        }
                        String content = WikipediaEntity.getContentOfEntity("<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">");
                        if (content != null) {
                            set.addAll(NLP.tokenize(content));
                        }
                    }
                    set.stemming();

                    double[] eEmb = set.getTfIdfWeightedEmbedding();
                    double score = Vectors.cosineD(qEmb, eEmb);
                    if (score < linkedScore) {
                        linkedScore = score;
                        linkedColumn = j;
                    }
                }
                if (quantityToEntityColumnGroundTruth[i] == linkedColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    @Deprecated
    // for quantity column: column header + cells content + all sentences from table surrounding text containing quantities
    // for entity column: column header + cells content + wikipage from entities in cells (if available)
    public double getAlignmentPrecisionFromColumnJaccardIndex() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;

                // Compute embedding from quantity column
                WordSet qset = new WordSet();
                qset.addAll(getQuantityDescriptionFromCombinedHeader(i));
                for (int r = 0; r < nDataRow; ++r) {
                    qset.addAll(data[r][i].text);
                    QuantityLink ql = data[r][i].getRepresentativeQuantityLink();
                    if (ql == null) {
                        continue;
                    }
                    Sentence sent = getSentenceContainingQuantity(ql.quantity);
                    if (sent == null) {
                        continue;
                    }
                    qset.addAll(sent.toString());
                }
                qset.stemming();

                int linkedColumn = -1;
                double linkedScore = Constants.MAX_DOUBLE;
                for (int j = 0; j < nColumn; ++j) {
                    // loop all other columns other than pivot quantity column.
                    if (j == i) {
                        continue;
                    }
                    // Compute score from entity column
                    WordSet set = new WordSet();
                    set.addAll(getOriginalCombinedHeader(j));
                    for (int r = 0; r < nDataRow; ++r) {
                        set.addAll(data[r][j].text);
                        EntityLink el = data[r][j].getRepresentativeEntityLink();
                        if (el == null) {
                            continue;
                        }
                        String content = WikipediaEntity.getContentOfEntity("<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">");
                        if (content != null) {
                            set.addAll(NLP.tokenize(content));
                        }
                    }
                    set.stemming();

                    double score = 1 - qset.getJaccardSim(set);
                    if (score < linkedScore) {
                        linkedScore = score;
                        linkedColumn = j;
                    }
                }
                if (quantityToEntityColumnGroundTruth[i] == linkedColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }
}
