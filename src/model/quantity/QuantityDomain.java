package model.quantity;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QuantityDomain {
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
            {"- yards", 0.9144}
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
    public static final Map<String, Double> AREA_DOMAIN = Stream.of(new Object[][]{ // Domain added for Wikipedia
            {"square miles", 2859988.11},
            {"square mile", 2859988.11},
            {"- square - mile", 2859988.11},
            {"sq miles", 2859988.11},
            {"sq mi", 2859988.11},
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

    public static Domain getDomainFromString(String str) {
        try {
            return Domain.valueOf(str);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static double getScale(Quantity quantity) {
        if (LENGTH_DOMAIN.containsKey(quantity.unit)) {
            return LENGTH_DOMAIN.get(quantity.unit);
        }
        if (MONEY_DOMAIN.containsKey(quantity.unit)) {
            return MONEY_DOMAIN.get(quantity.unit);
        }
        if (TIME_DOMAIN.containsKey(quantity.unit)) {
            return TIME_DOMAIN.get(quantity.unit);
        }
        if (PERCENTAGE_DOMAIN.containsKey(quantity.unit)) {
            return PERCENTAGE_DOMAIN.get(quantity.unit);
        }
        if (AREA_DOMAIN.containsKey(quantity.unit)) {
            return AREA_DOMAIN.get(quantity.unit);
        }
        if (VOLUME_DOMAIN.containsKey(quantity.unit)) {
            return VOLUME_DOMAIN.get(quantity.unit);
        }
        return 1.0; // dimensionless.
    }
    // anything else is considered dimensionless

    public static Domain getDomain(Quantity quantity) {
        if (LENGTH_DOMAIN.containsKey(quantity.unit)) {
            return Domain.LENGTH;
        }
        if (MONEY_DOMAIN.containsKey(quantity.unit)) {
            return Domain.MONEY;
        }
        if (TIME_DOMAIN.containsKey(quantity.unit)) {
            return Domain.TIME;
        }
        if (PERCENTAGE_DOMAIN.containsKey(quantity.unit)) {
            return Domain.PERCENTAGE;
        }
        if (AREA_DOMAIN.containsKey(quantity.unit)) {
            return Domain.AREA;
        }
        if (VOLUME_DOMAIN.containsKey(quantity.unit)) {
            return Domain.VOLUME;
        }
        return Domain.DIMENSIONLESS;
    }

    // quantityString: "(<value>;<unit>;<resolution>)"
    public static Domain getDomain(String quantityString) {
        return getDomain(Quantity.fromQuantityString(quantityString));
    }

    public static boolean quantityMatchesDomain(Quantity quantity, Domain domain) {
        if (domain == Domain.ANY) {
            return true;
        }
        return getDomain(quantity) == domain;
    }

    // quantityString: "(<value>;<unit>;<resolution>)"
    public static boolean quantityMatchesDomain(String quantityString, Domain domain) {
        return quantityMatchesDomain(Quantity.fromQuantityString(quantityString), domain);
    }

    public static boolean unitMatchesDomain(String unit, Domain domain) {
        return quantityMatchesDomain(new Quantity(0, unit, "="), domain);
    }

    public static void main(String[] args) {
        System.out.println(getDomainFromString("MONEY"));
    }

    public enum Domain {
        ANY, LENGTH, MONEY, TIME, PERCENTAGE, AREA, VOLUME, DIMENSIONLESS
    }
}
