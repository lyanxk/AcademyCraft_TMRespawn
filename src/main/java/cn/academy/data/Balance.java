package cn.academy.data;

/** Named numeric parameters for existing skill algorithms; values are read from the active snapshot on use. */
public final class Balance {
    public static int i(String document, String key) { return GameplayData.integer("parameters/" + document, key); }
    public static long l(String document, String key) { return GameplayData.value("parameters/" + document, key).getAsLong(); }
    public static float f(String document, String key) { return GameplayData.decimal("parameters/" + document, key); }
    public static double d(String document, String key) { return GameplayData.number("parameters/" + document, key); }
    public static float af(String document, String key, int index) { return (float) GameplayData.at("parameters/" + document, key, index); }
    public static double ad(String document, String key, int index) { return GameplayData.at("parameters/" + document, key, index); }
    public static double[] array(String document, String key) {
        return GameplayData.value("parameters/" + document, key).getAsJsonArray().asList().stream().mapToDouble(com.google.gson.JsonElement::getAsDouble).toArray();
    }
    private Balance() { }
}
