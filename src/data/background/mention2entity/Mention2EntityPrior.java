package data.background.mention2entity;

import util.FileUtils;
import util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

// Sensitive
public class Mention2EntityPrior {
    private static final String PRIOR_PATH = "./data/m2ePrior_wikipages+wikitables+wikilinks.case-sensitive.gz";
    private HashMap<String, List<Pair<String, Integer>>> mention2Entity;
    private static final Logger LOGGER = Logger.getLogger(Mention2EntityPrior.class.getName());

    public Mention2EntityPrior(int minEntityFreq) {
        mention2Entity = new HashMap<>();
        int afterPruned = 0;
        int total = 0;
        for (String line : FileUtils.getLineStream(PRIOR_PATH, "UTF-8")) {
            Mention2EntityInfoLine infoLine = Mention2EntityInfoLine.fromLine(line);
            total += infoLine.entityFreq.size();
            for (int i = infoLine.entityFreq.size() - 1; i >= 0; --i) {
                if (infoLine.entityFreq.get(i).second < minEntityFreq) {
                    infoLine.entityFreq.remove(i);
                }
            }
            afterPruned += infoLine.entityFreq.size();
            if (infoLine.entityFreq.size() > 0) {
                infoLine.entityFreq.trimToSize();
                mention2Entity.put(infoLine.mention, infoLine.entityFreq);
            }
        }
        LOGGER.info(String.format("Loaded: %d/%d", afterPruned, total));
    }

    public List<Pair<String, Integer>> getCanditateEntitiesForMention(String mention) {
        return mention2Entity.get(mention);
    }

    public static void main(String[] args) {
        Mention2EntityPrior prior = new Mention2EntityPrior(2);
        System.out.println(prior.getCanditateEntitiesForMention("Cristiano Ronaldo"));
    }
}
