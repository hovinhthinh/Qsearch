package util.hadoop;

import java.util.List;

public interface String2StringMap {
    // return null to discard output
    List<String> map(String input);
}
