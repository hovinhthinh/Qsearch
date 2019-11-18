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

    private Mention2EntityPrior prior;
    private boolean multipleEntitiesInCell;

    public PriorBasedEntityTaggingNode(int minMention2EntityFrequency, boolean multipleEntitiesInCell) {
        this.prior = new Mention2EntityPrior(minMention2EntityFrequency);
        this.multipleEntitiesInCell = multipleEntitiesInCell;
    }

    public PriorBasedEntityTaggingNode() {
        this(2, false);
    }

    @Override
    public boolean process(Table table) {
        for (int col = 0; col < table.nColumn; ++col) {
            for (Cell[] row : table.data) {
                for (int c = 0; c < table.nColumn; ++c) {

                    for (int r = 0; r < table.nHeaderRow; ++r) {
                        // No entity tagging for header cells
                        // tagCell(table.header[r][c]);
                    }
                    for (int r = 0; r < table.nDataRow; ++r) {
                        tagCell(table.data[r][c]);
                    }
                }
            }
        }
        return true;
    }

    public void tagCell(Cell cell) {
        cell.entityLinks = new ArrayList<>();
        ArrayList<String> arr = new ArrayList<>(Arrays.asList(cell.text.split(" ")));
        int current = 0;
        while (current < arr.size()) {
            boolean found = false;
            loop:
            for (int l = arr.size() - current; l >= 1; --l) {
                for (int s = current; s < arr.size(); ++s) {
                    if (s + l > arr.size()) {
                        continue;
                    }
                    String mention = String.join(" ", arr.subList(s, l));
                    List<Pair<String, Integer>> candidates = prior.getCanditateEntitiesForMention(mention);
                    if (candidates == null) {
                        continue;
                    }

                    // Make a candidate.
                    String candidate = candidates.get(0).first;
                    EntityLink el = new EntityLink();
                    el.text = mention;
                    el.target = "YAGO:" + candidate.substring(1, candidate.length() - 1);
                    cell.entityLinks.add(el);


                    current = s + l;
                    found = true;
                    break loop;
                }
            }
            if (!found || !multipleEntitiesInCell) {
                break;
            }
        }
    }

    public static void main(String[] args) {
        Cell cell = new Cell();
        cell.text = "Congo , Republic of the";
        new PriorBasedEntityTaggingNode().tagCell(cell);
        cell.quantityLinks = new ArrayList<>();
        System.out.println(cell.getDisambiguatedText());
    }
}
