package com.sun.gi.objectstore.hadbtest;

import java.sql.*;

public class TestGetTables {
    public static void main (String[] args) throws Exception {
	Class.forName ("com.sun.hadb.jdbc.Driver");
	Connection con = DriverManager.getConnection(
		"jdbc:sun:hadb:129.148.75.63:15025,129.148.75.60:15005",
		"system", "darkstar");
	Statement stmt = con.createStatement();
	try {
	    stmt.executeUpdate("DROP TABLE MYSC.FOO");
	} catch (SQLException e1) {}
	try {
	    stmt.executeUpdate ("DROP SCHEMA MYSC");
	} catch (SQLException e2) {}
	stmt.executeUpdate ("CREATE SCHEMA MYSC");
	stmt.executeUpdate ("CREATE TABLE MYSC.FOO " +
		"(id1 int primary key, s char(10))");

	DatabaseMetaData meta = con.getMetaData();
	ResultSet rs = meta.getTables(null, "mysc", "foo", null);
	while (rs.next()) {
	    System.out.println (rs.getString("TABLE_NAME") + " " +
	    rs.getString("TABLE_TYPE"));
	}
	con.close();
    }
}

