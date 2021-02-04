package eval.table.exp_2.recall;

import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.junit.Assert;
import util.FileUtils;
import util.Pair;

import java.util.*;

public class QueryTemplate {
    public static final transient Random RAND = new Random();

    public String full;
    public String quantitySpan;
    public String quantityUnit;
    public String bound;

    public String sourceURL;

    public ArrayList<GroundFact> groundtruth;

    public static QueryTemplate read(FileUtils.LineStream stream) {
        String line;
        while ((line = stream.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            Assert.assertTrue(line.equals(RecallQueryTemplate.BEGIN_QUERY));

            QueryTemplate qt = new QueryTemplate();

            String[] arr = stream.readLine().split("\t");
            qt.full = arr[0];
            qt.quantitySpan = arr[1].toLowerCase();
            qt.quantityUnit = arr[2];
            qt.bound = arr[3];
            Assert.assertTrue(qt.bound.equals("LB") || qt.bound.equals("UB") || qt.bound.equals("LUB"));
            qt.sourceURL = stream.readLine();

            // ground truth
            qt.groundtruth = new ArrayList<>();
            while (!(arr = stream.readLine().split("\t"))[0].equals(RecallQueryTemplate.END_QUERY)) {
                if (arr[0].equals(GroundTable.TABLE_START)) {
                    continue;
                }
                GroundFact f = new GroundFact(arr[0], arr[1], arr[2], arr[3]);
                f.q = Quantity.fromQuantityString(arr[3]);
                Assert.assertTrue(f.q != null);
                qt.groundtruth.add(f);
            }
            Assert.assertTrue(qt.groundtruth.size() > 0);
            return qt;
        }
        return null;
    }

    public static String chooseRandom(String... arr) {
        return arr[RAND.nextInt(arr.length)];
    }

    public Pair<Double, String> textualizeQuantity(double queryQ, boolean percent) {
        String queryQStr = String.format("%.1f", queryQ);
        if (queryQStr.endsWith(".0")) {
            queryQStr = queryQStr.substring(0, queryQStr.length() - 2);
        }
        queryQ = Double.parseDouble(queryQStr);
        if (Math.abs(queryQ) >= 1e9) {
            queryQStr = String.format("%.1f", queryQ / 1e9);
            queryQ = Double.parseDouble(queryQStr) * 1e9;
            if (queryQStr.endsWith(".0") || queryQ >= 10 * 1e9) {
                queryQStr = queryQStr.substring(0, queryQStr.length() - 2);
                queryQ = Double.parseDouble(queryQStr) * 1e9;
            }
            queryQStr += chooseRandom("B", " billion");
        } else if (Math.abs(queryQ) >= 1e6) {
            queryQStr = String.format("%.1f", queryQ / 1e6);
            queryQ = Double.parseDouble(queryQStr) * 1e6;
            if (queryQStr.endsWith(".0") || queryQ >= 10 * 1e6) {
                queryQStr = queryQStr.substring(0, queryQStr.length() - 2);
                queryQ = Double.parseDouble(queryQStr) * 1e6;
            }
            queryQStr += chooseRandom("M", " million", " mio");
        } else if (Math.abs(queryQ) >= 1e5) {
            queryQStr = String.format("%.0f", queryQ / 1e3);
            queryQ = Double.parseDouble(queryQStr) * 1e3;
            queryQStr += chooseRandom("K", "k", " thousand");
        } else if (Math.abs(queryQ) >= 1e4) {
            queryQStr = String.format("%.0f", queryQ / 1e3);
            queryQ = Double.parseDouble(queryQStr) * 1e3;
            queryQStr += chooseRandom(",000", " thousand", "000");
        } else if (percent) {
            queryQStr = String.format("%.1f", queryQ * 100);
            queryQ = Double.parseDouble(queryQStr) / 100;
            if (queryQStr.endsWith(".0")) {
                queryQStr = queryQStr.substring(0, queryQStr.length() - 2);
                queryQ = Double.parseDouble(queryQStr) / 100;
            }
        }

        return new Pair<>(queryQ, queryQStr);
    }

    public ArrayList<RecallQuery> generate() {

        ArrayList<RecallQuery> queries = new ArrayList<>();
        // LB
        if (bound.equals("LUB") || bound.equals("LB")) {
            Collections.sort(groundtruth, (a, b) -> Double.compare(b.q.value, a.q.value));
            // uniq
            ArrayList<GroundFact> facts = new ArrayList<>();
            HashSet<String> entities = new HashSet<>();
            for (GroundFact f : groundtruth) {
                if (!entities.contains(f.entity)) {
                    facts.add(f);
                    entities.add(f.entity);
                }
            }
            List<Integer> tops = new LinkedList<>() {{
                add(Math.min(facts.size(), 10));
            }};
            if (facts.size() >= 20) {
                tops.add(20);
            }
            if (facts.size() >= 30 && bound.equals("LB")) {
                tops.add(30);
            }

            int nResultsLast = -100;
            for (int top : tops) {
                Quantity thresholdQ = facts.get(top - 1).q;
                double queryQ = thresholdQ.value * thresholdQ.getScale() / new Quantity(0, quantityUnit, "=").getScale();
                Pair<Double, String> textualized = textualizeQuantity(queryQ,
                        QuantityDomain.getSearchDomainOfUnit(quantityUnit).equals(QuantityDomain.Domain.PERCENTAGE));
                queryQ = textualized.first;
                String queryQStr = textualized.second;
                double factQThreshold = queryQ * new Quantity(0, quantityUnit, "=").getScale() / thresholdQ.getScale();
                RecallQuery q = new RecallQuery();
                q.sourceURL = sourceURL;
                q.groundFacts = new ArrayList<>();
                double lastGoodVal = 0;
                for (GroundFact f : facts) {
                    if (f.q.value >= factQThreshold) {
                        q.groundFacts.add(f);
                        lastGoodVal = f.q.value;
                    }
                }
                Assert.assertTrue(q.groundFacts.size() > 0);
                if (q.groundFacts.size() <= nResultsLast + 7 || q.groundFacts.size() > 30) {
                    continue;
                }

                String comparator;
                if (lastGoodVal > factQThreshold + 1e-6 || queryQ >= 1e4) {
                    comparator = chooseRandom("more than", "above", "higher than", "above", "at least", "over");
                } else {
                    comparator = chooseRandom("no less than", "at least");
                }
                q.full = NLP.stripSentence(full.replace(quantitySpan, " " + comparator + " " + queryQStr + " " + quantityUnit + " "));
                queries.add(q);
                nResultsLast = q.groundFacts.size();
            }
        }
        // UB
        if (bound.equals("LUB") || bound.equals("UB")) {
            Collections.sort(groundtruth, Comparator.comparingDouble(o -> o.q.value));
            // uniq
            ArrayList<GroundFact> facts = new ArrayList<>();
            HashSet<String> entities = new HashSet<>();
            for (GroundFact f : groundtruth) {
                if (!entities.contains(f.entity)) {
                    facts.add(f);
                    entities.add(f.entity);
                }
            }
            List<Integer> tops = new LinkedList<>() {{
                add(Math.min(facts.size(), 10));
            }};
            if (facts.size() >= 20) {
                tops.add(20);
            }
            if (facts.size() >= 30 && bound.equals("UB")) {
                tops.add(30);
            }

            int nResultsLast = -100;
            for (int top : tops) {
                Quantity thresholdQ = facts.get(top - 1).q;
                double queryQ = thresholdQ.value * thresholdQ.getScale() / new Quantity(0, quantityUnit, "=").getScale();
                Pair<Double, String> textualized = textualizeQuantity(queryQ,
                        QuantityDomain.getSearchDomainOfUnit(quantityUnit).equals(QuantityDomain.Domain.PERCENTAGE));
                queryQ = textualized.first;
                String queryQStr = textualized.second;
                double factQThreshold = queryQ * new Quantity(0, quantityUnit, "=").getScale() / thresholdQ.getScale();
                RecallQuery q = new RecallQuery();
                q.sourceURL = sourceURL;
                q.groundFacts = new ArrayList<>();
                double lastGoodVal = 0;
                for (GroundFact f : facts) {
                    if (f.q.value <= factQThreshold) {
                        q.groundFacts.add(f);
                        lastGoodVal = f.q.value;
                    }
                }
                Assert.assertTrue(q.groundFacts.size() > 0);
                if (q.groundFacts.size() <= nResultsLast + 7 || q.groundFacts.size() > 30) {
                    continue;
                }

                String comparator;
                if (lastGoodVal < factQThreshold - 1e-6 || queryQ >= 1e4) {
                    comparator = chooseRandom("less than", "below", "lower than", "at most", "up to", "under");
                } else {
                    comparator = chooseRandom("no more than", "at most");
                }
                q.full = NLP.stripSentence(full.replace(quantitySpan, " " + comparator + " " + queryQStr + " " + quantityUnit + " "));
                queries.add(q);
                nResultsLast = q.groundFacts.size();
            }
        }

        return queries;
    }
}
