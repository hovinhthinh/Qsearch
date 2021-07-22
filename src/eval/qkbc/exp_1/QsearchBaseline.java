package eval.qkbc.exp_1;

import eval.qkbc.QKBCResult;
import eval.qkbc.WikidataGroundTruthExtractor;
import model.quantity.kg.KgUnit;
import qkbc.text.QKBCRunner;
import qkbc.text.RelationInstance;
import server.text.ResultInstance;
import server.text.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import util.Crawler;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class QsearchBaseline {
    public static final String QSEARCH_END_POINT = "http://qsearch.mpi-inf.mpg.de/kbc_text";

    private static ArrayList<RelationInstance> query(String yagoType, String context, KgUnit quantitySiUnit, int ntop) {
        try {
            URIBuilder b = new URIBuilder(QSEARCH_END_POINT);
            b.addParameter("type", yagoType);
            b.addParameter("context", context);
            b.addParameter("quantitySiUnit", quantitySiUnit.entity);
            b.addParameter("ntop", "" + ntop);
            b.addParameter("n-evidence", "1");
            SearchResult sr = Gson.fromJson(Crawler.getContentFromUrl(b.toString()), SearchResult.class);

            ArrayList<RelationInstance> result = new ArrayList<>();

            for (ResultInstance ri : sr.topResults) {
                loop:
                for (ResultInstance.SubInstance si : ri.subInstances) {
                    RelationInstance r = new RelationInstance(ri.entity, si.quantity, si.quantityStandardValue, 1 / si.score, si.kbcId);
                    r.positiveIterIndices = new ArrayList<>() {{
                        add(1);
                    }};
                    r.noiseIterIndices = new ArrayList<>();
                    result.add(r);
                    break;
                }
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void harvest(String predicateName, String type, String ctx, KgUnit quantitySiUnit,
                               int ntop, String groundTruthFile,
                               String outputFile) {
        Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> grouthtruth = groundTruthFile == null ? null :
                WikidataGroundTruthExtractor.loadPredicateGroundTruthFromFile(groundTruthFile).stream()
                        .collect(Collectors.toMap(f -> f.e, f -> f));

        QKBCResult r = new QKBCResult();
        r.predicate = predicateName;
        r.refinementByTime = false;
        r.groundTruthSize = grouthtruth == null ? null : grouthtruth.size();
        r.nIterations = 1;
        r.ctxList = new ArrayList<>(Arrays.asList(ctx));
        r.instances = query(type, ctx, quantitySiUnit, ntop);

        // mark effective, groundtruth
        QKBCRunner.markEffectiveAndGroundTruthFacts(r, grouthtruth, false);

        // mark sampled instances
        QKBCRunner.markSampledInstances(r);

        PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
        out.println(Gson.toJson(r));
        out.close();
    }

    public static void main(String[] args) {
        harvest("cityAltitude", "<wordnet_city_108524735>", "altitude", KgUnit.getKgUnitFromEntityName("<Metre>"),
                1396, null,
                "./eval/qkbc/exp_1/qsearch_queries/qs_output_fact/city_altitude_qs.json");

        harvest("companyRevenue", "<wordnet_company_108058098>", "reported revenue", KgUnit.getKgUnitFromEntityName("<United_States_dollar>"),
                951, null,
                "./eval/qkbc/exp_1/qsearch_queries/qs_output_fact/company_revenue_qs.json");

        harvest("buildingHeight", "<wordnet_building_102913152>", "height", KgUnit.getKgUnitFromEntityName("<Metre>"),
                236, "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height",
                "./eval/qkbc/exp_1/qsearch_queries/qs_output_fact/building_height_qs.json");

        harvest("mountainElevation", "<http://schema.org/Mountain>", "high", KgUnit.getKgUnitFromEntityName("<Metre>"),
                518, "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation",
                "./eval/qkbc/exp_1/qsearch_queries/qs_output_fact/mountain_elevation_qs.json");

        harvest("stadiumCapacity", "<wordnet_stadium_104295881>", "capacity", KgUnit.DIMENSIONLESS,
                1045,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity",
                "./eval/qkbc/exp_1/qsearch_queries/qs_output_fact/stadium_capacity_qs.json");

        harvest("riverLength", "<wordnet_river_109411430>", "long", KgUnit.getKgUnitFromEntityName("<Metre>"),
                589, "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length",
                "./eval/qkbc/exp_1/qsearch_queries/qs_output_fact/river_length_qs.json");
    }
}
