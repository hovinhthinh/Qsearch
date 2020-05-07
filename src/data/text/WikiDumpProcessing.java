package data.text;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import util.FileUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

@Deprecated
public class WikiDumpProcessing {

    public static String getStringFromDocument(Document doc) {
        try {
            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static final String nextPage(FileUtils.LineStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        String line;
        do {
            line = in.readLine();
        } while (!line.trim().equals("<page>"));
        builder.append(line).append("\r\n");
        do {
            line = in.readLine();
            builder.append(line).append("\r\n");
        } while (!line.trim().equals("</page>"));
        return builder.toString();
    }

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        FileUtils.LineStream stream = FileUtils.getLineStream("/home/hvthinh/datasets/enwiki-20180101-pages-articles" +
                ".xml.gz");
        for (int i = 0; i < 1; ++i) {
            nextPage(stream);
        }
        String page = nextPage(stream);

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(page)));
        System.out.println(getStringFromDocument(doc));
//        System.out.println(doc.getElementsByTagName("text").item(0).getTextContent());
    }
}
