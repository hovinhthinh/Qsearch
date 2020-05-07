package pipeline.text.deep;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class FactExtractionLabelingClient {
    private BufferedReader in = null;
    private PrintWriter out = null;
    private Process p = null;

    public FactExtractionLabelingClient(String modelPath) {
        try {
            String[] cmd = new String[]{
                    "/bin/bash",
                    "./interactive.sh",
                    "../" + modelPath
            };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("./deep"));
            p = pb.start();
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));
            String str;
            while (!(str = in.readLine()).equals("<READY>")) {
                System.out.println(str);
            }
            System.out.println(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String input = "7 Chalmers hit out onto the green to <QUANTITY> away , but Scott ran into rough on the " +
                "fringe on the left side of the green . ||| B O O O O O O O O O O O O O O O O O O O O O O O O O";
        FactExtractionLabelingClient client = new FactExtractionLabelingClient("./deep/data/stics+nyt/length/model");
        System.out.println(client.label(input));
        System.out.println(client.label(input));
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            in.close();
        } catch (Exception e) {
        }
        try {
            out.close();
        } catch (Exception e) {
        }
        try {
            p.destroy();
        } catch (Exception e) {
        }
        super.finalize();
    }

    public synchronized String label(String inputString) {
        out.println(inputString);
        out.flush();
        try {
            return in.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
