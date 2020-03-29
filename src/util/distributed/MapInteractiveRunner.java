package util.distributed;

import org.json.JSONArray;
import util.hadoop.String2StringMap;

import java.util.List;
import java.util.Scanner;

public class MapInteractiveRunner {
    public static final String ON_READY = "__map_ready__";
    public static final String ON_OUTPUT = "__interactive_output__";

    // args: <String2StringMapClass>
    public static void main(String[] args) {
        String2StringMap mapper;
        try {
            mapper = (String2StringMap) Class.forName(args[0]).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        System.out.println(ON_READY);
        System.out.flush();
        Scanner in = new Scanner(System.in, "UTF-8");

        String str;
        while ((str = in.nextLine()) != null) {
            List<String> output = mapper.map(str);
            JSONArray arr = new JSONArray();
            if (output != null) {
                for (String o : output) {
                    arr.put(o);
                }
            }
            System.out.println(String.format("%s\t%s", ON_OUTPUT, arr.toString()));
            System.out.flush();
        }
    }
}