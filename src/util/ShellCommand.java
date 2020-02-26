package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ShellCommand {
    public static String execute(String command) {
        StringBuilder output = new StringBuilder();
        Process p;
        try {
            String[] commands = new String[]{"/bin/sh", "-c", command};
            p = Runtime.getRuntime().exec(commands);

            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
            new Thread(() -> {
                try {
                    String line;
                    while ((line = err.readLine()) != null) {
                        // ignore err
//                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\r\n");
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }
}
