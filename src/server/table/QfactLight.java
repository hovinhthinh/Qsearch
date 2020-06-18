package server.table;

public class QfactLight implements Cloneable {
    public String entity;
    public String entitySpan;

    public String quantity;
    public String quantitySpan;
    public String domain;

    public String headerContext;
    public String headerUnitSpan;

    public String tableId;
    public int row, eCol, qCol;

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}