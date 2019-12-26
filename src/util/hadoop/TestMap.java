package util.hadoop;

public class TestMap implements String2StringMap {
    @Override
    public String map(String input) {
        return input + input;
    }
}
