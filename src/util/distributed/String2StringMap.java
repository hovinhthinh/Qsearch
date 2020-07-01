package util.distributed;

import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.util.List;

public abstract class String2StringMap {
    public void before() {

    }

    public void after() {

    }

    // return null to discard output
    public abstract List<String> map(String input);

    // set main class manually if it is not detected automatically.
    public static String MAIN_CLASS = null;
    // args: <input> <output>
    public static String[] ARGS = null;

    public static void main(String[] args) {
        if (MAIN_CLASS == null) {
            MAIN_CLASS = System.getProperty("sun.java.command");
        }
        if (MAIN_CLASS == null) {
            System.err.println("Cannot determine main class.");
            return;
        }
        if (ARGS == null) {
            System.err.println("ARGS is null.");
            return;
        }

        String2StringMap mapper = null;
        try {
            mapper = (String2StringMap) Class.forName(MAIN_CLASS).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        PrintWriter out = FileUtils.getPrintWriter(ARGS[1], "UTF-8");
        SelfMonitor m = new SelfMonitor(MAIN_CLASS, -1, 60);
        m.start();
        try {
            for (String line : FileUtils.getLineStream(ARGS[0], "UTF-8")) {
                try {
                    List<String> result = mapper.map(line);
                    if (result != null) {
                        for (String r : result) {
                            out.println(r);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(String.format("%s\t%s", MapInteractiveRunner.ON_FAIL, line));
                }
                m.incAndGet();
            }
        } finally {
            m.forceShutdown();
            out.close();
        }
    }
}
