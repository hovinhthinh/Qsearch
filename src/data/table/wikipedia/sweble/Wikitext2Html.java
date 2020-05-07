/**
 * Copyright 2011 The Open Source Research Group,
 * University of Erlangen-Nürnberg
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package data.table.wikipedia.sweble;

import org.apache.commons.lang.StringEscapeUtils;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WtUrl;
import util.FileUtils;

import java.io.PrintWriter;

public class Wikitext2Html {
    static WikiConfig config = DefaultConfigEnWp.generate();
    static WtEngineImpl engine = new WtEngineImpl(config);

    public static boolean renderToFile(String pageTitle, String wikitext, String outputFile) {
        try {
            PageTitle title = PageTitle.make(config, pageTitle);
            EngProcessedPage cp =
                    engine.postprocess(new PageId(title, -1), wikitext, null);
            String html = org.sweble.wikitext.engine.output.HtmlRenderer.print(
                    new HtmlRendererCallback() {
                        @Override
                        public MediaInfo getMediaInfo(String s, int i, int i1) {
                            return null;
                        }

                        @Override
                        public boolean resourceExists(PageTitle pageTitle) {
                            return false;
                        }

                        @Override
                        public String makeUrl(PageTitle pageTitle) {
                            return null;
                        }

                        @Override
                        public String makeUrl(WtUrl wtUrl) {
                            return null;
                        }

                        @Override
                        public String makeUrlMissingTarget(String s) {
                            return null;
                        }
                    }, config, title, cp.getPage());
            PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
            out.print(html);
            out.close();
            System.out.println(WikitextTableProcessor.debugNode(cp));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        String content = FileUtils.getContent("Simple_Page.wikitext");
        renderToFile("Test", StringEscapeUtils.unescapeJava(content), "Simple_Page.html");
    }
}
