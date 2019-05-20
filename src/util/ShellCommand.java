package util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShellCommand {
    public static String execute(String command) {
        StringBuilder output = new StringBuilder();
        Process p;
        try {
            String[] commands = new String[]{"/bin/sh", "-c", command};
            p = Runtime.getRuntime().exec(commands);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\r\n");
            }
        } catch (
                Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }
}
