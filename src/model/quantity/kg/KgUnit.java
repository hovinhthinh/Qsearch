package model.quantity.kg;

import model.quantity.QuantityDomain;
import util.FileUtils;
import util.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KgUnit {
    public static final String KG_UNIT_SPEC_FILE = "./resources/kgu/kg-unit-collection.json";

    // hard-coded conversion for currency (to USD)
    // TODO: remove!
    public static Map<String, Double> CURRENCY_CONVERISON_RATE = Stream.of(new Object[][]{
            {"<United_States_dollar>", 1.0}, // this is the standard
            {"<Indian_rupee>", 0.014},
            {"<Euro>", 1.2},
            {"<Pound_sterling>", 1.37},
            {"<Japanese_yen>", 0.0095},
            {"<Renminbi>", 0.15},
    }).collect(Collectors.toMap(o -> (String) o[0], o -> (Double) o[1]));

    private static Map<String, KgUnit> KG_ENTITY_2_KG_UNIT = new HashMap<>(
            Arrays.asList(Gson.fromJson(FileUtils.getContent(KG_UNIT_SPEC_FILE, "UTF-8"), KgUnit[].class))
                    .stream().collect(Collectors.toMap(k -> k.entity, v -> v))) {{
        CURRENCY_CONVERISON_RATE.forEach((k, v) -> {
            get(k).conversionToSI = v;
        });
    }};

    public static final KgUnit DIMENSIONLESS = new KgUnit() {{
        siUnit = "";
        conversionToSI = 1.0;
        measuredConcepts = new ArrayList<>() {{
            add(QuantityDomain.Domain.DIMENSIONLESS);
        }};
    }};

    public String entity; // from yago, null for dimensionless
    public String wdEntry; // from wikidata
    public ArrayList<String> measuredConcepts; // entity from yago

    public ArrayList<String> symbols;

    // conversion for SI units
    public String siUnit; // from yago
    public Double conversionToSI; // for currency, use conversionForCurrency, as this is dynamic measure

    // conversion for currency
    public boolean isCurrency;
    // not-used now
    @Deprecated
    public ArrayList<CurrencyConversionRule> conversionForCurrency;

    @Deprecated
    public static class CurrencyConversionRule {
        public String targetCurrency;
        public Double conversionFactor;
    }

    public static KgUnit getKgUnitFromEntityName(String kgEntity) {
        return KG_ENTITY_2_KG_UNIT.get(kgEntity);
    }

    public boolean isDimensionless() {
        return siUnit.equals("");
    }

    public String getSIDomain() {
        if (isCurrency || isDimensionless()) {
            return getDomain();
        } else {
            return getKgUnitFromEntityName(siUnit).getDomain();
        }
    }

    public String getDomain() {
        return measuredConcepts.get(0);
    }
}
