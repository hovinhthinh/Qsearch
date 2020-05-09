package server.table.experimental;

import storage.table.index.TableIndex;

@Deprecated
public class QfactLight {
    public String entity;
    public String entitySpan;

    public String quantity;
    public String quantitySpan;
    public String domain;

    public String headerContext;
    public String headerUnitSpan;

    public String tableId;
    public TableIndex tableIndex;
    public int row, eCol, qCol;
}