package data.background.mention2entity;

import nlp.NLP;
import util.FileUtils;
import util.Triple;
import yago.TaxonomyGraph;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

// Sensitive
public class Mention2EntityPrior {
    private static final String PRIOR_PATH = "./resources/m2ePrior_wikipages+wikitables+wikilinks.case-sensitive_tokenized.gz";
    private HashMap<String, List<Triple<String, Integer, Double>>> mention2Entity;
    private static final Logger LOGGER = Logger.getLogger(Mention2EntityPrior.class.getName());

    // if nTopKeptCandidateEntities == -1, keep all entities for each mention.
    public Mention2EntityPrior(int minMention2EntityFreq, int nTopKeptCandidateEntities) {
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
                if (infoLine.entityFreq.get(i).second < minMention2EntityFreq
                        || TaxonomyGraph.getDefaultGraphInstance().getEntityId(infoLine.entityFreq.get(i).first) == -1) {
                    infoLine.entityFreq.remove(i);
                }
            }
            if (infoLine.entityFreq.size() > 0) {
                if (nTopKeptCandidateEntities > 0 && infoLine.entityFreq.size() > nTopKeptCandidateEntities) {
                    infoLine.entityFreq.subList(nTopKeptCandidateEntities, infoLine.entityFreq.size()).clear();
                    infoLine.entityFreq.trimToSize();
                }
                afterPruned += infoLine.entityFreq.size();
                infoLine.entityFreq.trimToSize();
                mention2Entity.put(infoLine.mention, infoLine.entityFreq);
            }
        }
        LOGGER.info(String.format("MinFrequency: %d\tLoaded: %d/%d", minMention2EntityFreq, afterPruned, total));
    }

    public List<Triple<String, Integer, Double>> getCanditateEntitiesForMention(String mention, boolean doTokenizing) {
        if (doTokenizing) {
            mention = String.join(" ", NLP.tokenize(mention));
        }
        return mention2Entity.get(mention);
    }

    public List<Triple<String, Integer, Double>> getCanditateEntitiesForMention(String mention) {
        return getCanditateEntitiesForMention(mention, false);
    }

    public static void main(String[] args) {
        Mention2EntityPrior prior = new Mention2EntityPrior(2, 3);
        System.out.println(prior.getCanditateEntitiesForMention("Ronaldo"));
    }
}
