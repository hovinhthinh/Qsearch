package data;

import org.json.JSONException;
import org.json.JSONObject;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;

public class AddIdToTables {
    // args: <table_file(json)> <prefix> <output_file>
    public static void main(String[] args) {
//        args = "".split(" ");

        SelfMonitor monitor = new SelfMonitor("AddIdToTables", -1, 10);
        String prefix = args[1];

        PrintWriter out = FileUtils.getPrintWriter(args[2], "UTF-8");
        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            int count = monitor.incAndGet();
            try {
                JSONObject o = new JSONObject(line);
                o.put("_id", prefix + ":" + count);
                out.println(o.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        out.close();
        monitor.forceShutdown();
    }

}
