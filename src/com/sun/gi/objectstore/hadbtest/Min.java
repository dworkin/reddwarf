
package com.sun.gi.objectstore.hadbtest;

import java.sql.*;

public class Min {

    public static void main(String[] args) {
	String SCHEMA = "FOOSCHEMA";
	String OBJTBL = "FOOTABLE";

	try {
	    Class.forName("com.sun.hadb.jdbc.Driver");
	} catch (Exception e) {
	    System.out.println(e);
	}

	Connection con;
	DatabaseMetaData md;
	ResultSet rs;
	String s;
	PreparedStatement stmnt;
	try {
	    con = DriverManager.getConnection(
		    "jdbc:sun:hadb:129.148.75.63:15025,129.148.75.60:15005",
		    "system", "darkstar");
	    md = con.getMetaData();
	    rs = md.getTables(null, SCHEMA, OBJTBL, null);

	    if (rs.next()) {
		System.out.println("Found Objects table");
	    } else {
		System.out.println("Creating Schema");
		s = "CREATE SCHEMA " + SCHEMA;
		stmnt = con.prepareStatement(s);
		// stmnt.execute();

		System.out.println("Dropping Objects table");
		s = "DROP TABLE " + SCHEMA + "." + OBJTBL;
		stmnt = con.prepareStatement(s);
		stmnt.execute();

		System.out.println("Creating Objects table");
		s = "CREATE TABLE " + SCHEMA + "." + OBJTBL + " (" +
			"OBJIDHI DOUBLE INT NOT NULL, " +
			"OBJIDLO INT NOT NULL, " +
			"OBJBYTES BLOB," +
			"PRIMARY KEY (OBJIDHI, OBJIDLO))";
		stmnt = con.prepareStatement(s);
		stmnt.execute();
	    }
	} catch (Exception e) {
	    System.out.println(e);
	}
    }
}

