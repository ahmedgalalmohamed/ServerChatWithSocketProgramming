package org.example;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectDB {
    public Connection ConnectedDB() throws Exception {
        String query_con = "jdbc:mysql://localhost/chat";
        Connection con = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            con = DriverManager.getConnection(query_con, "root", "");
            System.out.println("Connected...");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        if (con == null) throw new Exception("disconnected");
        return con;
    }
}
