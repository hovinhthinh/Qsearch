package model.quantity;

import catalog.QuantityCatalog;
import catalog.Unit;
import model.quantity.kg.KgUnit;
import nlp.NLP;
import org.junit.Assert;
import org.w3c.dom.Element;
import util.FileUtils;

import java.util.*;

public class QuantityDomain {
    public static final String QUTREE_2_KG_UNIT_MAPPING_FILE = "./resources/kgu/QuTreeUnitName2KgUnit.tsv";
    public static final QuantityCatalog QUANTITY_CATALOG;

    private static Map<String, ArrayList<Unit>> SURFACE_UNITS_MAP;

    private static Map<String, KgUnit> QUTREE_UNITNAME_2_KG_UNIT;

    private static KgUnit getKgUnitForQuTreeUnit(Unit u) {
        String key = String.format("%s\t%s", NLP.stripSentence(u.getParentQuantity().getConcept()), NLP.stripSentence(u.getName()));
        return QUTREE_UNITNAME_2_KG_UNIT.getOrDefault(key, KgUnit.DIMENSIONLESS);
    }

    static {
        try {
            // load mapping between qutree and kg
            QUTREE_UNITNAME_2_KG_UNIT = new HashMap<>();
            for (String line : FileUtils.getLineStream(QUTREE_2_KG_UNIT_MAPPING_FILE, "UTF-8")) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] arr = line.split("\t");
                if (arr[2].equals("null")) {
                    continue;
                }
                KgUnit kgu = KgUnit.getKgUnitFromEntityName(arr[2]);
                Assert.assertNotNull("Unit not found: " + arr[2], kgu);
                QUTREE_UNITNAME_2_KG_UNIT.put(String.format("%s\t%s", NLP.stripSentence(arr[0]), NLP.stripSentence(arr[1])), kgu);
            }

            // load QuTree catalog
            QUANTITY_CATALOG = new QuantityCatalog((Element) null);
            SURFACE_UNITS_MAP = new HashMap<>();
            for (catalog.Quantity q : QUANTITY_CATALOG.getQuantities()) {
                if (q.isUnitLess()) {
                    continue;
                }
                for (Unit u : q.getUnits()) {
                    Set<String> surfaceForms = new HashSet<>();
                    for (String s : u.getBaseSymbols()) {
                        surfaceForms.add(NLP.stripSentence(s));
                    }
                    for (String s : u.getBaseNames()) {
                        surfaceForms.add(NLP.stripSentence(s));
                    }
                    for (String s : u.getLemmas()) {
                        surfaceForms.add(NLP.stripSentence(s));
                    }
                    for (String s : surfaceForms) {
                        if (s.isEmpty()) {
                            continue;
                        }
                        if (!SURFACE_UNITS_MAP.containsKey(s)) {
                            SURFACE_UNITS_MAP.put(s, new ArrayList<>());
                        }
                        SURFACE_UNITS_MAP.get(s).add(u);
                    }
                }
            }
            for (Map.Entry<String, ArrayList<Unit>> e : SURFACE_UNITS_MAP.entrySet()) {
                ArrayList<Unit> units = e.getValue();
                if (units.size() > 1) {
                    Unit standardUnit = QUANTITY_CATALOG.getUnitFromBaseName(e.getKey());
                    if (standardUnit != null) {
                        // if standard unit can be found from base name, then use it.
                        units.clear();
                        units.add(standardUnit);
                    }
                    // else we keep all candidate units, however we should use the first one.
                }

                // move the first KG-mapped one to the first position.
                for (int i = 0; i < units.size(); ++i) {
                    Unit u = units.get(i);
                    if (!getKgUnitForQuTreeUnit(u).isDimensionless()) {
                        units.set(i, units.get(0));
                        units.set(0, u);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static final Map<String, String> UNIT_STR_2_KG_UNIT = new HashMap<>() {{
        for (Object[] entry : new Object[][]{
                // LENGTH
                {"<Mile>", new String[]{"miles", "mile"}},
                {"<Foot_(unit)>", new String[]{"feet", "ft"}},
                {"<Centimetre>", new String[]{"cm"}},
                {"<Kilometre>", new String[]{"km", "kilometres", "kilometers", "kilometre", "kilometer"}},
                {"<Metre>", new String[]{"metres", "meters", "metre", "meter"}}, // this is the standard
                {"<Inch>", new String[]{"inches", "inch"}},
                {"<Yard>", new String[]{"yards", "- yards", "- yard"}},
                // MONEY
                {"<United_States_dollar>", new String[]{"usd", "US$"}}, // this is the standard
                {"<Euro>", new String[]{"euros", "euro", "eur"}},
                {"<Pound_sterling>", new String[]{"pound", "pounds", "gbp"}},
                {"<Japanese_yen>", new String[]{"yen"}},
                {"<Renminbi>", new String[]{"yuan"}},
                // TIME
                {"<Common_year>", new String[]{"years", "year", "- year"}},
                {"<Day>", new String[]{"days", "day"}},
                {"<Week>", new String[]{"weeks", "week"}},
                {"<Hour>", new String[]{"hours", "hour"}},
                {"<Minute>", new String[]{"minutes", "minute", "- minutes"}},
                {"<Second>", new String[]{"seconds", "second"}}, // this is the standard
                // PERCENTAGE
                {"<Percentage>", new String[]{"percent", "per cent"}},
                // MASS
                {"<Kilogram>", new String[]{"kilogram"}}, // this is the standard
                {"<Tonne>", new String[]{"tonnes"}},
                // AREA
                {"<Square_mile>", new String[]{"square miles", "square mile", "- square - mile", "mi 2", "sq miles", "sq mi", "sqmi"}},
                {"<Square_foot>", new String[]{"square feet", "square foot", "- square - foot", "square ft", "sq ft"}},
                {"<Square_centimetre>", new String[]{"square centimetres", "square centimeters", "square cm", "sq cm"}},
                {"<Acre>", new String[]{"acres"}},
                {"<Hectare>", new String[]{"hectares", "ha"}},
                {"<Are_(unit)>", new String[]{"ares"}},
                {"<Square_kilometre>", new String[]{"square kilometres", "square kilometers", "square kilometre", "square kilometer", "square km", "sq km", "sqkm", "km²", "km2", "km 2", "km ^ 2"}},
                {"<Square_metre>", new String[]{"square metres", "square meters", "- square - meter", "square metre", "square meter", "sq m"}}, // this is the standard
                {"<Square_inch>", new String[]{"square inches", "square inch", "sq in"}},
                {"<Square_yard>", new String[]{"square yards", "square yard", "square yds", "square yd", "sq yd"}},
                // VOLUME
                {"<Cubic_mile>", new String[]{"cubic miles", "cubic mile", "cu mi"}},
                {"<Cubic_foot>", new String[]{"cubic feet", "cubic foot", "cubic ft", "cu ft"}},
                {"<Cubic_centimetre>", new String[]{"cubic centimetres", "cubic centimeters", "cubic cm", "cu cm"}},
                {"<cubic_kilometre_wd:Q4243638>", new String[]{"cubic kilometres", "cubic kilometers", "cubic kilometre", "cubic kilometer", "cubic km", "cu km"}},
                {"<Cubic_metre>", new String[]{"cubic metres", "cubic meters", "cubic metre", "cubic meter", "cu m"}}, // this is the standard
                {"<Cubic_inch>", new String[]{"cubic inches", "cubic inch", "cu in"}},
                {"<Cubic_yard>", new String[]{"cubic yards", "cubic yard", "cubic yds", "cubic yd", "cu yd"}},
        }) {
            for (String unitStr : (String[]) entry[1]) {
                put(unitStr, (String) entry[0]);
            }
        }
    }};

    // units are tokenized during preprocessing, e.g., "km/h" -> "km / h", this function reverts this action.
    private static String untokenizeUnit(String unit) {
        return unit.replace(" / ", "/");
        // TODO: more;
    }

    public static KgUnit getKgUnitFromUnitStr(String unit) {
        unit = untokenizeUnit(unit);
        String kge;
        if ((kge = UNIT_STR_2_KG_UNIT.get(unit)) != null) {
            return KgUnit.getKgUnitFromEntityName(kge);
        }
        // Now use QuTree.
        try {
            Unit u = unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(unit);
                u = units == null ? null : units.get(0);
            }
            if (u != null) {
                return getKgUnitForQuTreeUnit(u);
            }
        } catch (Exception e) {
        }
        return KgUnit.DIMENSIONLESS;
    }

    public static String getDomainOfUnit(String unit) {
        return getKgUnitFromUnitStr(unit).getDomain();
    }

    public static String getSearchDomainOfUnit(String unit) {
        KgUnit kgu = getKgUnitFromUnitStr(unit);
        String siDomain = kgu.getSIDomain();
        return kgu.conversionToSI != null && Domain.SEARCHABLE_SPECIFIC_DOMAINS.contains(siDomain)
                ? siDomain : Domain.DIMENSIONLESS;
    }

    public static void main(String[] args) {
        System.out.println(Domain.ANY);
        System.out.println(Domain.LENGTH);
        System.out.println(Domain.MONEY);
        System.out.println(Domain.TIME);
        System.out.println(Domain.PERCENTAGE);
        System.out.println(Domain.MASS);
        System.out.println(Domain.AREA);
        System.out.println(Domain.VOLUME);
        System.out.println(Domain.DIMENSIONLESS);
        System.out.println(Domain.SPEED);
        System.out.println(Domain.POWER);
    }

    public static class Domain {
        public static final String ANY = "ANY";
        public static final String DIMENSIONLESS = "Dimensionless";

        public static final String LENGTH = KgUnit.getKgUnitFromEntityName("<Metre>").getSIDomain();
        public static final String MONEY = KgUnit.getKgUnitFromEntityName("<United_States_dollar>").getSIDomain();
        public static final String TIME = KgUnit.getKgUnitFromEntityName("<Second>").getSIDomain();
        public static final String PERCENTAGE = KgUnit.getKgUnitFromEntityName("<Percentage>").getSIDomain();
        public static final String MASS = KgUnit.getKgUnitFromEntityName("<Kilogram>").getSIDomain();
        public static final String AREA = KgUnit.getKgUnitFromEntityName("<Square_metre>").getSIDomain();
        public static final String VOLUME = KgUnit.getKgUnitFromEntityName("<Cubic_metre>").getSIDomain();
        public static final String SPEED = KgUnit.getKgUnitFromEntityName("<Metre_per_second>").getSIDomain();
        public static final String POWER = KgUnit.getKgUnitFromEntityName("<Watt>").getSIDomain();

        public static final Set<String> SEARCHABLE_SPECIFIC_DOMAINS = Set.of(
                LENGTH, MONEY, TIME, PERCENTAGE, MASS, AREA, VOLUME, SPEED, POWER
        );
    }
}
