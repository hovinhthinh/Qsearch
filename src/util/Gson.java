package util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class Gson {
    private static ThreadLocal<com.google.gson.Gson> GSON_LOCAL =
            ThreadLocal.withInitial(() -> new com.google.gson.Gson());
    private static ThreadLocal<com.google.gson.Gson> GSON_LOCAL_PRETTY =
            ThreadLocal.withInitial(() -> new GsonBuilder().setPrettyPrinting().create());

    public static String toJson(Object src) {
        return toJson(src, false);
    }

    public static String toJson(Object src, boolean pretty) {
        com.google.gson.Gson g = (pretty ? GSON_LOCAL_PRETTY : GSON_LOCAL).get();
        return g.toJson(src);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        return GSON_LOCAL.get().fromJson(json, classOfT);
    }
}
