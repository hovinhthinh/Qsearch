package storage.table;

import java.util.ArrayList;

public class ResultInstance {
    public String entity;
    public double score;
    public String quantity;
    public double quantityStandardValue;
    public String sentence;
    public String source;

    // For verbose
    public String entityStr;
    public String quantityStr;
    public ArrayList<String> contextStr;

    public String quantityConvertedStr;

    // For evaluation
    public String eval;

    // For table
    public int row, entityColumn, quantityColumn;
    public String tableId;
    public String[] header;
    public String[][] data;
    public String headerUnitSpan;
    public String caption, pageTitle, pageContent;
}