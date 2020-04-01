package util.distributed;

import org.json.JSONObject;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class TestMap implements String2StringMap {
    @Override
    public List<String> map(String input) {
        return Arrays.asList(new JSONObject().put("input", input).toString());
    }
}
