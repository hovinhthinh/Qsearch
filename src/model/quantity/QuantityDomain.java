package model.quantity;

import catalog.QuantityCatalog;
import catalog.Unit;
import nlp.NLP;
import org.w3c.dom.Element;

import java.util.*;

public class QuantityDomain {
    public static final QuantityCatalog QUANTITY_CATALOG;

    private static Map<String, List<Unit>> SURFACE_UNITS_MAP;

    static {
        try {
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
                            SURFACE_UNITS_MAP.put(s, new LinkedList<>());
                        }
                        SURFACE_UNITS_MAP.get(s).add(u);
                    }
                }
            }
            for (Map.Entry<String, List<Unit>> e : SURFACE_UNITS_MAP.entrySet()) {
                if (e.getValue().size() > 1) {
                    Unit standardUnit = QUANTITY_CATALOG.getUnitFromBaseName(e.getKey());
                    if (standardUnit != null) {
                        // if standard unit can be found from base name, then use it.
                        e.getValue().clear();
                        e.getValue().add(standardUnit);
                    }
                    // else we keep all candidate units, however we should use the first one.
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Double> createDomainMap(Object[][] data) {
        Map<String, Double> dm = new HashMap<>();
        for (Object[] entry : data) {
            for (int i = 0; i < entry.length - 1; ++i) {
                dm.put((String) entry[i], (Double) entry[entry.length - 1]);
            }
        }
        return dm;
    }

    public static final Map<String, Double> LENGTH_DOMAIN = createDomainMap(new Object[][]{
            {"miles", "mile", 1609.344},
            {"feet", "ft", 0.3048},
            {"cm", 0.01},
            {"km", "kilometres", "kilometers", "kilometre", "kilometer", 1000.0},
            {"metres", "meters", "metre", "meter", 1.0}, // this is the standard
            {"inches", "inch", 0.0254},
            {"yards", "- yards", "- yard", 0.9144},
    });
    public static final Map<String, Double> MONEY_DOMAIN = createDomainMap(new Object[][]{
            {"usd", "US$", 1.0}, // this is the standard
            {"euros", "euro", "eur", 1.15},
            {"pound", "pounds", "gbp", 1.3},
            {"yen", 0.009},
            {"yuan", 0.15}
    });
    public static final Map<String, Double> TIME_DOMAIN = createDomainMap(new Object[][]{
            {"decades", "decade", 3600.0 * 24 * 365 * 10},
            {"years", "year", "- year", 3600.0 * 24 * 365},
            {"months", "month", 3600.0 * 24 * 30},
            {"days", "day", 3600.0 * 24},
            {"weeks", "week", 3600.0 * 24 * 7},
            {"hours", "hour", 3600.0},
            {"minutes", "minute", "- minutes", 60.0},
            {"seconds", "second", 1.0}, // this is the standard
    });
    public static final Map<String, Double> PERCENTAGE_DOMAIN = createDomainMap(new Object[][]{
            {"percent", "per cent", 1.0},
    });
    public static final Map<String, Double> MASS_DOMAIN = createDomainMap(new Object[][]{
            {"kilogram", 1.0}, // this is the standard
            {"tonnes", 1000.0}
    });
    public static final Map<String, Double> AREA_DOMAIN = createDomainMap(new Object[][]{ // Domain added for Wikipedia
            {"square miles", "square mile", "- square - mile", "mi 2", "sq miles", "sq mi", "sqmi", 2859988.11},
            {"square feet", "square foot", "- square - foot", "square ft", "sq ft", 0.0929},
            {"square centimetres", "square centimeters", "square cm", "sq cm", 0.0001},
            {"acres", 4048.0},
            {"hectares", "ha", 10000.0},
            {"ares", 100.0},
            {"square kilometres", "square kilometers", "square kilometre", "square kilometer", "square km", "sq km", "sqkm", "km²", "km2", "km 2", "km ^ 2", 1000000.0},
            {"square metres", "square meters", "- square - meter", "square metre", "square meter", "sq m", 1.0}, // this is the standard
            {"square inches", "square inch", "sq in", 0.000645},
            {"square yards", "square yard", "square yds", "square yd", "sq yd", 0.836},
    });
    public static final Map<String, Double> VOLUME_DOMAIN = createDomainMap(new Object[][]{ // Domain added for Wikipedia
            {"cubic miles", "cubic mile", "cu mi", 4168181825.44},
            {"cubic feet", "cubic foot", "cubic ft", "cu ft", 0.0028},
            {"cubic centimetres", "cubic centimeters", "cubic cm", "cu cm", 0.0000001},
            {"cubic kilometres", "cubic kilometers", "cubic kilometre", "cubic kilometer", "cubic km", "cu km", 1000000000.0},
            {"cubic metres", "cubic meters", "cubic metre", "cubic meter", "cu m", 1.0}, // this is the standard
            {"cubic inches", "cubic inch", "cu in", 0.0000163},
            {"cubic yards", "cubic yard", "cubic yds", "cubic yd", "cu yd", 0.764},
    });

    // units are tokenized during preprocessing, e.g., "km/h" -> "km / h", this function reverts this action.
    private static String untokenizeUnit(String unit) {
        return unit.replace(" / ", "/");
        // TODO: more;
    }

    public static double getScale(Quantity quantity) {
        if (quantity.scale != null) {
            return quantity.scale;
        }
        String unit = untokenizeUnit(quantity.unit);
        if (LENGTH_DOMAIN.containsKey(unit)) {
            quantity.scale = LENGTH_DOMAIN.get(unit);
            return LENGTH_DOMAIN.get(unit);
        }
        if (MONEY_DOMAIN.containsKey(unit)) {
            quantity.scale = MONEY_DOMAIN.get(unit);
            return MONEY_DOMAIN.get(unit);
        }
        if (TIME_DOMAIN.containsKey(unit)) {
            quantity.scale = TIME_DOMAIN.get(unit);
            return TIME_DOMAIN.get(unit);
        }
        if (MASS_DOMAIN.containsKey(unit)) {
            quantity.scale = MASS_DOMAIN.get(unit);
            return MASS_DOMAIN.get(unit);
        }
        if (PERCENTAGE_DOMAIN.containsKey(unit)) {
            quantity.scale = PERCENTAGE_DOMAIN.get(unit);
            return PERCENTAGE_DOMAIN.get(unit);
        }
        if (AREA_DOMAIN.containsKey(unit)) {
            quantity.scale = AREA_DOMAIN.get(unit);
            return AREA_DOMAIN.get(unit);
        }
        if (VOLUME_DOMAIN.containsKey(unit)) {
            quantity.scale = VOLUME_DOMAIN.get(unit);
            return VOLUME_DOMAIN.get(unit);
        }
        // Now use QuTree.
        try {
            Unit u = unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(unit);
                u = units == null ? null : units.get(0);
            }
            if (u != null) {
                String domain = u.getParentQuantity().getConcept();
                // allows only these specific domains
                if (domain.equals(Domain.LENGTH) ||
                        domain.equals(Domain.MONEY) ||
                        domain.equals(Domain.TIME) ||
                        domain.equals(Domain.PERCENTAGE) ||
                        domain.equals(Domain.MASS) ||
                        domain.equals(Domain.AREA) ||
                        domain.equals(Domain.SPEED) ||
                        domain.equals(Domain.POWER) ||
                        domain.equals(Domain.VOLUME)
                ) {
                    return quantity.scale = u.getMultiplier();
                }
            }
        } catch (Exception e) {
        }
        quantity.scale = 1.0;
        return 1.0; // dimensionless.
    }

    public static String getFineGrainedDomain(Quantity quantity) {
        if (quantity.fineGrainedDomain != null) {
            return quantity.fineGrainedDomain;
        }
        return quantity.fineGrainedDomain = getFineGrainedDomainOfUnit(quantity.unit);
    }

    public static String getFineGrainedDomainOfUnit(String unit) {
        unit = untokenizeUnit(unit);
        if (LENGTH_DOMAIN.containsKey(unit)) {
            return Domain.LENGTH;
        }
        if (MONEY_DOMAIN.containsKey(unit)) {
            return Domain.MONEY;
        }
        if (TIME_DOMAIN.containsKey(unit)) {
            return Domain.TIME;
        }
        if (MASS_DOMAIN.containsKey(unit)) {
            return Domain.MASS;
        }
        if (PERCENTAGE_DOMAIN.containsKey(unit)) {
            return Domain.PERCENTAGE;
        }
        if (AREA_DOMAIN.containsKey(unit)) {
            return Domain.AREA;
        }
        if (VOLUME_DOMAIN.containsKey(unit)) {
            return Domain.VOLUME;
        }
        // Now use QuTree.
        try {
            Unit u = unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(unit);
                u = units == null ? null : units.get(0);
            }
            if (u != null) {
                return u.getParentQuantity().getConcept();
            }
        } catch (Exception e) {
        }
        return Domain.DIMENSIONLESS;
    }

    public static String getDomain(Quantity quantity) {
        if (quantity.domain != null) {
            return quantity.domain;
        }
        return quantity.domain = getDomainOfUnit(quantity.unit);
    }

    public static String getDomainOfUnit(String unit) {
        String domain = getFineGrainedDomainOfUnit(unit);
        if (domain.equals(Domain.LENGTH) || domain.equals(Domain.MONEY) || domain.equals(Domain.TIME) ||
                domain.equals(Domain.PERCENTAGE) || domain.equals(Domain.MASS) || domain.equals(Domain.AREA) ||
                domain.equals(Domain.SPEED) || domain.equals(Domain.POWER) || domain.equals(Domain.VOLUME)) {
            return domain;
        } else {
            return Domain.DIMENSIONLESS;
        }
    }

    public static boolean quantityMatchesDomain(Quantity quantity, String domain) {
        if (domain.equals(Domain.ANY)) {
            return true;
        }
        return getDomain(quantity).equals(domain);
    }

    // quantityString: "(<value>;<unit>;<resolution>)"
    public static boolean quantityMatchesDomain(String quantityString, String domain) {
        return quantityMatchesDomain(Quantity.fromQuantityString(quantityString), domain);
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
        public static final String LENGTH = QUANTITY_CATALOG.getUnitFromBaseName("metre").getParentQuantity().getConcept();
        public static final String MONEY = QUANTITY_CATALOG.getUnitFromBaseName("United States dollar").getParentQuantity().getConcept();
        public static final String TIME = QUANTITY_CATALOG.getUnitFromBaseName("second").getParentQuantity().getConcept();
        public static final String PERCENTAGE = QUANTITY_CATALOG.getUnitFromBaseName("percent").getParentQuantity().getConcept();
        public static final String MASS = QUANTITY_CATALOG.getUnitFromBaseName("kilogram").getParentQuantity().getConcept();
        public static final String AREA = QUANTITY_CATALOG.getUnitFromBaseName("square metre").getParentQuantity().getConcept();
        public static final String VOLUME = QUANTITY_CATALOG.getUnitFromBaseName("cubic metre").getParentQuantity().getConcept();
        public static final String SPEED = QUANTITY_CATALOG.getUnitFromBaseName("kilometre per hour").getParentQuantity().getConcept();
        public static final String POWER = QUANTITY_CATALOG.getUnitFromBaseName("horsepower").getParentQuantity().getConcept();
        public static final String DIMENSIONLESS = "Dimensionless";
    }

}
