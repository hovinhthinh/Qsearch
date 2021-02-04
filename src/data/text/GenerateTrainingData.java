package data.text;

import model.quantity.QuantityDomain;
import model.text.QuantitativeFact;
import model.text.Sentence;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.util.HashSet;

public class GenerateTrainingData {
    public static boolean isGoodSentence(Sentence sent) {
        return true;
    }

    public static boolean isGoodQuantitativeFact(QuantitativeFact fact, String quantityDomain) {
        // fact.conf >= 0.9 && entityTag.confidence >= 0.5 covers ~ 53% of NYT; ~77% of STICS
        return fact.quantityTag.quantity.matchesSearchDomain(quantityDomain) &&
                fact.conf >= 0.9 && (fact.entityTag == null || fact.entityTag.confidence >= 0.5);
    }

    private static int getQposFromTrainingSample(String line) {
        line = line.split("\t")[1];
        return Integer.parseInt(line.substring(0, line.indexOf(' ')));
    }

    // args: <input> <output_train> [output_raw]
    public static void main(String[] args) {
        args = ("data/text/stics/news-en-documents_20181120_train_with_negative.gz ./data/stics/train_all_with_negative.gz")
                .split("\\s++");

//        args = ("./data/nyt/nytimes_aida.tar.bz2_train_with_negative.gz ./data/nyt/train_all_with_negative.gz")
//                .split("\\s++");
        PrintWriter train_out = FileUtils.getPrintWriter(args[1]);
        PrintWriter raw_out = null;
        if (args.length > 2) {
            raw_out = FileUtils.getPrintWriter(args[2]);
        }


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
            if (raw_out != null) {
                for (String str : sent.getPrintingQuantitativeFacts()) {
                    raw_out.println(str);
                }
            }
            HashSet<Integer> qposSet = new HashSet<>();
            for (String str : sent.getPrintTrainingSamples()) {
                train_out.println(str);
                qposSet.add(getQposFromTrainingSample(str));
            }

            // Negative training samples.
            for (String str : sent.getPrintNegativeTrainingSamples()) {
                if (!qposSet.contains(getQposFromTrainingSample(str))) {
                    train_out.println(str);
                }
            }
        }

        if (raw_out != null) {
            raw_out.close();
        }
        train_out.close();
    }
}
