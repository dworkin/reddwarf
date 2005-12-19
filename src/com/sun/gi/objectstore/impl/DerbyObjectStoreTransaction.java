package com.sun.gi.objectstore.impl;

import com.sun.gi.objectstore.Transaction;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.*;
import java.util.Map;
import java.util.HashMap;
import com.sun.gi.objectstore.DeadlockException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import com.sun.gi.objectstore.TransactionClosedException;
import java.util.Map.Entry;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DerbyObjectStoreTransaction extends ObjectStoreTransactionAbs
        implements Transaction {
    private static final boolean TTWORKAROUND = false;
   
    public DerbyObjectStoreTransaction(DerbyObjectStore ostore) {
        
        this.ostore = ostore;
        this.timestamp = timestamp;
        this.tiebreaker = tiebreaker;
        dataConn = ostore.getDataConnection();
        tstampConn = ostore.getTstampConnection();
        if (dataConn == tstampConn) {
            System.out.println(
                    "Error! Did not get unique connections for data and tstamp!");
            System.exit(1);
        }
        try {
            insertStmnt = dataConn.prepareStatement("INSERT INTO " +
                    DerbyObjectStore.OBJTBLNAME +
                    " VALUES(?,?,?)");
            updateStmnt = dataConn.prepareStatement(
                    "UPDATE " + DerbyObjectStore.OBJTBLNAME + " SET " +
                    "Object = ? WHERE APPID = ? AND ID = ?");
            insertNameStmnt = dataConn.prepareStatement(
                    "INSERT INTO " + DerbyObjectStore.NAMETBLNAME + "  VALUES (?,?,?)");
            insertTstampStmnt = dataConn.prepareStatement(
                    "INSERT INTO " + DerbyObjectStore.TSTAMPTBLNAME +
                    " VALUES (?,?,?,?,?,?)");
            destroyStmnt = dataConn.prepareStatement(
                    "DELETE FROM " + DerbyObjectStore.OBJTBLNAME +
                    " WHERE APPID = ? AND ID = ?");
            destroyNameStmnt = dataConn.prepareStatement(
                    "DELETE FROM " + DerbyObjectStore.NAMETBLNAME +
                    " WHERE APPID = ? AND ID = ?");
            destroyTstampStmnt = dataConn.prepareStatement(
                    "DELETE FROM " + DerbyObjectStore.TSTAMPTBLNAME +
                    " WHERE APPID = ? AND ID  = ?");
            peekStmnt = dataConn.prepareStatement(
                    "SELECT * FROM " + DerbyObjectStore.OBJTBLNAME + " T " +
                    "WHERE T.APPID = ? AND T.ID = ?");
            lockTstampStmnt = tstampConn.prepareStatement(
                    "SELECT * FROM " + DerbyObjectStore.TSTAMPTBLNAME + " T  " +
                    "WHERE " + "T.APPID = ? AND T.ID  = ? FOR UPDATE");
            testTstampStmnt = tstampConn.prepareStatement(
                    "SELECT * FROM " + DerbyObjectStore.TSTAMPTBLNAME + " T  " +
                    "WHERE " + "T.APPID = ? AND T.ID  = ?");
            updateTstampStmnt = tstampConn.prepareStatement(
                    "UPDATE " + DerbyObjectStore.TSTAMPTBLNAME + " SET " +
                    "TaskTstamp = ?, " +
                    "Tiebreaker = ?, " +
                    "InUse = ?, " +
                    "LockTime = ? WHERE APPID = ? AND ID = ?");
            readNameStmnt = dataConn.prepareStatement(
                    "SELECT * FROM " + DerbyObjectStore.NAMETBLNAME + " T " +
                    "WHERE T.APPID = ? AND T.NAME = ?");
            writeTstampStmnt = tstampConn.prepareStatement(
                    "INSERT INTO " + DerbyObjectStore.TSTAMPTBLNAME +
                    " VALUES (?,?,?,?,?,?)");
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}
