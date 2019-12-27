package util.hadoop;

import org.json.JSONObject;

public class TestMap implements String2StringMap {
    @Override
    public String map(String input) {
        return new JSONObject().put("input", input).toString();
    }
}
