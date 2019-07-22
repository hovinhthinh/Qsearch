package nlp;

import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.HeidelTimeStandalone;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.POSTagger;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import edu.illinois.cs.cogcomp.quant.driver.Quantifier;
import edu.knowitall.openie.OpenIE;
import edu.knowitall.tool.parse.ClearParser;
import edu.knowitall.tool.postag.ClearPostagger;
import edu.knowitall.tool.srl.ClearSrl;
import edu.knowitall.tool.tokenize.ClearTokenizer;

public class Static {
    private static Quantifier ILLINOIS_QUANTIFIER = null;
    private static OpenIE OPEN_IE = null;
    /**
     * public static StanfordCoreNLP PIPELINE;
     * <p>
     * public static void initCoreNLP() {
     * if (PIPELINE == null) {
     * Properties props = new Properties();
     * props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref");
     * PIPELINE = new StanfordCoreNLP(props);
     * }
     * }
     */

    // Initialize HeidelTime for English narrative
    // For faster, but worse results; no TreeTagger required: "POSTagger.NO"
    private static HeidelTimeStandalone HEIDEL_TIME_NARRATIVES = null;
    private static HeidelTimeStandalone HEIDEL_TIME_NEWS = null;

    public static Quantifier getIllinoisQuantifier() {
        if (ILLINOIS_QUANTIFIER == null) {
            ILLINOIS_QUANTIFIER = new Quantifier();
        }
        return ILLINOIS_QUANTIFIER;
    }

    public static OpenIE getOpenIe() {
        if (OPEN_IE == null) {
            OPEN_IE = new edu.knowitall.openie.OpenIE(new ClearParser(new ClearPostagger(new ClearTokenizer())),
                    new ClearSrl(), false, false);
        }
        return OPEN_IE;
    }

    public static HeidelTimeStandalone getHeidelTime(boolean news) {
        if (!news) {
            // narrative
            if (HEIDEL_TIME_NARRATIVES == null) {
                HEIDEL_TIME_NARRATIVES = new HeidelTimeStandalone(Language.ENGLISH, DocumentType.NARRATIVES,
                        OutputType.TIMEML, "lib/heideltime/config.props", POSTagger.TREETAGGER);
            }
            return HEIDEL_TIME_NARRATIVES;
        } else {
            // news
            if (HEIDEL_TIME_NEWS == null) {
                HEIDEL_TIME_NEWS = new HeidelTimeStandalone(Language.ENGLISH, DocumentType.NEWS,
                        OutputType.TIMEML, "lib/heideltime/config.props", POSTagger.TREETAGGER);
            }
            return HEIDEL_TIME_NEWS;
        }
    }
}