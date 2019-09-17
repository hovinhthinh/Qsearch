package nlp;

import edu.knowitall.tool.tokenize.Token;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import org.json.JSONObject;
import scala.collection.JavaConversions;
import util.Triple;
import util.headword.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NLP {
    public static final HeadFinder HEAD_FINDER = new CollinsHeadFinder();

    public static String getHeadWord(String phrase, boolean fast) {
        if (!phrase.contains(" ")) {
            return phrase;
        }
        try {
            // Use faster checking first.
            phrase = StringUtils.headWord(phrase);
            if (phrase.contains(" ") && !fast) {
                // If fail then use the slower but better one.
                Tree tree = new Sentence(phrase).parse();
                while (!tree.isLeaf()) {
                    tree = HEAD_FINDER.determineHead(tree);
                }
                return tree.toString();
            } else {
                return phrase;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String stripSentence(String sentence) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentence.length(); ++i) {
            char c = sentence.charAt(i);
            if (c == 160 || Character.isWhitespace(c)) {
                continue;
            }
            if (sb.length() != 0) {
                sb.append(' ');
            }
            int j = i;
            do {
                if (j >= sentence.length()) {
                    break;
                }
                c = sentence.charAt(j);
                if (c == 160 || Character.isWhitespace(c)) {
                    break;
                }
                sb.append(c);
                ++j;
            } while (true);
            i = j;
        }
        return sb.toString();
    }

    public static ArrayList<String> splitSentence(String sentence) {
        ArrayList<String> arr = new ArrayList<>();
        for (int i = 0; i < sentence.length(); ++i) {
            char c = sentence.charAt(i);
            if (c == 160 || Character.isWhitespace(c)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            int j = i;
            do {
                if (j >= sentence.length()) {
                    break;
                }
                c = sentence.charAt(j);
                if (c == 160 || Character.isWhitespace(c)) {
                    break;
                }
                sb.append(c);
                ++j;
            } while (true);
            i = j;
            arr.add(sb.toString());
        }
        return arr;
    }

    public static String replaceNonLetterOrDigitBySpace(String sentence) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentence.length(); ++i) {
            char c = sentence.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    public static String join(String seperator, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(seperator);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    // Use the published service:
    // $ curl --data text="Dylan was born in Duluth." https://gate.d5.mpi-inf.mpg.de/aida/service/disambiguate
    // Input sentence should already be tokenized.
    // Output: <startTokenIndex> <numTokens> <KB_id>
    // Return null in case of exception.
    @Deprecated
    public static ArrayList<Triple<Integer, Integer, String>> disambiguateWithAIDAOnline(String input) {
        try {
            HttpURLConnection http = (HttpURLConnection)
                    new URL("https://gate.d5.mpi-inf.mpg.de/aida/service/disambiguate").openConnection();
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            byte[] out = ("text=" + URLEncoder.encode(input, "UTF-8")).getBytes();
            http.setFixedLengthStreamingMode(out.length);
            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            http.connect();
            try (OutputStream os = http.getOutputStream()) {
                os.write(out);
                os.flush();
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                builder.append(line).append("\r\n");
            }
            http.disconnect();

            JSONObject json = new JSONObject(builder.toString());
            ArrayList<Triple<Integer, Integer, String>> output = new ArrayList<>();
            String annotatedText = json.getString("annotatedText");
            if (annotatedText != null) {
                int current = 0;
                do {
                    int start = annotatedText.indexOf("[[", current);
                    if (start == -1) {
                        break;
                    }
                    int mid = annotatedText.indexOf("|", start + 2);
                    if (mid == -1) {
                        break;
                    }
                    int end = annotatedText.indexOf("]]", mid + 1);
                    if (end == -1) {
                        break;
                    }
                    current = end + 2;
                    String id = annotatedText.substring(start + 2, mid);
                    if (id.equals("AIDA:--OOKBE--")) {
                        continue;
                    }
                    String token = annotatedText.substring(mid + 1, end);
                    int oriPos = input.indexOf(token);
                    if (oriPos != -1) {
                        String prefix = input.substring(0, oriPos).trim();
                        int startToken = 0;
                        if (prefix.isEmpty()) {
                            startToken = 0;
                        } else {
                            startToken = prefix.split("\\s++").length;
                        }
                        output.add(new Triple<>(startToken, token.trim().split("\\s++").length, id));
                    }
                } while (true);
            }
            return output;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Extracted sentences with coreference resolved.
    // Restrict to resolve only the first mention for a single sentence.

    /**
     * @Deprecated public static List<String> coreferenceResolution(String paragraph) {
     * Static.initCoreNLP();
     * paragraph = paragraph.replaceAll("\\s++", " ").trim();
     * CoreDocument document = new CoreDocument(paragraph);
     * Static.PIPELINE.annotate(document);
     * List<String> result = new ArrayList<>();
     * <p>
     * for (int i = 0; i < document.sentences().size(); ++i) {
     * CoreSentence sentence = document.sentences().get(i);
     * System.out.println(sentence.toString());
     * StringBuilder builder = new StringBuilder();
     * ArrayList<Pair<CorefChain.CorefMention, CorefChain.CorefMention>> resolveList = new ArrayList<>();
     * for (CorefChain chain : document.corefChains().values()) {
     * if (chain.getRepresentativeMention().position.get(0) == i + 1) {
     * // Only resolve if representative mention is from another sentence.
     * continue;
     * }
     * for (CorefChain.CorefMention m : chain.getMentionsInTextualOrder()) {
     * if (m.position.get(0) != i + 1 || m.mentionSpan.equalsIgnoreCase(chain.getRepresentativeMention().mentionSpan)) {
     * // Looking for mentions from this sentence && no changes in text form.
     * continue;
     * }
     * resolveList.add(new Pair(m, chain.getRepresentativeMention()));
     * }
     * }
     * resolveList.sort(new Comparator<Pair<CorefChain.CorefMention, CorefChain.CorefMention>>() {
     * @Override public int compare(Pair<CorefChain.CorefMention, CorefChain.CorefMention> o1,
     * Pair<CorefChain.CorefMention, CorefChain.CorefMention> o2) {
     * return o1.first.startIndex - o2.first.startIndex;
     * }
     * });
     * <p>
     * Pair<CorefChain.CorefMention, CorefChain.CorefMention> coref = null;
     * // Only resolve 1 mention.
     * if (resolveList.size() > 0) {
     * coref = resolveList.get(0);
     * }
     * <p>
     * int current = 0;
     * for (Iterator<CoreLabel> it = sentence.tokens().iterator(); it.hasNext(); ) {
     * CoreLabel token = it.next();
     * ++current;
     * if (builder.length() != 0) {
     * builder.append(" ");
     * }
     * if (coref != null && current == coref.first.startIndex) {
     * builder.append(coref.second.mentionSpan);
     * for (int j = 0; j < coref.first.endIndex - coref.first.startIndex - 1; ++j) {
     * it.hasNext();
     * it.next();
     * }
     * } else {
     * builder.append(token.value());
     * }
     * }
     * result.add(builder.toString());
     * }
     * return result;
     * }
     */

    public static final String fastStemming(String phrase, int morphaCode) {
        ArrayList<String> arr = NLP.splitSentence(phrase);
        for (int i = 0; i < arr.size(); ++i) {
            arr.set(i, StringUtils.stem(arr.get(i).toLowerCase(), morphaCode));
        }
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(s);
        }
        return sb.toString();
    }


    public static final List<String> tokenize(String text) {
        text = NLP.stripSentence(text);
        List<Token> tokens = JavaConversions.seqAsJavaList(Static.getOpenIe().tokenizer().tokenize(Static.getOpenIe().clean(text)));
        return tokens.stream().map(o -> o.string()).collect(Collectors.toList());
    }

    public static void main(String[] args) {
        System.out.println(String.join(" ", tokenize("$916k.")));
    }
}
