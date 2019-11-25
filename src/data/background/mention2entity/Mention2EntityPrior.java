package data.background.mention2entity;

import nlp.NLP;
import nlp.YagoType;
import util.FileUtils;
import util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

// Sensitive
public class Mention2EntityPrior {
    private static final String PRIOR_PATH = "./resources/m2ePrior_wikipages+wikitables+wikilinks.case-sensitive_tokenized.gz";
    private HashMap<String, List<Pair<String, Integer>>> mention2Entity;
    private static final Logger LOGGER = Logger.getLogger(Mention2EntityPrior.class.getName());

    public Mention2EntityPrior(int minEntityFreq) {
        mention2Entity = new HashMap<>();
        int afterPruned = 0;
        int total = 0;
        for (String line : FileUtils.getLineStream(PRIOR_PATH, "UTF-8")) {
            Mention2EntityInfoLine infoLine = Mention2EntityInfoLine.fromLine(line);
            total += infoLine.entityFreq.size();
            if (infoLine.mention.length() <= 1) {
                // ignore short mentions to reduce noise.
                continue;
            }
            for (int i = infoLine.entityFreq.size() - 1; i >= 0; --i) {
                // if the frequency is too low; or the entity does not exist in YAGO type system
                if (infoLine.entityFreq.get(i).second < minEntityFreq || !YagoType.entityExists(infoLine.entityFreq.get(i).first)) {
                    infoLine.entityFreq.remove(i);
                }
            }
            if (infoLine.entityFreq.size() > 0) {
                afterPruned += infoLine.entityFreq.size();
                infoLine.entityFreq.trimToSize();
                mention2Entity.put(infoLine.mention, infoLine.entityFreq);
            }
        }
        LOGGER.info(String.format("MinFrequency: %d\tLoaded: %d/%d", minEntityFreq, afterPruned, total));
    }

    public List<Pair<String, Integer>> getCanditateEntitiesForMention(String mention, boolean doTokenizing) {
        if (doTokenizing) {
            mention = String.join(" ", NLP.tokenize(mention));
        }
        return mention2Entity.get(mention);
    }

    public List<Pair<String, Integer>> getCanditateEntitiesForMention(String mention) {
        return getCanditateEntitiesForMention(mention, false);
    }

    public static void main(String[] args) {
        Mention2EntityPrior prior = new Mention2EntityPrior(2);
        System.out.println(prior.getCanditateEntitiesForMention("Cristiano Ronaldo"));
    }
}
