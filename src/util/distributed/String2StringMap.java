package util.distributed;

import java.util.List;

public abstract class String2StringMap {
    // return null to discard output
    public abstract List<String> map(String input);
}
