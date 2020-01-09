package util.db;

import javatools.database.PostgresDatabase;
import org.postgresql.copy.CopyManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * @author hvthinh
 */
public class Database {
    private static final int FETCH_SIZE = 1024;
    private static javatools.database.Database db;
    private static CopyManager cm;

    public static ResultSet q(String sql, boolean setAutoCommit) {
        try {
            Statement st = db.getConnection().createStatement();
            st.setFetchSize(FETCH_SIZE);
            db.getConnection().setAutoCommit(setAutoCommit);
            return st.executeQuery(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static ResultSet q(String sql) {
        return q(sql, false);
    }

    public static boolean openNewConnection(String user, String password, String database, String host, String port) {
        closeConnection();
        try {
            db = new PostgresDatabase(user, password, database, host, port);
            // Turn off autocommit mode
            db.getConnection().setAutoCommit(false);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean openNewConnection(String dbName) {
        return openNewConnection("hvthinh", "AtGKs2*c", dbName, "postgres2.d5.mpi-inf.mpg.de", null);
    }

    public static boolean closeConnection() {
        try {
            if (!db.getConnection().isClosed()) {
                db.close();
            }
        } catch (Exception e) {
            return false;
        } finally {
            db = null;
            cm = null;
        }
        return true;
    }

    public static ArrayList<ArrayList<String>> select(String sql) {

        ArrayList<ArrayList<String>> data = new ArrayList<>();

        ResultSet rs = q(sql);

        try {
            int nCol = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= nCol; ++i) {
                    row.add(rs.getString(i));
                }
                data.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return data;
    }

    public static void main(String[] args) {
        openNewConnection("yibrahim");
        System.out.println(select("select count(*) from evaluation.table_data"));
    }

}