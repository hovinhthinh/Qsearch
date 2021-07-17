package nlp;

import config.Configuration;
import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.HeidelTimeStandalone;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.POSTagger;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import edu.iitd.cse.open_nre.onre.constants.OnreFilePaths;
import edu.iitd.cse.openieListExtractor.constants.ListExtractorFilePaths;
import edu.illinois.cs.cogcomp.annotation.AnnotatorServiceConfigurator;
import edu.illinois.cs.cogcomp.quant.driver.Quantifier;
import edu.knowitall.openie.OpenIE;
import edu.knowitall.tool.parse.ClearParser;
import edu.knowitall.tool.postag.ClearPostagger;
import edu.knowitall.tool.srl.ClearSrl;
import edu.knowitall.tool.tokenize.ClearTokenizer;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import iitb.shared.XMLConfigs;
import parser.CFGParser4Header;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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

    private static IDictionary WN_DICT;

    private static CFGParser4Header TABLE_HEADER_PARSER = null;

    public static IDictionary getWordNetDict() {
        if (WN_DICT == null) {
            try {
                WN_DICT = new Dictionary(new File(Configuration.get("wordnet.folder_path"), "dict"));
                WN_DICT.open();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return WN_DICT;
    }

    public static CFGParser4Header getTableHeaderParser() {
        if (TABLE_HEADER_PARSER == null) {
            try {
                TABLE_HEADER_PARSER = new CFGParser4Header(XMLConfigs.load(new FileReader("lib/unit-tagger/unit-tagger-configs.xml")));
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return TABLE_HEADER_PARSER;
    }

    public static Quantifier getIllinoisQuantifier() {
        if (ILLINOIS_QUANTIFIER == null) {
            ILLINOIS_QUANTIFIER = new Quantifier();
            ILLINOIS_QUANTIFIER.initialize(null);
            AnnotatorServiceConfigurator.DISABLE_CACHE.value = "true";
        }
        return ILLINOIS_QUANTIFIER;
    }

    public static OpenIE getOpenIe() {
        // Only for OpenIE5
        ListExtractorFilePaths.LMFilePath = Configuration.get("openie5.language_model_file");
        OnreFilePaths.folderpath_wordnet = Configuration.get("wordnet.folder_path");

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