package model.context;

import java.util.ArrayList;

public interface ContextMatcher {
    double match(ArrayList<String> queryContext, ArrayList<String> factContext);
}
