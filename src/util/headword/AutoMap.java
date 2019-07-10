package util.headword;

import java.util.*;

/**
 * @author ntandon
 * @version 1
 * @description this class is like C# AutoMap
 * @date Dec 8, 2011
 */
public class AutoMap<K, V> extends HashMap<K, V> {

    private static final long serialVersionUID = 1L;

    public AutoMap(int size) {
        super(size);
    }

    public AutoMap(List<K> repeatedValues, V one) {
        super();
        boolean isVInt = one.getClass().getCanonicalName().endsWith("Integer");
        for (K k : repeatedValues) {
            if (isVInt)
                addNumericValueInt(k, one);
            else
                addNumericValue(k, one);
        }
    }

    public AutoMap(List<K> kv, V one, String sep) {
        super();
        boolean isVInt = one.getClass().getCanonicalName().endsWith("Integer");
        for (K k : kv) {

            // Treat as list of values if no sep is present in kv.
            if (!k.toString().contains(sep)) {
                if (isVInt)
                    addNumericValueInt(k, one);
                else
                    addNumericValue(k, one);
                continue;
            }

            String[] kAndv = k.toString().split(sep);
            if (isVInt)
                addNumericValueInt(k, (V) (new Integer(kAndv[1])));
            else
                addNumericValueInt(k, (V) (new Double(kAndv[1])));
        }
    }

    public AutoMap() {
        super();
    }

    public AutoMap(Map<K, V> existingMap) {
        super(existingMap);
    }

    public static <T> Iterable<T> nullableIter(Iterable<T> it) {
        return it != null ? it : Collections.emptySet();
    }

    /**
     * Updates frequency of value's key in AutoMap (String, AutoMap (String,
     * Double)) <BR>
     * red,blue,yellow p1:20, p2:30, p3:5
     *
     * @param key   : e.g. red,blue,yellow
     * @param value : e.g. p3:3 or p4:10
     */
    public static void addKeyKeyNumericValue(String key,
                                             AutoMap<String, AutoMap<String, Double>> map, String patternString,
                                             double freqToAdd) {
        AutoMap<String, Double> patternFreq = new AutoMap<String, Double>();
        if (map.containsKey(key))
            patternFreq = map.get(key);
        patternFreq.addNumericValue(patternString, freqToAdd); // p3,100
        map.put(key, patternFreq);
    }

    /**
     * Updates frequency of value's key in AutoMap (String, AutoMap (String,
     * Double)) <BR>
     * red,blue,yellow p1:20, p2:30, p3:5
     *
     * @param key   : e.g. red,blue,yellow
     * @param value : e.g. p3:3 or p4:10
     */
    public static <A, B> void addKeyKeyNumericValueGeneric(A key,
                                                           AutoMap<A, AutoMap<B, Double>> map, B patternString,
                                                           double freqToAdd) {
        AutoMap<B, Double> patternFreq = new AutoMap<>();
        if (map.containsKey(key))
            patternFreq = map.get(key);
        patternFreq.addNumericValue(patternString, freqToAdd); // p3,100
        map.put(key, patternFreq);
    }

    public static <A, B extends Number> double jaccard(AutoMap<A, B> m1,
                                                       AutoMap<A, B> m2) {
        double nr = 0;
        for (A k1 : m1.keySet()) {
            if (m2.containsKey(k1)) {
                nr +=
                        m1.get(k1).doubleValue()
                                * m2.get(k1).doubleValue();
            }
        }
        return nr / (m1.frobNorm().doubleValue() * m2.frobNorm().doubleValue());
    }

    /**
     * @param m = {k1,20;k2,30}
     *          "this" map =  {k1,20;k3,10}
     *          this map is updated = {k1,40;k2,30;k3,10}
     */
    public void merge(Map<K, V> m) {
        for (Entry<K, V> e : m.entrySet())
            addNumericValue(e.getKey(), e.getValue());
    }

    public void addNumericValueLong(K patternKey, V scoreOrFreq) {
        V value = scoreOrFreq;
        if (this.containsKey(patternKey)) {
            Long temp = (Long) value + (Long) this.get(patternKey);
            this.put(patternKey, (V) temp);
        } else
            this.put(patternKey, value);
    }

    public void addNumericValueInt(K patternKey, V scoreOrFreq) {
        V value = scoreOrFreq;
        if (this.containsKey(patternKey)) {
            Integer temp = (Integer) value + (Integer) this.get(patternKey);
            this.put(patternKey, (V) temp);
        } else
            this.put(patternKey, value);
    }

    public void addNumericValue(K patternKey, V scoreOrFreq) {
        V value = scoreOrFreq;
        if (scoreOrFreq instanceof Number && this.containsKey(patternKey)) {
            Double temp =
                    ((Number) scoreOrFreq).doubleValue()
                            + ((Number) this.get(patternKey)).doubleValue();
            value = (V) temp;
        }
        this.put(patternKey, value);
    }

    /**
     * Input:
     *
     * <PRE>
     * p2,(s2,1)
     * Existing map:
     * p2,(s1,2; s3,5; s2,1)
     * Output:
     * p2 (s1,2; s3,5; s2,2)
     * Constraints:
     * this automap is (K, AutoMap< K2, V2 extends Number >)
     * </PRE>
     *
     * @param p1 , (s1, 2)
     * @return true: if adding was successful, false otherwise.
     */
    public <K2, V2 extends Number> boolean updateInternalMap(K key,
                                                             K2 keyInternal, V2 valInternal) {
        AutoMap<K2, V2> valInternalUpdated = new AutoMap<>();
        try {

            // Type check: the automap must be (K, AutoMap< K2, V2 extends
            // Number >)
            if (!this.values().getClass().getName().equals(
                    "java.util.HashMap$Values"))
                return false;

            // Fetch existing internal value and update it.
            if (this.containsKey(key))
                valInternalUpdated = (AutoMap<K2, V2>) this.get(key);
            // Add the (updated/ to add) internal value to the map.
            valInternalUpdated.addNumericValue(keyInternal, valInternal);
            this.put(key, (V) valInternalUpdated);

        } catch (Exception e) {
            // Not sucessful.
            System.err.println("AutoMap.addMapToKey could not update: "
                    + keyInternal + ", " + valInternal + "\n\t" + e.getMessage());
            return false;
        }

        return true;
    }

    public void addArrayValueNoRepeat(K key, Object value) {
        if (value == null)
            return;

        V newList = (V) new ArrayList();
        if (this.containsKey(key))
            newList = this.get(key);
        if (!((Collection) newList).contains(value))
            ((Collection) newList).add(value);
        this.put(key, newList);
    }

    public void addArrayValue(K key, Object value) {
        if (value == null)
            return;

        V newList = (V) new ArrayList();
        if (this.containsKey(key))
            newList = this.get(key);
        ((Collection) newList).add(value);
        this.put(key, newList);
    }

    public void addSetValue(K key, Object value) {
        if (value == null)
            return;

        V newList = (V) new HashSet();
        if (this.containsKey(key)) {
            newList = this.get(key);
        }
        ((Collection) newList).add(value);
        this.put(key, newList);
    }

    public List<V> getArrayValue(K key) {
        if (key == null)
            return null;

        V newList = (V) new ArrayList();
        if (this.containsKey(key))
            newList = this.get(key);
        return (List<V>) newList;
    }

    public Number sumValues() {
        double sum = 0;
        for (V value : nullableIter(this.values()))
            if (value instanceof Number)
                sum += (Double) value;
            else
                return -1;
        return sum;
    }

    public Number frobNorm() {
        double sum = 0;
        for (V value : nullableIter(this.values()))
            if (value instanceof Number)
                sum +=
                        ((Number) value).doubleValue()
                                * ((Number) value).doubleValue();
            else
                return -1;
        return Math.sqrt(sum);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.AbstractMap#toString()
     */
    @Override
    public String toString() {
        return super.toString();
    }

    public List<String> toList(String kvSep) {
        List<String> result = new ArrayList<>();
        for (java.util.Map.Entry<K, V> e : nullableIter(this.entrySet())) {
            result.add(new StringBuilder().append(e.getKey().toString())
                    .append(kvSep).append(e.getValue().toString()).toString());
        }
        return result;
    }

    public void put(String kv, String separator, int keyPosition) {
        String[] parts = kv.split(separator);
        if (parts.length == 2)
            this.put((K) parts[keyPosition], (V) parts[parts.length - 1
                    - keyPosition]);
    }

    public TreeMap<K, V> sortByValue() {
        ValueComparator<K, V> sortByNumericVal =
                new ValueComparator<K, V>(this);
        TreeMap<K, V> sortedMap = new TreeMap<K, V>(sortByNumericVal);
        sortedMap.putAll(this);
        return sortedMap;
    }

    public Map<K, V> sortByValue(int topK) {
        // A treemap would require another ValueComparator!
        // thus, just add linkedhashmap that preserves order.
        Map<K, V> topkM = new LinkedHashMap<>();
        for (java.util.Map.Entry<K, V> e : sortByValue().entrySet()) {
            if (--topK >= 0)
                topkM.put(e.getKey(), e.getValue());
        }
        return topkM;
    }

    /**
     * @return
     * @description this method returns a cloned keyset so no changes are
     * reflected in the original keyset e.g. in case of retainAll
     * operation
     * @date Dec 14, 2011
     * @author ntandon
     */
    public Set getClonedKeySet() {
        Set clonedSet = new HashSet<K>();
        for (K key : this.keySet())
            clonedSet.add(key);
        return clonedSet;
    }

}

class ValueComparator<K, V> implements Comparator {

    Map<K, V> base;

    public ValueComparator(Map<K, V> base) {
        this.base = base;
    }

    @Override
    public int compare(Object a, Object b) {
        // Imp Note: if you return 0 on compare, the map assumes this is
        // duplicate key.
        if (((Number) base.get(a)).doubleValue() <= ((Number) base.get(b))
                .doubleValue())
            return 1;
        else
            return -1;
    }
}
