package pipeline.deep;

import java.util.ArrayList;
import java.util.List;

public interface ScoringClientInterface {
    ArrayList<Double> getScores(List<String> entitiesDesc, String quantityDesc);

    double getScore(String typeDesc, String quantityDesc);
}
