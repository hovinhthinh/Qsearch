package model.quantity;

import catalog.QuantityCatalog;
import catalog.Unit;
import org.w3c.dom.Element;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                        surfaceForms.add(s.trim());
                    }
                    for (String s : u.getBaseNames()) {
                        surfaceForms.add(s.trim());
                    }
                    for (String s : u.getLemmas()) {
                        surfaceForms.add(s.trim());
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

    public static final Map<String, Double> LENGTH_DOMAIN = Stream.of(new Object[][]{
            {"miles", 1609.344},
            {"mile", 1609.344},
            {"feet", 0.3048},
            {"ft", 0.3048},
            {"km", 1000.0},
            {"cm", 0.01},
            {"kilometres", 1000.0},
            {"kilometers", 1000.0},
            {"kilometre", 1000.0},
            {"kilometer", 1000.0},
            {"metres", 1.0}, // this is the standard
            {"meters", 1.0},
            {"metre", 1.0},
            {"meter", 1.0},
            {"inches", 0.0254},
            {"inch", 0.0254},
            {"yards", 0.9144},
            {"- yards", 0.9144},
            {"- yard", 0.9144}
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));
    public static final Map<String, Double> MONEY_DOMAIN = Stream.of(new Object[][]{
            {"usd", 1.0},
            {"US$", 1.0}, // this is the standard
            {"euros", 1.13},
            {"euro", 1.13},
            {"yen", 0.009},
            {"yuan", 0.15}
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));
    // This is amount of time.
    public static final Map<String, Double> TIME_DOMAIN = Stream.of(new Object[][]{
            {"years", 3600.0 * 24 * 365},
            {"months", 3600.0 * 24 * 30},
            {"days", 3600.0 * 24},
            {"weeks", 3600.0 * 24 * 7},
            {"hours", 3600.0},
            {"minutes", 60.0},
            {"decades", 3600.0 * 24 * 365 * 10},
            {"seconds", 1.0}, // this is the standard
            {"year", 3600.0 * 24 * 365},
            {"month", 3600.0 * 24 * 30},
            {"day", 3600.0 * 24},
            {"week", 3600.0 * 24 * 7},
            {"hour", 3600.0},
            {"minute", 60.0},
            {"decade", 3600.0 * 24 * 365 * 10},
            {"second", 1.0},
            {"- year", 3600.0 * 24 * 265},
            {"- minutes", 60.0}
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));
    public static final Map<String, Double> PERCENTAGE_DOMAIN = Stream.of(new Object[][]{
            {"percent", 1.0},
            {"per cent", 1.0}
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));
    public static final Map<String, Double> MASS_DOMAIN = Stream.of(new Object[][]{
            {"kilogram", 1.0}, // this is the standard
            {"tonnes", 1000.0}
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));
    public static final Map<String, Double> AREA_DOMAIN = Stream.of(new Object[][]{ // Domain added for Wikipedia
            {"square miles", 2859988.11},
            {"square mile", 2859988.11},
            {"- square - mile", 2859988.11},
            {"sq miles", 2859988.11},
            {"sq mi", 2859988.11},
            {"sqmi", 2859988.11},
            {"square feet", 0.0929},
            {"square foot", 0.0929},
            {"- square - foot", 0.0929},
            {"square ft", 0.0929},
            {"sq ft", 0.0929},
            {"square centimetres", 0.0001},
            {"square centimeters", 0.0001},
            {"square cm", 0.0001},
            {"sq cm", 0.0001},
            {"acres", 4048.0},
            {"hectares", 10000.0},
            {"ha", 10000.0},
            {"ares", 100.0},
            {"square kilometres", 1000000.0},
            {"square kilometers", 1000000.0},
            {"square kilometre", 1000000.0},
            {"square kilometer", 1000000.0},
            {"square km", 1000000.0},
            {"sq km", 1000000.0},
            {"sqkm", 1000000.0},
            {"km²", 1000000.0},
            {"km2", 1000000.0},
            {"km ^ 2", 1000000.0},
            {"square metres", 1.0}, // this is the standard
            {"square meters", 1.0},
            {"- square - meter", 1.0},
            {"square metre", 1.0},
            {"square meter", 1.0},
            {"sq m", 1.0},
            {"square inches", 0.000645},
            {"square inch", 0.000645},
            {"sq in", 0.000645},
            {"square yards", 0.836},
            {"square yard", 0.836},
            {"square yds", 0.836},
            {"square yd", 0.836},
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));
    public static final Map<String, Double> VOLUME_DOMAIN = Stream.of(new Object[][]{ // Domain added for Wikipedia
            {"cubic miles", 4168181825.44},
            {"cubic mile", 4168181825.44},
            {"cu mi", 4168181825.44},
            {"cubic feet", 0.0028},
            {"cubic foot", 0.0028},
            {"cubic ft", 0.0028},
            {"cu ft", 0.0028},
            {"cubic centimetres", 0.0000001},
            {"cubic centimeters", 0.0000001},
            {"cubic cm", 0.0000001},
            {"cu cm", 0.0000001},
            {"cubic kilometres", 1000000000.0},
            {"cubic kilometers", 1000000000.0},
            {"cubic kilometre", 1000000000.0},
            {"cubic kilometer", 1000000000.0},
            {"cubic km", 1000000000.0},
            {"cu km", 1000000000.0},
            {"cubic metres", 1.0}, // this is the standard
            {"cubic meters", 1.0},
            {"cubic metre", 1.0},
            {"cubic meter", 1.0},
            {"cu m", 1.0},
            {"cubic inches", 0.0000163},
            {"cubic inch", 0.0000163},
            {"cu in", 0.0000163},
            {"square yards", 0.764},
            {"square yard", 0.764},
            {"square yds", 0.764},
            {"square yd", 0.764},
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));

    public static double getScale(Quantity quantity) {
        if (quantity.scale != null) {
            return quantity.scale;
        }
        if (LENGTH_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = LENGTH_DOMAIN.get(quantity.unit);
            return LENGTH_DOMAIN.get(quantity.unit);
        }
        if (MONEY_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = MONEY_DOMAIN.get(quantity.unit);
            return MONEY_DOMAIN.get(quantity.unit);
        }
        if (TIME_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = TIME_DOMAIN.get(quantity.unit);
            return TIME_DOMAIN.get(quantity.unit);
        }
        if (MASS_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = MASS_DOMAIN.get(quantity.unit);
            return MASS_DOMAIN.get(quantity.unit);
        }
        if (PERCENTAGE_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = PERCENTAGE_DOMAIN.get(quantity.unit);
            return PERCENTAGE_DOMAIN.get(quantity.unit);
        }
        if (AREA_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = AREA_DOMAIN.get(quantity.unit);
            return AREA_DOMAIN.get(quantity.unit);
        }
        if (VOLUME_DOMAIN.containsKey(quantity.unit)) {
            quantity.scale = VOLUME_DOMAIN.get(quantity.unit);
            return VOLUME_DOMAIN.get(quantity.unit);
        }
        // Now use QuTree.
        try {
            Unit u = quantity.unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(quantity.unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(quantity.unit);
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
                        domain.equals(Domain.VOLUME)
                ) {
                    quantity.scale = u.getMultiplier();
                    return u.getMultiplier();
                }
            }
        } catch (Exception e) {
        }
        quantity.scale = 1.0;
        return 1.0; // dimensionless.
    }
    // anything else is considered dimensionless

    public static double getFineGrainedScale(Quantity quantity) {
        if (quantity.fineGrainedScale != null) {
            return quantity.fineGrainedScale;
        }
        if (LENGTH_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = LENGTH_DOMAIN.get(quantity.unit);
            return LENGTH_DOMAIN.get(quantity.unit);
        }
        if (MONEY_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = MONEY_DOMAIN.get(quantity.unit);
            return MONEY_DOMAIN.get(quantity.unit);
        }
        if (TIME_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = TIME_DOMAIN.get(quantity.unit);
            return TIME_DOMAIN.get(quantity.unit);
        }
        if (MASS_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = MASS_DOMAIN.get(quantity.unit);
            return MASS_DOMAIN.get(quantity.unit);
        }
        if (PERCENTAGE_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = PERCENTAGE_DOMAIN.get(quantity.unit);
            return PERCENTAGE_DOMAIN.get(quantity.unit);
        }
        if (AREA_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = AREA_DOMAIN.get(quantity.unit);
            return AREA_DOMAIN.get(quantity.unit);
        }
        if (VOLUME_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedScale = VOLUME_DOMAIN.get(quantity.unit);
            return VOLUME_DOMAIN.get(quantity.unit);
        }
        // Now use QuTree.
        try {
            Unit u = quantity.unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(quantity.unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(quantity.unit);
                u = units == null ? null : units.get(0);
            }
            if (u != null) {
                quantity.fineGrainedScale = u.getMultiplier();
                return u.getMultiplier();
            }
        } catch (Exception e) {
        }
        quantity.fineGrainedScale = 1.0;
        return 1.0; // dimensionless.
    }
    // anything else is considered dimensionless

    public static String getFineGrainedDomain(Quantity quantity) {
        if (quantity.fineGrainedDomain != null) {
            return quantity.fineGrainedDomain;
        }
        if (LENGTH_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.LENGTH;
            return Domain.LENGTH;
        }
        if (MONEY_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.MONEY;
            return Domain.MONEY;
        }
        if (TIME_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.TIME;
            return Domain.TIME;
        }
        if (MASS_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.MASS;
            return Domain.MASS;
        }
        if (PERCENTAGE_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.PERCENTAGE;
            return Domain.PERCENTAGE;
        }
        if (AREA_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.AREA;
            return Domain.AREA;
        }
        if (VOLUME_DOMAIN.containsKey(quantity.unit)) {
            quantity.fineGrainedDomain = Domain.VOLUME;
            return Domain.VOLUME;
        }
        // Now use QuTree.
        try {
            Unit u = quantity.unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(quantity.unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(quantity.unit);
                u = units == null ? null : units.get(0);
            }
            if (u != null) {
                String domain = u.getParentQuantity().getConcept();
                // allows only these specific domains
                quantity.fineGrainedDomain = domain;
                return domain;
            }
        } catch (Exception e) {
        }
        quantity.fineGrainedDomain = Domain.DIMENSIONLESS;
        return Domain.DIMENSIONLESS;
    }

    public static String getDomain(Quantity quantity) {
        if (quantity.domain != null) {
            return quantity.domain;
        }
        if (LENGTH_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.LENGTH;
            return Domain.LENGTH;
        }
        if (MONEY_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.MONEY;
            return Domain.MONEY;
        }
        if (TIME_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.TIME;
            return Domain.TIME;
        }
        if (MASS_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.MASS;
            return Domain.MASS;
        }
        if (PERCENTAGE_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.PERCENTAGE;
            return Domain.PERCENTAGE;
        }
        if (AREA_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.AREA;
            return Domain.AREA;
        }
        if (VOLUME_DOMAIN.containsKey(quantity.unit)) {
            quantity.domain = Domain.VOLUME;
            return Domain.VOLUME;
        }
        // Now use QuTree.
        try {
            Unit u = quantity.unit.isEmpty() ? null : QUANTITY_CATALOG.getUnitFromBaseName(quantity.unit);
            if (u == null) {
                List<Unit> units = SURFACE_UNITS_MAP.get(quantity.unit);
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
                        domain.equals(Domain.VOLUME)
                ) {
                    quantity.domain = domain;
                    return domain;
                }
            }
        } catch (Exception e) {
        }
        quantity.domain = Domain.DIMENSIONLESS;
        return Domain.DIMENSIONLESS;
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

    public static boolean unitMatchesDomain(String unit, String domain) {
        return quantityMatchesDomain(new Quantity(0, unit, "="), domain);
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
        public static final String DIMENSIONLESS = "Dimensionless";
    }

}
