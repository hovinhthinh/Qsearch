package server.table.experimental;

import storage.table.index.TableIndex;

@Deprecated
public class QfactLight {
    // entity
    String entity;
    String entitySpan;

    String quantity;
    String quantitySpan;

    String headerContext;
    String headerUnitSpan;

    String tableId;
    TableIndex tableIndex;
    int row, eCol, qCol;
}