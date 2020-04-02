package data.wikipedia.sweble;

import de.fau.cs.osr.utils.StringTools;
import org.apache.commons.lang3.StringEscapeUtils;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.*;
import org.sweble.wikitext.parser.utils.StringConversionException;
import util.FileUtils;
import util.distributed.String2StringMap;

import java.util.ArrayList;
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
        
        TableBuilder builder = new TableBuilder();
        ArrayList<WtNode> rows = getListSubNode(n, false, "WtTableRow");
        for (int i = 0; i < rows.size(); ++i) {
            WtNode r = rows.get(i);
            ArrayList<WtNode> cols = getListSubNode(r, false, "WtTableHeader", "WtTableCell");
            for (int j = 0; j < cols.size(); ++j) {
                WtNode c = cols.get(j);
                WtXmlAttributes attrs;
                if (c.getNodeName().equals("WtTableHeader")) {
                    attrs = ((WtTableHeader) c).getXmlAttributes();
                } else {
                    attrs = ((WtTableCell) c).getXmlAttributes();
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

                // TODO: build content
                String content = "CELL";
//                try {
//                    content = config.getAstTextUtils().astToText();
//                } catch (StringConversionException var3) {
//                }

                builder.addHtmlCell(i, j, rowspan, colspan, content);
            }
        }
        if (builder.hasDuplicatedNode() || builder.hasBlankNode()) {
            return;
        }
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
        if (n.getNodeName().equals("WtXmlAttributes")) {
            sb.append(" : ").append(n.toString());
            sb.append("\r\n");
        } else if (n.getNodeName().equals("WtInternalLink")) {
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

    public ArrayList<WtNode> getListSubNode(WtNode n, boolean visitOnMatch, String... subNodeNames) {
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

    public static void main(String[] args) {
        WikitextTableProcessor processor = new WikitextTableProcessor();
        args = "/local/home/hvthinh/datasets/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.wikitext.gz".split(" ");

        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            processor.map(line);
        }
    }
}
