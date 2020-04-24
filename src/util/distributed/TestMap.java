package util.distributed;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public class TestMap extends String2StringMap {
    @Override
    public List<String> map(String input) {
        String o1 = new JSONObject().put("input", input).toString();
        String o2 = new JSONObject().put("input_repeated", input).toString();
//        System.err.println(o1);
        return Arrays.asList(o1, o2);
    }
}
