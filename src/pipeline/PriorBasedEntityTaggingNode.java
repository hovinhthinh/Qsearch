package pipeline;

import data.background.mention2entity.Mention2EntityPrior;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PriorBasedEntityTaggingNode implements TaggingNode {
    public static final double REPRESENTATIVE_THRESHOLD = 0.7;

    private Mention2EntityPrior prior;
    private boolean multipleEntitiesInCell;

    public PriorBasedEntityTaggingNode(int minMention2EntityFrequency, boolean multipleEntitiesInCell) {
        this.prior = new Mention2EntityPrior(minMention2EntityFrequency, 1);
        this.multipleEntitiesInCell = multipleEntitiesInCell;
    }

    public PriorBasedEntityTaggingNode() {
        this(1, false);
    }

    @Override
    public boolean process(Table table) {
        for (int c = 0; c < table.nColumn; ++c) {
            for (int r = 0; r < table.nHeaderRow; ++r) {
                // No entity tagging for header cells
                // tagCell(table.header[r][c]);
                table.header[r][c].entityLinks = new ArrayList<>();
            }
            for (int r = 0; r < table.nDataRow; ++r) {
                tagCell(table.data[r][c]);
            }
        }
        return true;
    }

    public void tagCell(Cell cell) {
        cell.entityLinks = new ArrayList<>();
        try {
            // if cell's content is number then stop tagging.
            Double.parseDouble(cell.text);
            return;
        } catch (Exception e) {
        }

        ArrayList<String> arr = new ArrayList<>(Arrays.asList(cell.text.split(" ")));
        int current = 0;
        while (current < arr.size()) {
            boolean found = false;
            for (int l = arr.size() - current; l >= 1; --l) {
                if (!multipleEntitiesInCell && l < arr.size() * REPRESENTATIVE_THRESHOLD) {
                    break;
                }
                int candidatePos = -1, candidateFreq = 0;
                String candidateEntity = null;
                for (int s = current; s < arr.size(); ++s) {
                    if (s + l > arr.size()) {
                        break;
                    }
                    List<Pair<String, Integer>> candidates = prior.getCanditateEntitiesForMention(String.join(" ", arr.subList(s, s + l)));
                    if (candidates == null) {
                        continue;
                    }

                    // Make a candidate.
                    Pair<String, Integer> candidate = candidates.get(0);

                    if (candidateEntity == null || candidate.second > candidateFreq) {
                        candidateEntity = candidate.first;
                        candidateFreq = candidate.second;
                        candidatePos = s;
                    }
                }
                if (candidateEntity != null) {
                    EntityLink el = new EntityLink();
                    el.text = String.join(" ", arr.subList(candidatePos, candidatePos + l));
                    el.target = "YAGO:" + candidateEntity.substring(1, candidateEntity.length() - 1);
                    cell.entityLinks.add(el);

                    current = candidatePos + l;
                    found = true;
                    break;
                }
            }
            if (!found || !multipleEntitiesInCell) {
                break;
            }
        }
    }

    public static void main(String[] args) {
        Cell cell = new Cell();
        cell.text = "\" How Chelsea Got Her Groove Back \"";
        new PriorBasedEntityTaggingNode().tagCell(cell);
        cell.quantityLinks = new ArrayList<>();
        System.out.println(cell.getDisambiguatedText());
    }
}
