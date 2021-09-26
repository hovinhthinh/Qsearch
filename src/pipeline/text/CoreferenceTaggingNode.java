package pipeline.text;

import model.text.Paragraph;
import model.text.Sentence;
import model.text.Token;
import model.text.tag.EntityTag;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class CoreferenceTaggingNode implements TaggingNode {
    private BufferedReader in = null;
    private PrintWriter out = null;
    private Process p = null;
    private boolean logErrStream;
    private boolean useAllenCoref;

    public CoreferenceTaggingNode() {
        this(false, false);
    }

    public static class CorefMentionInfo {
        int sentIndex, beginToken, endToken;

        public CorefMentionInfo(int sentIndex, int beginToken, int endToken) {
            this.sentIndex = sentIndex;
            this.beginToken = beginToken;
            this.endToken = endToken;
        }
    }

    public void resetService(boolean logErrStream) {
        try {
            p.destroy();
        } catch (Exception e) {
        }
        try {
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    useAllenCoref ? "python3 -u allencoref_runner.py" : "python3 -u coref_runner.py"
            };
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .redirectError(logErrStream ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.DISCARD);
            pb.directory(new File("./coref"));
            p = pb.start();

            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));
            String str;
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            while (!(str = in.readLine()).equals("__coref_ready__")) {
//                System.out.println(str);
            }
            System.out.println(str);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // if logErrStream is true, need to explicitly call System.exit(0) at the end of the main thread.
    // only works
    public CoreferenceTaggingNode(boolean useAllenCoref, boolean logErrStream) {
        this.useAllenCoref = useAllenCoref;
        this.logErrStream = logErrStream;
        resetService(logErrStream);
    }

    public String callCorefService(Object data) {
        out.println(new JSONObject().put("paragraph", data).toString());
        out.flush();
        try {
            String str;
            while (!(str = in.readLine()).startsWith("__interactive_result__")) {
//                System.out.println(str);
            }
            return str.substring(str.indexOf("\t") + 1);
        } catch (NullPointerException | IOException e) {
            resetService(logErrStream);
            return null;
        }
    }

    public ArrayList<CorefMentionInfo> analyzeCorefMentionInfo(Paragraph paragraph, JSONArray cls) {
        ArrayList<CorefMentionInfo> infos = new ArrayList<>();
        loop_coref:
        for (int i = 0; i < cls.length(); ++i) {
            JSONObject corefMention = cls.getJSONObject(i);

            if (useAllenCoref) { // AllenCoref
                infos.add(new CorefMentionInfo(corefMention.getInt("sent"), corefMention.getInt("start"), corefMention.getInt("end")));
                continue;
            }

            int corefStart = corefMention.getInt("start");
            int corefEnd = corefMention.getInt("end");
            String corefText = corefMention.getString("text");

            CorefMentionInfo info = new CorefMentionInfo(-1, -1, -1);

            int numPassedChars = 0;

            loop_token:
            for (int x = 0; x < paragraph.sentences.size(); ++x) {
                Sentence sent = paragraph.sentences.get(x);
                for (int y = 0; y < sent.tokens.size(); ++y) {
                    Token token = sent.tokens.get(y);
                    if (info.beginToken == -1) {
                        if (numPassedChars + token.str.length() + 1 > corefStart) {
                            if (numPassedChars < corefStart) {
                                continue loop_coref;
                            } else {
                                info.sentIndex = x;
                                info.beginToken = y;
                            }
                        }
                    }
                    if (info.endToken == -1) {
                        if (numPassedChars + token.str.length() + 1 > corefEnd) {
                            if (numPassedChars + token.str.length() > corefEnd) {
                                continue loop_coref;
                            } else {
                                info.endToken = y + 1;
                            }
                        }
                    }

                    numPassedChars += token.str.length() + 1;
                    if (info.sentIndex != -1 && info.beginToken != -1 && info.endToken != -1) {
                        break loop_token;
                    }
                }
                if (info.sentIndex != -1) {
                    break;
                }
            }

            if (info.sentIndex != -1 && info.beginToken != -1 && info.endToken != -1) {
                // double check
                if (paragraph.sentences.get(info.sentIndex).getSubTokensString(info.beginToken, info.endToken).equals(corefText)) {
                    infos.add(info);
                }
            }
        }
        return infos;
    }

    @Override
    public boolean process(Paragraph paragraph) {
        Object data;
        if (useAllenCoref) {
            JSONArray sents = new JSONArray();
            for (Sentence s : paragraph.sentences) {
                JSONArray sent = new JSONArray();
                for (Token t : s.tokens) {
                    sent.put(t.str);
                }
                sents.put(sent);
            }
            data = sents;
        } else {
            data = paragraph.toString();
        }
        String corefInfo = callCorefService(data);
        if (corefInfo == null) {
            System.err.println("[CoreferenceTaggingNodeError]\t" + paragraph.sentences.get(0).source);
            // Coref error, but we still continue without it.
            return true;
        }
        JSONArray clusters = new JSONArray(corefInfo);
        for (int i = 0; i < clusters.length(); ++i) {
            JSONArray cls = clusters.getJSONArray(i);
            ArrayList<CorefMentionInfo> infos = analyzeCorefMentionInfo(paragraph, cls);
            // now get a set of candidate entities from explicit given entities.

            HashMap<String, Double> entitySet = new HashMap<>();
            int referSentIndex = -1;
            for (CorefMentionInfo info : infos) {
                Sentence sent = paragraph.sentences.get(info.sentIndex);
                for (EntityTag t : sent.entityTags) {
                    // Allow an additional "the" before the entity.
                    if (t.endIndex == info.endToken && (t.beginIndex == info.beginToken || (t.beginIndex == info.beginToken + 1
                            && sent.tokens.get(info.beginToken).str.equalsIgnoreCase("the")))) {
                        // use max conf
                        entitySet.put(t.id, Math.max(entitySet.getOrDefault(t.id, 0.0), t.confidence));
                        if (referSentIndex == -1 || info.sentIndex < referSentIndex) {
                            referSentIndex = info.sentIndex;
                        }
                        break;
                    }
                }
            }
            if (entitySet.size() != 1) {
                continue;
            }
            String id = entitySet.keySet().iterator().next();
            double conf = entitySet.values().iterator().next();
            String referSent = paragraph.sentences.get(referSentIndex).toString();

            // now populate
            for (CorefMentionInfo info : infos) {
                Sentence sent = paragraph.sentences.get(info.sentIndex);

                // Check for any overlap tag
                boolean toBeAdded = true;
                for (int j = 0; j < sent.entityTags.size(); ++j) {
                    EntityTag t = sent.entityTags.get(j);
                    if (t.beginIndex < info.endToken && info.beginToken < t.endIndex) {
                        toBeAdded = false;
                        break;
                    }
                }
                if (toBeAdded) {
                    // put with same conf
                    sent.entityTags.add(new EntityTag(info.beginToken, info.endToken, id, conf, referSentIndex != info.sentIndex ? referSent : null));
                }
            }
        }
        return true;
    }
}
