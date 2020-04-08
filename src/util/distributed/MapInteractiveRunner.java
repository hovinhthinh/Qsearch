package util.distributed;

import org.json.JSONArray;

import java.util.List;
import java.util.Scanner;

public class MapInteractiveRunner {
    public static final String ON_READY = "__map_ready__";
    public static final String ON_OUTPUT = "__interactive_output__";
    public static final String ON_FAIL = "__fail_input__";
    public static final String ON_KEEP_ALIVE = "__im_alive__";

    public static final int KEEP_ALIVE_INTERVAL = 10;

    // args: <String2StringMapClass>
    public static void main(String[] args) {
        String2StringMap mapper;
        try {
            mapper = (String2StringMap) Class.forName(args[0]).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        System.out.println();
        System.out.println(ON_READY);
        System.out.flush();

        Thread keepAlive = new Thread(() -> {
            do {
                try {
                    Thread.sleep(KEEP_ALIVE_INTERVAL * 1000);
                } catch (InterruptedException e) {
                    break;
                }
                synchronized (System.out) {
                    System.out.println(ON_KEEP_ALIVE);
                    System.out.flush();
                }
            } while (!Thread.currentThread().isInterrupted());
        });
        keepAlive.start();

        try {
            Scanner in = new Scanner(System.in, "UTF-8");

            String str;
            while ((str = in.nextLine()) != null) {
                List<String> output = null;
                try {
                    output = mapper.map(str);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(String.format("%s\t%s", ON_FAIL, str));
                }

                JSONArray arr = new JSONArray();
                if (output != null) {
                    for (String o : output) {
                        arr.put(o);
                    }
                }
                synchronized (System.out) {
                    System.out.println();
                    System.out.println(String.format("%s\t%s", ON_OUTPUT, arr.toString()));
                    System.out.flush();
                }
            }
        } finally {
            keepAlive.interrupt();
        }
    }
}
