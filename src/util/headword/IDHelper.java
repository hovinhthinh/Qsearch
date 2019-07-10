package util.headword;

import util.FileUtils;
import util.Pair;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ntandon
 */

public class IDHelper {

    public static String cacheFile(String tbName) {
        try {
            String dbText =
                    "./resources/headword/db-text/";
            String localFile = dbText + tbName;
            return localFile;
        } catch (Exception e) {
            return "";
        }
    }

    public static class WNWords {

        private static AutoMap<String, Set<String>> wnWordsNo_;
        private static AutoMap<String, Set<Character>> wnWordTypes;
        private static AutoMap<String, Character> wnWpsTypes;
        private static Set<String> wnFreqPhysical;

        /**
         * If the word is in wordnet returns list of pos it occurs with, else
         * null
         *
         * @param w e.g. potato
         * @return a: adjective, v: verb, n: noun, r: adverb
         * @throws SQLException during initialization of wordnet (words, pos) map
         * @throws IOException
         */
        public static Pair<String, Set<String>> inWN(String w)
                throws SQLException, IOException {
            if (wnWordsNo_ == null) {
                wnWordsNo_ = new AutoMap<>();
                loadWnWordsNo_();
            }

            if (w == null)
                return null;

            w = w.toLowerCase();

            // only check variations of two worded w e.g. horse shoe
            // tolowercase Yunnan province
            if (!wnWordsNo_.containsKey(w)) {
                // horse-shoe
                if (!w.contains(" "))
                    return null;

                // w= horse shoe; wn has horseshoe or wn has horse-shoe
                if (wnWordsNo_.containsKey(w.replace(" ", ""))) {
                    return new Pair<>(w.replace(" ", ""),
                            wnWordsNo_.get(w.replace(" ", "")));
                } else if (wnWordsNo_.containsKey(w.replace(' ', '-'))) {
                    return new Pair<>(w.replace(" ", ""),
                            wnWordsNo_.get(w.replace(' ', '-')));
                } else
                    // give up
                    return null;
            } else
                return new Pair<String, Set<String>>(w, wnWordsNo_.get(w));
        }

        private static void loadWnWordsNo_() throws IOException, SQLException {
            String tbName = "wordnet.wn_synsets";
            String localFile = cacheFile(tbName);
            if (!localFile.isEmpty()) {
                String[] fields;
                for (String line : FileUtils.getLineStream(localFile, "UTF-8")) {
                    fields = line.split("\t");
                    String pos = fields[3].equals("s") ? "a" : fields[3];
                    wnWordsNo_.addSetValue(fields[2].toLowerCase(),
                            pos);
                }
            }
        }

        public static Set<Character> getWNWordTypes(String w)
                throws IOException, SQLException {
            if (wnWordTypes == null) {
                wnWordTypes = new AutoMap<>();
                loadWNTypes();
            }

            if (w == null)
                return null;

            w = w.toLowerCase();

            if (!wnWordTypes.containsKey(w))
                return null;

            return wnWordTypes.get(w);
        }

        private static void loadWNTypes() throws SQLException, IOException {
            String tbName = "ngram.wntypes_tree";
            String localFile = cacheFile(tbName);
            if (!localFile.isEmpty()) {
                String[] fields;
                for (String line : FileUtils.getLineStream(localFile, "UTF-8")) {
                    fields = line.split("\t");
                    wnWordTypes.addSetValue(fields[0].replace('_',
                            ' '), fields[4].charAt(0));
                }
            }
        }

        /**
         * Get WordNet noun type {p=physical, a=abstract, i=instance}
         *
         * @param wps
         * @return
         * @throws IOException
         * @throws SQLException
         */
        public static Character getWNWpsTypes(String wps) throws IOException,
                SQLException {

            if (wnWpsTypes == null) {
                wnWpsTypes = new AutoMap<>();
                loadWNWpsTypes();
            }

            if (wps == null)
                return null;

            wps = wps.toLowerCase();

            if (wnWpsTypes.containsKey(wps))
                return wnWpsTypes.get(wps);
            else {
                wps = wps.replace('_', ' ');
                if (wnWpsTypes.containsKey(wps))
                    return wnWpsTypes.get(wps);
            }
            return null;
        }

        private static void loadWNWpsTypes() throws IOException, SQLException {
            String tbName = "ngram.wntypes_tree";
            String localFile = cacheFile(tbName);
            if (!localFile.isEmpty()) {
                for (String line : FileUtils.getLineStream(localFile, "UTF-8")) {
                    String[] fields = line.split("\t");
                    wnWpsTypes.put(fields[2].replace('_', ' '), fields[4]
                            .charAt(0));
                }
            }

        }

        public static boolean isFreqPhysical(String w) throws IOException {
            if (wnFreqPhysical == null) {
                wnFreqPhysical = new HashSet<>();
                String localFile = cacheFile("freq-phy-nouns");
                if (!localFile.isEmpty()) {
                    for (String line : FileUtils.getLineStream(localFile, "UTF-8")) {
                        if (line.startsWith("#"))
                            continue;
                        wnFreqPhysical.add(line);
                    }
                }

            }
            if (w == null)
                return false;
            w = w.toLowerCase();
            return wnFreqPhysical.contains(w);
        }


        /**
         * Check if w is instance in WN for example Texas, Los Angeles
         *
         * @param w
         * @return boolean if w is an instance in WN
         */
        public static boolean isAnInstance(String w) {
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

        /**
         * Check if w is instance in WN for example Texas, Los Angeles
         *
         * @param w
         * @return boolean if w is an instance in WN
         */
        public static boolean isPhyAndNotAnInstance(String w) {
            try {
                Set<Character> types = IDHelper.WNWords.getWNWordTypes(w);
                if (types == null)
                    return false;
                else if (!types.contains('i') && types.contains('p'))
                    return true;
            } catch (IOException | SQLException e) {
            }
            return false;
        }
    }
}