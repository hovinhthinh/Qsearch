package storage.table.experimental;

import com.google.gson.Gson;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.NLP;
import pipeline.TextBasedColumnScoringNode;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.headword.StringUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

@Deprecated
public class TableQfactSaver {
    public static void main(String[] args) {
        String wikiFile = "/GW/D5data-12/hvthinh/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.tables+id_annotation+linking.gz";
        String tablemFile = "/GW/D5data-11/hvthinh/TABLEM/all/all+id.annotation+linking.gz";

        double LINKING_THRESHOLD = 0.70;

        Gson gson = new Gson();
        PrintWriter out = FileUtils.getPrintWriter("/GW/D5data-12/hvthinh/TabQs/annotation+linking/wiki+tablem_qfacts.gz", "UTF-8");
        for (String file : Arrays.asList(tablemFile, wikiFile))
            for (String line : FileUtils.getLineStream(file, "UTF-8")) {
                Table table = gson.fromJson(line, Table.class);
                // for all Qfacts
                for (int qCol = 0; qCol < table.nColumn; ++qCol) {
                    if (!table.isNumericColumn[qCol] || (LINKING_THRESHOLD != -1 && table.quantityToEntityColumnScore[qCol] < LINKING_THRESHOLD)) {
                        continue;
                    }

                    for (int row = 0; row < table.nDataRow; ++row) {
                        QuantityLink ql = table.data[row][qCol].getRepresentativeQuantityLink();
                        if (ql == null) {
                            continue;
                        }
                        EntityLink el = table.data[row][table.quantityToEntityColumn[qCol]].getRepresentativeEntityLink();
                        if (el == null) {
                            continue;
                        }

                        Quantity qt = ql.quantity;

                        String domain = QuantityDomain.getDomain(qt, true);
                        // context
                        ArrayList<String> X = new ArrayList<>(NLP.splitSentence(table.getCombinedHeader(qCol)));
                        if (domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                            X.addAll(NLP.splitSentence(qt.unit));
                            qt.unit = "";
                        }
                        for (int j = X.size() - 1; j >= 0; --j) {
                            X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                            if (NLP.BLOCKED_STOPWORDS.contains(X.get(j)) || TextBasedColumnScoringNode.BLOCKED_OVERLAP_CONTEXT_TOKENS.contains(X.get(j))) {
                                X.remove(j);
                            }
                        }
                        if (X.isEmpty()) {
                            continue;
                        }

                        out.println(String.format("%s\t%s\t%s\t%.2f\t%s\t%s",
                                el.target,
                                X.toString(),
                                ql.quantity.toString(2),
                                table.quantityToEntityColumnScore[qCol],
                                domain,
//                                TaxonomyGraph.getDefaultGraphInstance().getTextualizedTypes("<" + el.target.substring(el.target.lastIndexOf(':') + 1) + ">", false).toString(),
                                table.source
                        ));
                    }
                }
            }

        out.close();
    }
}
