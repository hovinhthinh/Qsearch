package util.hadoop;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public class TestMap implements String2StringMap {
    @Override
    public List<String> map(String input) {
        return Arrays.asList(new JSONObject().put("input", input).toString());
    }
}
