package data.table.background.qfact_text;

import model.quantity.QuantityDomain;
import model.text.QuantitativeFact;
import model.text.Sentence;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;

public class GenerateTrainingDataForTabQs {
    public static boolean isGoodSentence(Sentence sent) {
        return true;
    }

    public static boolean isGoodQuantitativeFact(QuantitativeFact fact, String quantityDomain) {
        // fact.conf >= 0.9 && entityTag.confidence >= 0.5 covers ~ 53% of NYT; ~77% of STICS
        return
                QuantityDomain.quantityMatchesDomain(fact.quantityTag.quantity, quantityDomain) &&
                        fact.conf >= 0.0 && (fact.entityTag == null || fact.entityTag.confidence >= 0.5);
    }

    private static int getQposFromTrainingSample(String line) {
        line = line.split("\t")[1];
        return Integer.parseInt(line.substring(0, line.indexOf(' ')));
    }

    // args: <input> <output_train> [output_raw]
    public static void main(String[] args) {
        args = ("/local/home/hvthinh/TabQs/deep/data/train/all.gz /home/hvthinh/TabQs/non-deep/tabqs_train_noMinConf.gz")
                .split("\\s++");

//        args = ("./data/nyt/nytimes_aida.tar.bz2_train_with_negative.gz ./data/nyt/train_all_with_negative.gz")
//                .split("\\s++");
        PrintWriter train_out = FileUtils.getPrintWriter(args[1]);

        for (String line : FileUtils.getLineStream(args[0])) {
            Sentence sent = Gson.fromJson(line, Sentence.class);
            if (!isGoodSentence(sent)) {
                continue;
            }
            for (int i = sent.quantitativeFacts.size() - 1; i >= 0; --i) {
                if (!isGoodQuantitativeFact(sent.quantitativeFacts.get(i), QuantityDomain.Domain.ANY)) {
                    sent.quantitativeFacts.remove(i);
                }
            }
            for (int i = sent.negativeQuantitativeFacts.size() - 1; i >= 0; --i) {
                if (!isGoodQuantitativeFact(sent.negativeQuantitativeFacts.get(i), QuantityDomain.Domain.ANY)) {
                    sent.negativeQuantitativeFacts.remove(i);
                }
            }

            for (String str : sent.getPrintingTrainingDataForTabQs()) {
                train_out.println(str);
            }
        }

        train_out.close();
    }
}
