package data.wikipedia.sweble;

import de.fau.cs.osr.utils.StringTools;
import nlp.NLP;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.*;
import org.sweble.wikitext.parser.utils.StringConversionException;
import util.FileUtils;
import util.Pair;
import util.distributed.String2StringMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

interface WtNodeDeepVisitor {
    // process node n, return true to continue to visit its children
    boolean visit(WtNode n);

    static void deepVisit(WtNode n, WtNodeDeepVisitor callback) {
        if (callback.visit(n)) {
            for (WtNode c : n) {
                deepVisit(c, callback);
            }
        }
    }
}

public class WikitextTableProcessor implements String2StringMap {
    WikiConfig config = DefaultConfigEnWp.generate();
    WtEngineImpl engine = new WtEngineImpl(config);

    public void beforeMap() {
    }

    @Override
    public List<String> map(String input) {
        try {
            beforeMap();
            String[] content = input.split("\t");
            String pageTitle = StringEscapeUtils.unescapeJava(content[0]),
                    wikitext = StringEscapeUtils.unescapeJava(content[1]);
            if (wikitext.startsWith("#REDIRECT") || !wikitext.contains("wikitable")) {
                return null;
            }


            EngProcessedPage cp = engine.postprocess(new PageId(PageTitle.make(config, pageTitle), -1), wikitext, null);

            if (
                    pageTitle.equals("List of governors of Alabama")
            ) {
//                Wikitext2Html.renderToFile(pageTitle, wikitext, "Simple_Page.html");
//                System.out.println(pageTitle);
//                StringBuilder sb = new StringBuilder();
//                debugNode(cp, sb, 0);
//                System.out.println(sb);

                visit(cp, new Stack<WtNode>());

                System.exit(0);
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void processWtTable(WtNode n) {
        WtXmlAttributes tableAttrs = ((WtTable) n).getXmlAttributes();
        if (!tableAttrs.toString().contains("wikitable")) {
            return;
        }

        // Content of table
        TableBuilder builder = new TableBuilder();
        int nHeaderRows = 0;
        String caption = "";

        // caption
        List<WtNode> captionNode = getSubNodeList(n, false, "WtTableCaption");
        if (captionNode.size() > 0) {
            WtTableCaption cn = (WtTableCaption) captionNode.get(0);
            caption = getTablePartText(cn.getBody()).getString("text");
        }
        // table
        ArrayList<WtNode> rows = getSubNodeList(n, false, "WtTableRow");
        boolean bodyReached = false;
        for (int i = 0; i < rows.size(); ++i) {
            WtNode r = rows.get(i);
            ArrayList<WtNode> cols = getSubNodeList(r, false, "WtTableHeader", "WtTableCell");
            int nHeaderCells = 0;
            for (int j = 0; j < cols.size(); ++j) {
                WtNode c = cols.get(j);
                WtXmlAttributes attrs;
                WtBody body;
                if (c.getNodeName().equals("WtTableHeader")) {
                    ++nHeaderCells;
                    attrs = ((WtTableHeader) c).getXmlAttributes();
                    body = ((WtTableHeader) c).getBody();
                } else {
                    attrs = ((WtTableCell) c).getXmlAttributes();
                    body = ((WtTableCell) c).getBody();
                }

                int rowspan = 1, colspan = 1;

                for (WtNode attr : attrs) {
                    if (attr instanceof WtXmlAttribute) {
                        String name = ((WtXmlAttribute) attr).getName().getAsString().toLowerCase();
                        String value = "";
                        try {
                            value = StringTools.collapseWhitespace(config.getAstTextUtils().astToText(((WtXmlAttribute) attr).getValue())).trim();
                        } catch (StringConversionException var3) {
                        }
                        try {
                            if (name.equals("rowspan")) {
                                rowspan = Integer.parseInt(value);
                            } else if (name.equals("colspan")) {
                                colspan = Integer.parseInt(value);
                            }
                        } catch (Exception e) {
                        }
                    }
                }

                // TODO: build cell content
                JSONObject cellContent = getTablePartText(body);

                builder.addHtmlCell(i, j, rowspan, colspan, cellContent);
            }

            if (nHeaderCells == cols.size() && !bodyReached) {
                ++nHeaderRows;
            } else {
                bodyReached = true;
            }
        }
        if (builder.hasDuplicatedNode() || builder.hasBlankNode()) {
            return;
        }

        System.out.println(nHeaderRows);
        System.out.println(caption);
        System.out.println(builder.getSimplePrint());
    }

    public void visit(WtNode n, Stack<WtNode> nodeStack) {
        String nn = n.getNodeName();
        if (nn.equals("WtTable")) {
            processWtTable(n);
            return;
        }
        // Visit childs
        nodeStack.push(n);
        for (WtNode c : n) {
            visit(c, nodeStack);
        }
        nodeStack.pop();
    }

    public void debugNode(WtNode n, StringBuilder sb, int nIndent) {
        for (int i = 0; i < nIndent; ++i) {
            sb.append("\t");
        }
        sb.append(n.getNodeName());
        List<String> fullPrintNodes = Arrays.asList(
                "WtXmlAttributes",
                "WtInternalLink",
                "WtXmlElement",
                "WtXmlEntityRef");
        if (fullPrintNodes.contains(n.getNodeName())) {
            sb.append(" : ").append(n.toString());
            sb.append("\r\n");
        } else if (n.getNodeName().equals("WtUrl")) {
            sb.append(" : ").append(((WtUrl) n).getProtocol() + ":" + ((WtUrl) n).getPath());
            sb.append("\r\n");
        } else if (n.getNodeName().equals("WtText")) {
            sb.append(" : ").append(((WtText) n).getContent());
            sb.append("\r\n");
        } else {
            sb.append("\r\n");
            for (WtNode child : n) {
                debugNode(child, sb, nIndent + 1);
            }
        }
    }

    public ArrayList<WtNode> getSubNodeList(WtNode n, boolean visitOnMatch, String... subNodeNames) {
        ArrayList<WtNode> subNodes = new ArrayList<>();
        WtNodeDeepVisitor.deepVisit(n, (node) -> {
            for (String snn : subNodeNames) {
                if (node.getNodeName().equals(snn)) {
                    subNodes.add(node);
                    return visitOnMatch;
                }
            }
            return true;
        });
        return subNodes;
    }

    // concat content of all WtText sub nodes
    public String getAstText(WtNode n) {
        StringBuilder text = new StringBuilder();

        WtNodeDeepVisitor.deepVisit(n, (node) -> {
            String nn = node.getNodeName();
            if (nn.equals("WtText")) {
                text.append(((WtText) node).getContent()).append(" ");
                return false;
            }
            return true;
        });

        return NLP.stripSentence(text.toString());
    }

    // content of table part (text + entity links), apply for WtTableHeader & WtTableCell & WtTableCaption
    public JSONObject getTablePartText(WtNode n) {
        JSONArray entityLinks = new JSONArray();
        StringBuilder content = new StringBuilder();

        WtNodeDeepVisitor.deepVisit(n, (node) -> {
            String nn = node.getNodeName();
            if (nn.equals("WtText")) {
                content.append(((WtText) node).getContent()).append(" ");
                return false;
            }
            if (nn.equals("WtInternalLink")) {
                Pair<String, String> link = getInternalLinkContent((WtInternalLink) node);
                content.append(link.second).append(" ");
                entityLinks.put(new JSONObject()
                        .put("linkType", "INTERNAL")
                        .put("surface", NLP.stripSentence(link.second))
                        .put("target", new JSONObject().put("title", link.first.replace(' ', '_')))
                );
                return false;
            }
            if (nn.equals("WtXmlEntityRef")) {
                content.append(((WtXmlEntityRef) node).getResolved()).append(" ");
                return false;
            }
            if (nn.equals("WtImageLink") || nn.equals("WtTemplate") || nn.equals("WtXmlAttributes")) {
                return false;
            }
            return true;
        });

        return new JSONObject()
                .put("text", NLP.stripSentence(content.toString()))
                .put("surfaceLinks", entityLinks);
    }

    // return Pair <target, surface>
    public Pair<String, String> getInternalLinkContent(WtInternalLink link) {
        String target = getAstText(link.getTarget());
        String surface = getAstText(link.getTitle());
        if (surface.isEmpty()) {
            surface = target;
        }
        return new Pair<>(target, surface);
    }

    public static void main(String[] args) {
        WikitextTableProcessor processor = new WikitextTableProcessor();
        args = "/local/home/hvthinh/datasets/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.wikitext.gz".split(" ");

        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            processor.map(line);
        }
    }
}
