package model.quantity.kg;

import java.util.ArrayList;

public class KgUnit {
    public String entity; // from yago, null for dimensionless
    public String wdEntry; // from wikidata
    public ArrayList<String> measuredConcepts; // entity from yago

    public ArrayList<String> symbols;

    // conversion for SI units
    public String siUnit; // from yago
    public Double conversionToSI;

    // conversion for currency
    public boolean isCurrency;
    // maybe not-used now
    public ArrayList<CurrencyConversionRule> conversionForCurrency;
    public static class CurrencyConversionRule {
        public String targetCurrency;
        public Double conversionFactor;
    }
}
