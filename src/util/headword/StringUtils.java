package util.headword;

import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.Pair;

import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

public class StringUtils {


    private static final ThreadLocal<Morpha> lexer = ThreadLocal.withInitial(() -> new Morpha(System.in));
    public static Collection<String> copularVerbs = new HashSet<>(
            Arrays.asList("be", "has", "have", "had", "is", "was", "are", "were"));
    public static Collection<String> articles = new HashSet<String>(
            Arrays.asList("a", "an", "the", "your", "my", "our", "his", "her"));
    public static Collection<String> prepositions = new HashSet<String>(Arrays.asList("in", "on", "at",
            "with", "into", "across", "opposite", "toward", "towards", "through", "beyond", "aboard", "amid", "past",
            "by", "near", "nearby", "above", "below", "over", "under", "up", "down", "around", "through", "inside",
            "out", "outside", "outside of", "between", "beside", "besides", "beyond", "in front of", "in back of",
            "behind", "next to", "on top of", "within", "beneath", "underneath", "among", "along", "against",

            "aboard", "about", "above", "across", "after", "against", "along", "amid", "among", "anti", "around", "as",
            "at", "before", "behind", "below", "beneath", "beside", "besides", "between", "beyond", "but", "by",
            "concerning", "considering", "despite", "down", "during", "except", "excepting", "excluding", "following",
            "for", "from", "in", "inside", "into", "in front of", "like", "minus", "near", "of", "off", "on", "onto",
            "opposite", "outside", "over", "past", "per", "plus", "regarding", "round", "save", "since", "than",
            "through", "to", "toward", "towards", "under", "underneath", "unlike", "until", "up", "upon", "versus",
            "via", "with", "within", "without"));
    public static Collection<String> MODAL_VERBS = (new HashSet<String>(Arrays.asList(
            "can", "could", "may", "might", "will", "would", "must", "shall", "should", "ought to")));
    public static Collection<String> STOPWORDS = (new HashSet<String>(Arrays.asList("a", "able", "about",
            "across", "after", "all", "almost", "also", "always", "am", "among", "an", "and", "another", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "but", "by", "can", "cannot", "could", "dear",
            "did", "do", "does", "either", "else", "ever", "every", "few", "for", "from", "get", "got", "had", "has",
            "have", "he", "her", "here", "hers", "him", "his", "how", "however", "i", "if", "in", "into", "is", "it",
            "its", "just", "least", "let", "like", "likely", "lrb", "many", "may", "me", "might", "mine", "more",
            "most", "much", "must", "my", "neither", "no", "none", "nor", "not", "nothing", "now", "nt", "of", "off",
            "often", "on", "only", "or", "other", "our", "ours", "own", "per", "rather", "rrb", "said", "say", "says",
            "she", "should", "since", "so", "some", "somehow", "still", "such", "than", "that", "the", "their",
            "theirs", "them", "then", "there", "these", "they", "this", "those", "though", "tis", "to", "too", "twas",
            "u", "us", "very", "want", "wants", "was", "we", "were", "what", "when", "where", "which", "while", "who",
            "whom", "why", "will", "with", "would", "www", "yet", "you", "your", "yours", "yourss", "'m", "'ll", "a",
            "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "as", "at", "be",
            "because", "been", "before", "being", "below", "between", "both", "but", "by", "cannot", "could", "did",
            "do", "does", "dont", "doesnt", "cant", "doing", "down", "during", "each", "few", "for", "from", "further",
            "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him", "himself", "his", "how",
            "however", "i", "if", "in", "into", "is", "it", "its", "itself", "let", "lrb", "me", "more", "most", "must",
            "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our",
            "ours ourselves", "out", "over", "own", "rrb", "same", "sha", "she", "should", "so", "some", "such", "than",
            "that", "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this", "those",
            "through", "to", "too", "under", "until", "up", "very", "was", "we", "were", "what", "when", "where",
            "which", "while", "who", "who", "whom", "why", "why", "with", "wo", "would", "would", "you", "you", "you",
            "you", "you", "your", "yours", "yourself", "yourselves",
            "ser", "lady", "lord", "king", "house", "iron")));
    private static DecimalFormat decim;

    /**
     * Woodman_\u0028town\u0029\u002c_Wisconsin --> Woodman_(town),_Wisconsin
     *
     * @param str. E.g. Woodman_\u0028town\u0029\u002c_Wisconsin
     * @return unicode form of str. E.g. Woodman_(town),_Wisconsin
     */
    public static String unicodeConverter(String str) {
        String text = "";
        int i = 0;
        while (i < str.length() - 1) {
            if (str.charAt(i) == '\\' && str.charAt(i + 1) == 'u') {
                String tmp = "";
                for (int j = i + 2; j < i + 6; j++)
                    tmp += str.charAt(j);
                int hexVal;
                try {
                    hexVal = Integer.parseInt(tmp, 16);
                } catch (NumberFormatException e) {
                    return str;
                }
                text += (char) hexVal;
                i += 6;
            } else
                text += str.charAt(i++);

        }
        if (i < str.length())
            text += str.charAt(i);
        return text;
    }

    public static String normalizeNP(String o, boolean pickHeadWords) throws IOException {
        o = judiciousStemming(o, Morpha.noun);
        o = dropLeadingArticles(o);
        if (pickHeadWords) {
            try {
                o = headWordStr(o.split(" "));
            } catch (SQLException | IOException e) {
                throw new IOException("Some of the WordNet related files are not set, check NLPUtil");
            }
        }

        // Open IE attracts a lot of noise, remove it by checking if the noun
        // is frequent w.r.t. WordNet
        if (!isStopWord(o))
            return o;
        else return "";
    }

    /*public static String headWord(String s) {
        String[] words = s.split(" ");
        return words[headWord(words)];
    }*/
    public static String headWord(String s) throws SQLException, IOException {
        return headWordStr(s.split(" "));
    }

    /**
     * returns position of headword.
     *
     * @throws IOException
     * @throws SQLException
     */
    public static int headWord(String[] s) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length; i++) {
            String w = s[i];
            // tower of hanoi (here, tower = index 0, i.e. preceding "of")
            if (prepositions.contains(w) && sb.length() > 0)
                return i - 1;
            sb.append(sb.length() == 0 ? "" : " ").append(w);
        }

        // Fallback to last position case when no prepositions are present.
        return s.length - 1;
    }

    public static String headWordStr(String[] s) throws SQLException,
            IOException {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length; i++) {
            String w = s[i];
            // tower of hanoi (here, tower = index 0, i.e. preceding "of")
            if (prepositions.contains(w) && sb.length() > 0)
                break;
            sb.append(sb.length() == 0 ? "" : " ").append(w);
        }

        return wnHeadWord(sb.toString().split(" "));
    }

    /**
     * san francisco botanical garden -> botanical garden
     * Loop over phrases in the sentence to find a valid WN noun phrase as the head words.
     *
     * @param s input phrase
     * @return head words (valid WN noun phrases)
     * @throws IOException
     * @throws SQLException
     */
    private static String wnHeadWord(String[] s) throws SQLException,
            IOException {
        StringBuilder phrase;
        for (int i = 0; i < s.length; i++) {
            phrase = new StringBuilder();
            for (int j = i; j < s.length; j++)
                phrase.append(phrase.length() > 0 ? " " : "").append(s[j]);
            if (IDHelper.WNWords.inWN(phrase.toString()) != null)
                return phrase.toString();
        }
        // Fallback to last position case when no prepositions are present.
        return s[s.length - 1];
    }

    /************************************************************************
     * Stems only head noun (e.g. will stem "schools boxes" to "schools box")
     *
     * @param MorphaDotPOS
     *            morpha code : Morpha.noun , Morpha.verb , or Morpha.any
     ************************************************************************/
	/*public static String judiciousStemming(String phrase, int MorphaDotPOS) {

		// instance e.g. Los Angeles doesn't need to be stemmed
		// check if it's in WN
		// things is a WN entry. how to stem things to thing? --> keep instance as it is
		List<String> inWNList = new ArrayList<>();
		if (phrase.indexOf(" ") > 0 && MorphaDotPOS == Morpha.noun) {
			try {
				inWNList = IDHelper.WNWords.inWN(phrase).second;
			} catch (Exception e) {
			}
		}
		if (MorphaDotPOS == Morpha.noun && inWNList != null
				&& inWNList.size() > 0) {
			return phrase;
		} else {
			// Only for noun
			StringBuilder sb = new StringBuilder();
			String[] splitted = phrase.split(" ");
			if (MorphaDotPOS == Morpha.noun) {
				int headWordIndex = headWord(splitted);
				// The blue darts to blue darts.

				for (int i = 0; i < splitted.length; i++) {
					// if (!Util.isStopWord(splitted[i]))
					sb.append(i > 0 ? ' ' : "");
					if (i == headWordIndex)
						sb.append(stem(splitted[i], MorphaDotPOS));
					else
						sb.append(splitted[i]);
				}
			} else {
				for (int i = 0; i < splitted.length; i++) {
					// if (!Util.isStopWord(splitted[i]))
					sb.append(i > 0 ? ' ' : "").append(
							stem(splitted[i], MorphaDotPOS));
				}
			}

			if (sb.length() == 0)
				sb.append(sb.length() > 0 ? ' ' : "").append(
						stem(splitted[splitted.length - 1], MorphaDotPOS));

			return sb.toString();
		}
	}*/
    public static String judiciousStemming(String phrase, int MorphaDotPOS) {
        if (MorphaDotPOS == Morpha.noun && isAnInstance(phrase)) {
            return phrase;
        }
        StringBuilder sb = new StringBuilder();
        String[] splitted = phrase.split(" ");
        if (MorphaDotPOS == Morpha.noun) {
            int headWordIndex = headWord(splitted);
            // The blue darts to blue darts.

            for (int i = 0; i < splitted.length; i++) {
                // if (!Util.isStopWord(splitted[i]))
                sb.append(i > 0 ? ' ' : "");
                if (i == headWordIndex)
                    sb.append(stem(splitted[i], MorphaDotPOS));
                else
                    sb.append(splitted[i]);
            }
        } else {
            for (int i = 0; i < splitted.length; i++) {
                sb.append(i > 0 ? ' ' : "").append(
                        stem(splitted[i], MorphaDotPOS));
            }
        }

        if (sb.length() == 0)
            sb.append(sb.length() > 0 ? ' ' : "").append(
                    stem(splitted[splitted.length - 1], MorphaDotPOS));

        return sb.toString();
        // }
    }

    /****
     * Check if input phrase is eligible to be stemmed.
     * (In short, we don't stem instances)
     * @param phrase los_angeles -> false, boxes -> true
     * @return
     * @throws SQLException
     * @throws IOException
     */

    public static String stem(String w, int MorphaDotPOS) {
        String stemmed = null;
        if (MorphaDotPOS == Morpha.noun) {
            stemmed = IrregularPlurals.PLURAL.getSingular(w);
        }
        if (stemmed == null || stemmed.isEmpty())
            stemmed = stemExceptWN(w, MorphaDotPOS);
        return stemmed;
    }

    private static boolean isAnInstance(String w) {
        try {
            Set<Character> types = IDHelper.WNWords.getWNWordTypes(w);
            if (types == null)
                return false;
            else if (types.size() == 1 && types.contains('i'))
                return true;
        } catch (IOException | SQLException e) {
        }
        return false;
    }

    private static String stemExceptWN(String w, int MorphaDotPOS) {
        if (w.equals("_s"))
            return w; // exception for _s which originally was 's
        else {
            String stemmed = stemMorpha(w, MorphaDotPOS);
            String pos = "";
            switch (MorphaDotPOS) {
                case Morpha.noun:
                    pos = "n";
                    break;
                case Morpha.verb:
                    pos = "v";
                    break;
            }
            Pair<String, Set<String>> inWN = null;
            try {
                inWN = IDHelper.WNWords.inWN(stemmed);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (inWN == null)
                return w;
            if (inWN.second == null || inWN.second.isEmpty())
                return w;
            if (pos.isEmpty())
                return stemmed;
            if (inWN.second.contains(pos))
                return stemmed;
            else
                return w;
        }
    }

    private static int countChar(String s, char ec, boolean shouldTrim) {
        int count = 0;
        if (shouldTrim)
            s = s.trim();
        for (char c : s.toCharArray()) {
            if (c == ec)
                count++;
        }
        return count;
    }

    /*************************************************************************
     * That is, it only does noun plurals, pronoun case, and verb endings, and
     * not things like comparative adjectives or derived nominals. It is based
     * on a finite-state transducer implemented by John Carroll et al., written
     * in flex and publicly available. See:
     * http://www.informatics.susx.ac.uk/research/nlp/carroll/morph.html .
     *
     * @param w
     *            e.g. fighter jets
     * @param morphaPOSNum
     *            (Use Morpha. static members) e.g. 2 (for noun), 3 (for any)
     * @return fighter jet (note that fighters jets returns fighters jet
     * @usage Util.stem(" goes ", Morpha.verb);
     ************************************************************************/
    public static String stemMorpha(String w, int morphaPOSNum) {
        try {
            if (w == null || w.length() == 0)
                return w;
            int numWords = countChar(w, ' ', false) + 1;
            String[] ws = null;
            if (numWords > 1)
                ws = w.split(" ");
            Morpha lex = lexer.get();
            lex.yyreset(new StringReader(numWords == 1 ? w
                    : ws[ws.length - 1]));
            lex.yybegin(morphaPOSNum);
            if (numWords == 1)
                return lex.next();
            else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ws.length - 1; i++)
                    sb.append(ws[i]).append(" ");
                sb.append(lex.next());
                return sb.toString();
            }
        } catch (Exception e) {
            /*
             * System.out.println("Exception in stemming (" + w + "): " +
             * e.getMessage());
             */
            // e.printStackTrace();
        } catch (Error e) {
            // Sometimes Morpha throws Error!
            // Exception in thread "main" java.lang.Error: Error: could not
            // match input
            /*
             * System.out.println("Error in stemming (" + w + "): " +
             * e.getMessage());
             */
            // e.printStackTrace();
        }
        return w;
    }

    // Check stop-words
    public static boolean isStopWord(String v) {
        return STOPWORDS.contains(v);
    }

    /**
     * returns position of headverb from headverbCandidate s.
     */
    public static int headVerb(String[] s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length; i++) {
            String w = s[i];
            // begin to peel (here, peel = index 1, i.e. succeeding "to")
            if (prepositions.contains(w) && sb.length() > 0

            ) {
                // prep. not last word e.g. heat up
                if (i != s.length - 1)
                    return i + 1;
                /*
                 * else if return 0;
                 */
            }
            sb.append(sb.length() == 0 ? "" : " ").append(w);
        }

        // had turned

        if (s.length > 1
                && (copularVerbs.contains(s[0]) || MODAL_VERBS.contains(s[0]) || STOPWORDS
                .contains(s[0]))) {

            return 1 + headVerb(Arrays.copyOfRange(s, 1, s.length));
        }

        // Fallback to last position case when no prepositions are present.
        // continue peeling
        if (s[s.length - 1].endsWith("ing")) // present tense.
            return s.length - 1;
        else
            // take out
            /*
             * return verbsInCorpus != null && verbsInCorpus.contains(s[0]) ? 0
             * : s.length - 1;
             */
            return 0;
    }

    /**
     * Input = a lion, Output = lion
     */
    public static String dropLeadingArticles(String w) {
        if (w == null || w.isEmpty())
            return "";

        String[] words = w.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {

            // Until the first non-stop word is seen.
            if (isArticle(words[i]) && result.length() == 0)
                continue;
            else
                result.append(result.length() == 0 ? "" : " ").append(words[i]);
        }

        return result.toString();

    }

    /**
     * Input = a/an/the/his/her (see {@link #articles}).., Output= true
     */
    private static boolean isArticle(String s) {
        return articles.contains(s);
    }


    public static String normString(String s) {
        if (s == null) s = "";
        return s.replaceAll("[^0-9a-zA-Z ,-.\\_%/+']+", " ")
                .replaceAll("'", "")
                .replaceAll("\\s+", " ")
                .replaceAll(" ,", ",")
                .trim().toLowerCase();
    }

    public static ArrayList<String> normString(ArrayList<String> s) {
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            result.add(normString(s.get(i)));
        }
        return result;
    }

    public static List<String> normList(List<String> l) throws SQLException, IOException {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < l.size(); i++) {
            String headword = headWord(l.get(i));
            headword = headword.contains(" ") ? headword.substring(headword.lastIndexOf(" ") + 1) : headword;
            headword = headword.replaceAll("[,.]", "");
            if (!STOPWORDS.contains(headword))
                result.add(headword);
        }
        return result;
    }

    public static List<String> removeStopwordInString(String s) {
        List<String> result = new ArrayList<>();
        String[] l = s.split(" ");
        for (int i = 0; i < l.length; i++) {
            if (!STOPWORDS.contains(l[i]))
                result.add(l[i]);
        }
        return result;
    }

    public static Set<String> removeStopwordFromString(String s) {
        Set<String> result = new HashSet<>();
        String[] l = s.split(" ");
        for (int i = 0; i < l.length; i++) {
            if (!STOPWORDS.contains(l[i]))
                result.add(l[i]);
        }
        return result;
    }

    public static String listToPhrase(List<String> list) {
        String res = "";
        if (list.size() > 0 && list.get(0).length() > 0) {
            for (String t : list) {
                res += t + "_";
            }
            res = res.substring(0, res.lastIndexOf("_") - 1);
        }
        return res;
    }

    public static String arrayToString(double[] vector) {
        String res = "";
        if (vector.length == 1) {
            return Double.toString(vector[0]);
        } else if (vector.length > 1) {
            for (int i = 0; i < vector.length - 1; i++)
                res += vector[i] + " ";
            res += Double.toString(vector[vector.length - 1]);
        }
        return res;
    }

    public static List<Integer> stringToListInt(String s) {
        List<Integer> res = new ArrayList<>();
        if (!s.contains(";"))
            res.add(Integer.parseInt(s));
        else {
            String[] tmp = s.split(";");
            for (String ss : tmp)
                res.add(Integer.parseInt(ss));
        }
        return res;
    }

    public static Set<String> stringtoSet(String s, String separate) {
        Set<String> res = new HashSet<>();
        if (s.contains(separate)) {
            String[] tmp = s.split(separate);
            for (String ss : tmp) {
                res.add(ss);
            }
        } else {
            res.add(s);
        }
        return res;
    }

    /**
     * 32.535534534534; after formatting = 32.536
     */
    public static String format(double x) {
        if (decim == null)
            decim = new DecimalFormat("#.###");
        return decim.format(x);
    }

    public static boolean equalIgnoreCaseAndLemma(String s1, String s2) {
        if (s1.equals(s2)) return true;
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        s1 = s1.replaceAll("[^a-z0-9 ]", " ").trim();
        s2 = s2.replaceAll("[^a-z0-9 ]", " ").trim();
        if (s1.equals(s2)) return true;
        if (!s1.contains(" ") && !s2.contains(" ")) {
            return stemMorpha(s1, 2).equals(stemMorpha(s2, 2));
        }
        if (s1.contains(" ") && s2.contains(" ")) {
            String[] s1tmp = s1.split(" ");
            String[] s2tmp = s2.split(" ");
            if (s1tmp.length != s2tmp.length) return false;
            for (int i = 0; i < s1tmp.length; i++) {
                if (!stemMorpha(s1tmp[i], 2).equals(stemMorpha(s2tmp[i], 2))) return false;
            }
            return true;
        }
        return false;
    }

    /**
     * remove all <..> tag
     *
     * @param s
     * @return
     */
    public static String removeTags(String s) {
        String o = "";
        boolean append = true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '<')
                append = false;

            if (append)
                o += s.charAt(i);

            if (s.charAt(i) == '>')
                append = true;
        }

        return o;
    }

    private enum IrregularPlurals {
        PLURAL();

        private final Map<String, String> pluralToSingularMap;

        IrregularPlurals() {
            this.pluralToSingularMap = loadPlurals();
        }

        private Map<String, String> loadPlurals() {
            Map<String, String> mapping = new HashMap<>();
            // women woman
            try {
                String[] splitted;
                for (String line : FileUtils.getLineStream("resources/headword/irregular-plurals.txt", "UTF-8")) {
                    splitted = line.split("\t");
                    mapping.put(splitted[0], splitted[1]);
                }
            } catch (Exception e) {
            }
            return mapping;
        }

        public String getSingular(String pluralNoun) {
            return PLURAL.pluralToSingularMap.get(pluralNoun);
        }

    }

}
