/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.objectstore.tso.dataspace;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 * A {@link DataSpace} based on HADB/JDBC.
 */
public class HadbDataSpace implements DataSpace {

    private static Logger log =
	    Logger.getLogger("com.sun.gi.objectstore.tso.dataspace");

    boolean debug = true;
    private long appID;
    //private String dataConnURL;

    /*
     * HADB appears to convert all table and column names to lower-case
     * internally, and some of the methods for accessing the metadata do
     * not convert, so it's easier if everything is simply lower-case to
     * begin with. (otherwise it's possible to successfully create a
     * table with a given name, and then ask whether a table with that
     * name exists, and get the answer "no") -DJE
     */

    private static final String SCHEMAROOT = "simserver";

    private static final String OBJBASETBL = "objects";
    private static final String OBJLOCKBASETBL = "objlocks";
    private static final String NAMEBASETBL = "namedirectory";
    private static final String INFOBASETBL = "appinfo";

    private final String SCHEMA;

    private String NAMETBLNAME;
    private String OBJTBLNAME;
    private String OBJLOCKTBLNAME;
    private String INFOTBLNAME;

    private Connection readConn;
    private Connection updateTransConn;
    private Connection updateSingleConn;
    private Connection idConn;
    private Connection schemaConn;

    private PreparedStatement getIdStmnt;
    private PreparedStatement getObjStmnt;
    private PreparedStatement getNameStmnt;
    private PreparedStatement updateObjStmnt;
    private PreparedStatement insertObjStmnt;
    private PreparedStatement insertNameStmnt;
    private PreparedStatement updateInfoStmnt;
    private PreparedStatement updateObjLockStmnt;
    private PreparedStatement findObjLockStmnt;
    private PreparedStatement updateObjUnlockStmnt;
    private PreparedStatement insertObjLockStmnt;
    private PreparedStatement deleteObjStmnt;
    private PreparedStatement deleteObjLockStmnt;
    private PreparedStatement deleteNameStmnt;

    private volatile boolean closed = false;

    private String hadbHosts = null;
    private String hadbUserName = "system";
    private String hadbPassword = "sungameserver";
    private String hadbDBname = null;

    /**
     * Creates a DataSpace with the given appId, connected to the
     * "default" HADB instance.
     * 
     * @param appId the application ID
     */
    public HadbDataSpace(long appId) throws Exception {
        this(appId, false);
    }

    /**
     * Creates a DataSpace with the given appId, connected to the
     * "default" HADB instance.
     * 
     * @param appID the application ID
     * 
     * @param dropTables if <code>true</code>, then drop all tables
     * before begining.
     */
    public HadbDataSpace(long appID, boolean dropTables) throws Exception {
        this.appID = appID;

        // If we can't load the driver, bail out immediately.
        try {
            Class.forName("com.sun.hadb.jdbc.Driver");
        } catch (Exception e) {
	    log.severe("ERROR: Failed to load the HADB JDBC driver: " + e);
            throw e;
        }

        if (!loadParams()) {
            throw new IllegalArgumentException("illegal parameters");
        }

        SCHEMA = SCHEMAROOT + "_" + appID;

        OBJTBLNAME = SCHEMA + "." + OBJBASETBL;
        OBJLOCKTBLNAME = SCHEMA + "." + OBJLOCKBASETBL;
        NAMETBLNAME = SCHEMA + "." + NAMEBASETBL;
        INFOTBLNAME = SCHEMA + "." + INFOBASETBL;

        try {
            String dataConnURL = "jdbc:sun:hadb:" + hadbHosts + ";create=true";

	    log.fine("dataConnURL: " + dataConnURL);

            // XXX: Do we really need all these connections?

            updateTransConn = getConnection(dataConnURL, hadbUserName,
                    hadbPassword, Connection.TRANSACTION_READ_COMMITTED);
            updateSingleConn = getConnection(dataConnURL, hadbUserName,
                    hadbPassword, Connection.TRANSACTION_READ_COMMITTED);
            updateSingleConn.setAutoCommit(true);

            idConn = getConnection(dataConnURL, hadbUserName, hadbPassword,
                    Connection.TRANSACTION_READ_COMMITTED);

            readConn = getConnection(dataConnURL, hadbUserName, hadbPassword,
                    Connection.TRANSACTION_READ_COMMITTED);
            readConn.setReadOnly(true);
            readConn.setAutoCommit(true);

            schemaConn = getConnection(dataConnURL, hadbUserName, hadbPassword,
                    Connection.TRANSACTION_REPEATABLE_READ);
            schemaConn.setAutoCommit(true);

            if (dropTables) {
                if (!dropTables()) {

                    // This isn't necessarily an error. The cases
                    // need to be subdivided more carefully.

                    log.warning("Failed to drop all tables.");
                }
            }

            checkTables();
            createPreparedStmnts();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadParams() {
        FileInputStream fis = null;
        Properties hadbParams = new Properties();
        String hadbParamFile = System.getProperty("dataspace.hadbParamFile");

        if (hadbParamFile != null) {
            try {
                fis = new FileInputStream(hadbParamFile);
            } catch (IOException e) {
                log.warning("failed to open params file ("
                        + hadbParamFile + "): " + e);
                return false;
            }

            if (fis != null) {
                try {
                    hadbParams.load(fis);
                } catch (IOException e) {
                    log.warning("failed to read params file ("
                            + hadbParamFile + "): " + e);
                    hadbParams.clear();
                    return false;
                } catch (IllegalArgumentException e) {
                    log.warning("params file (" + hadbParamFile
                            + ") contains errors: " + e);
                    hadbParams.clear();
                    return false;
                }

                try {
                    fis.close();
                } catch (IOException e) {
                    // XXX: Is this really a problem?
                }
            }
        }

        hadbHosts = hadbParams.getProperty("dataspace.hadb.hosts",
                System.getProperty("dataspace.hadb.hosts", null));
        if (hadbHosts == null) {
            hadbHosts = "129.148.75.63:15025,129.148.75.60:15005";
            hadbHosts = "20.20.10.104:15025,20.20.10.103:15085,20.20.11.107:15105,20.20.10.101:15005,20.20.11.105:15065,20.20.10.102:15045";
        }

        hadbUserName = hadbParams.getProperty("dataspace.hadb.username",
                System.getProperty("dataspace.hadb.username", "system"));

        hadbPassword = hadbParams.getProperty("dataspace.hadb.password",
                System.getProperty("dataspace.hadb.password", "darkstar"));

        // DO NOT USE: leave hadbDBname null.
        // Setting the database name to anything other than the default
        // doesn't work properly. -DJE

        hadbDBname = hadbParams.getProperty("dataspace.hadb.dbname",
                System.getProperty("dataspace.hadb.dbname", null));

        if (debug) {
            log.info("PARAM: dataspace.hadb.hosts: " + hadbHosts);
            log.info("PARAM: dataspace.hadb.username: " + hadbUserName);
            log.info("PARAM: dataspace.hadb.password: " + hadbPassword);
            log.info("PARAM: dataspace.hadb.dbname: "
                    + ((hadbDBname == null) ? "null" : hadbDBname));
        }

        return true;
    }

    private String[] getTableNames() {
        String[] tableNames = { OBJTBLNAME, OBJLOCKTBLNAME, NAMETBLNAME,
                INFOTBLNAME };
        return tableNames;
    }

    /**
     * Drops all of the tables.
     * <p>
     */
    private synchronized boolean dropTables() {

        log.info("Dropping all tables!");

        boolean allSucceeded = true;
        for (String name : getTableNames()) {
            if (!dropTable(name)) {
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    /**
     * Clears all of the tables <em>except</em> the infotbl.
     * <p>
     * 
     * The infotbl contains the generator for the next object ID.
     * clearing the infotbl can cause disasters. (clearing any of the
     * tables when the system is mid-stride can be disasterous, but
     * clearing the infotbl is almost guaranteed to be disasterous).
     */
    public synchronized boolean clearTables() {
        log.info("INFO: Clearing tables!");

        boolean allSucceeded = true;
        for (String name : getTableNames()) {
            // don't clear the INFOTBL.
            if (!name.equals(INFOTBLNAME) && !clearTable(name)) {
                allSucceeded = false;
            }
        }

        return allSucceeded;
    }

    private boolean dropTable(String tableName) {
        String s;
        Statement stmnt;

        try {
            updateTransConn.commit();
            idConn.commit();
        } catch (Exception e) {
            log.severe("ERROR: FAILED to prepare/commit " +
		    tableName + " " + e);
            e.printStackTrace();
        }

        stmnt = null;
        s = "DROP TABLE " + tableName;
        try {
            stmnt = schemaConn.createStatement();
            stmnt.execute(s);
            log.finer("Dropped " + tableName);
        } catch (SQLException e) {
            log.severe("FAILED to drop " + tableName + " " + e);
            /* e.printStackTrace(); */
            return false;
        }

        return true;
    }

    private boolean clearTable(String tableName) {
        String s;
        Statement stmnt;

        try {
            updateTransConn.commit();
            idConn.commit();
        } catch (Exception e) {
            log.severe("ERROR: FAILED to prepare/commit " +
		    tableName + " " + e);
            e.printStackTrace();
        }

        stmnt = null;
        s = "DELETE FROM " + tableName;
        try {
            stmnt = schemaConn.createStatement();
            stmnt.execute(s);
            log.fine("Deleted contents of " + tableName);
        } catch (SQLException e) {
            log.severe("FAILED to delete " + tableName + " " + e);
            /* e.printStackTrace(); */
            return false;
        }

        return true;
    }

    /**
     * @throws SQLException
     */
    private synchronized void checkTables() throws SQLException {
        ResultSet rs;
        Statement stmnt = null;
        boolean schemaExists = false;

        /*
         * It is not possible, in HADB, to bring a schema into existance
         * simply by defining tables within it. Therefore we create it
         * separately -- but this might fail, because the schema might
         * already exist. So we need to check whether it exists first,
         * and then try to create it if it doesn't, etc.
         * 
         * There's probably a cleaner way. -DJE
         */

        try {
            String s = "select schemaname from sysroot.allschemas"
                    + " where schemaname like '" + SCHEMA + "'";
            stmnt = schemaConn.createStatement();
            rs = stmnt.executeQuery(s);
            if (!rs.next()) {
		log.info("SCHEMA does not exist: " + SCHEMA);
                schemaExists = false;
            } else {
                log.fine("SCHEMA already exists: " + SCHEMA);
                schemaExists = true;
            }
            rs.close();
        } catch (SQLException e) {
            log.severe("SCHEMA error: " + e);
            schemaExists = false;
        }

        if (!schemaExists) {
            try {
                log.info("INFO: Creating Schema");
                String s = "CREATE SCHEMA " + SCHEMA;
                stmnt = schemaConn.createStatement();
                stmnt.execute(s);
            } catch (SQLException e) {

                /*
                 * 11751 is "this schema already exists"
                 */

                if (e.getErrorCode() != 11751) {
                    log.severe("SCHEMA problem: " + e);
                    throw e;
                } else {
                    log.info("SCHEMA already exists: " + e);
                }
            }
        }

        HashSet<String> foundTables = new HashSet<String>();

        try {
            String s = "select tablename from sysroot.alltables"
                    + " where schemaname like '" + SCHEMA + "%'";

            stmnt = schemaConn.createStatement();
            rs = stmnt.executeQuery(s);
            while (rs.next()) {
                foundTables.add(rs.getString(1).trim());
            }
            rs.close();
        } catch (SQLException e) {
            log.severe("failure finding schema" + e);
            throw e;
        }

        if (foundTables.contains(OBJBASETBL.toLowerCase())) {
            log.fine("Found Objects table");
        } else {
            createObjTable();
        }

        if (foundTables.contains(OBJLOCKBASETBL.toLowerCase())) {
            log.fine("Found object lock table");
        } else {
            createObjLockTable();
        }

        if (foundTables.contains(NAMEBASETBL.toLowerCase())) {
            log.fine("Found Name table");
        } else {
            createNameTable();
        }

        if (foundTables.contains(INFOBASETBL.toLowerCase())) {
            log.fine("INFO: Found info table");
        } else {
            createInfoTable();
        }
    }

    private synchronized void createPreparedStmnts() throws SQLException {

        getObjStmnt = readConn.prepareStatement("SELECT * FROM " + OBJTBLNAME
                + " WHERE OBJID=?");
        getNameStmnt = readConn.prepareStatement("SELECT * FROM " + NAMETBLNAME
                + " WHERE NAME=?");

        deleteNameStmnt = updateTransConn.prepareStatement("DELETE FROM "
                + NAMETBLNAME + " WHERE OBJID=?");

        insertObjStmnt = updateTransConn.prepareStatement("INSERT INTO "
                + OBJTBLNAME + " VALUES(?,?)");
        updateObjStmnt = updateTransConn.prepareStatement("UPDATE "
                + OBJTBLNAME + " SET OBJBYTES=? WHERE OBJID=?");

        insertNameStmnt = updateTransConn.prepareStatement("INSERT INTO "
                + NAMETBLNAME + " VALUES(?,?)");
        deleteObjStmnt = updateTransConn.prepareStatement("DELETE FROM " +
		OBJTBLNAME + " WHERE OBJID=?");
        deleteObjLockStmnt = updateTransConn.prepareStatement("DELETE FROM " +
		OBJLOCKTBLNAME + " WHERE OBJID=?");

        updateObjLockStmnt = updateSingleConn.prepareStatement("UPDATE " +
		OBJLOCKTBLNAME + " SET OBJLOCK=1 WHERE OBJID=? AND OBJLOCK=0");
        updateObjUnlockStmnt = updateSingleConn.prepareStatement("UPDATE " +
		OBJLOCKTBLNAME + " SET OBJLOCK=0 WHERE OBJID=?");

        findObjLockStmnt = readConn.prepareStatement("SELECT OBJID FROM " +
		OBJLOCKTBLNAME + " WHERE OBJID=?");

        insertObjLockStmnt = updateSingleConn.prepareStatement("INSERT INTO "
                + OBJLOCKTBLNAME + " VALUES(?,?)");

        // XXX: HADB does not implement table locking.
        // So, we NEED another way to do this.

        updateInfoStmnt = idConn.prepareStatement("UPDATE " + INFOTBLNAME
                + " SET NEXTIDBLOCKBASE=? "
                + "WHERE APPID=? AND NEXTIDBLOCKBASE=?");

        getIdStmnt = idConn.prepareStatement("SELECT * FROM " + INFOTBLNAME
                + " I  " + "WHERE I.APPID = " + appID);

        idConn.commit();
        updateTransConn.commit();
    }

    private boolean createObjTable() {
	log.info("Creating Objects table " + OBJTBLNAME);

        Statement stmnt;
        String s = "CREATE TABLE " + OBJTBLNAME + " ("
                + "OBJID DOUBLE INT NOT NULL, " + "OBJBYTES BLOB, "
                + "PRIMARY KEY (OBJID))";
        try {
            stmnt = schemaConn.createStatement();
            stmnt.execute(s);
        } catch (Exception e) {
            log.severe("FAILED to create table: " + OBJTBLNAME + " " + e);
            return false;
        }

        return true;
    }

    private boolean createInfoTable() {
        log.info("Creating info table " + INFOTBLNAME);

        Statement stmnt;
        ResultSet rs;

        String s = "CREATE TABLE " + INFOTBLNAME + "("
                + "APPID DOUBLE INT NOT NULL," + "NEXTIDBLOCKBASE DOUBLE INT, "
                + "IDBLOCKSIZE INT, " + "PRIMARY KEY(APPID)" + ")";
        try {
            stmnt = schemaConn.createStatement();
            stmnt.execute(s);
        } catch (SQLException e) {
            log.severe("FAILED to create table: " + INFOTBLNAME + " " + e);
            return false;
        }

        s = "SELECT * FROM " + INFOTBLNAME + " I  " +
		"WHERE I.APPID = " + appID;
        try {
            stmnt = schemaConn.createStatement();
            rs = stmnt.executeQuery(s);
            if (!rs.next()) { // entry does not exist
                log.fine("Creating new entry in info table for appID " + appID);
                stmnt = schemaConn.createStatement();
                s = "INSERT INTO " + INFOTBLNAME + " VALUES(" + appID + ","
                        + defaultIdStart + "," + defaultIdBlockSize + ")";
                stmnt.executeUpdate(s);

                /*
                 * If the table is being re-created, make sure that the
                 * cached state for the table is invalidated as well.
                 */
                currentIdBlockBase = DataSpace.INVALID_ID;
            }
            rs.close();
        } catch (SQLException e) {
            log.severe("FAILED to create " + INFOTBLNAME + ": " + e);
            return false;
        }

        return true;
    }

    private boolean createObjLockTable() {
        log.info("Creating object Lock table " + OBJLOCKTBLNAME);

        String s = "CREATE TABLE " + OBJLOCKTBLNAME + " ("
                + "OBJID DOUBLE INT NOT NULL," + "OBJLOCK INT NOT NULL,"
                + "PRIMARY KEY (OBJID)" + ")";
        try {
            Statement stmnt = schemaConn.createStatement();
            stmnt.execute(s);
        } catch (SQLException e) {
            log.severe("FAILED to create " + OBJLOCKTBLNAME + ": " + e);
            return false;
        }
        return true;
    }

    private boolean createNameTable() {
        log.info("Creating name table " + NAMETBLNAME);

        String s = "CREATE TABLE " + NAMETBLNAME + "("
                + "NAME VARCHAR(255) NOT NULL, " + "OBJID DOUBLE INT NOT NULL,"
                + "PRIMARY KEY (NAME)" + ")";
        try {
            Statement stmnt = schemaConn.createStatement();
            stmnt.execute(s);
        } catch (SQLException e) {
            log.severe("FAILED to create " + NAMETBLNAME + ": " + e);
            return false;
        }

        s = "CREATE INDEX " + NAMEBASETBL + "_" + appID + "_r ON "
                + NAMETBLNAME + " (OBJID)";
        try {
            Statement stmnt = schemaConn.createStatement();
            stmnt.execute(s);
        } catch (SQLException e) {
            log.warning("FAILED to create reverse name index: " + e);
            return false;
        }
        return true;
    }

    /**
     * @return Connection
     */
    private Connection getConnection(String dataConnURL, String userName,
            String userPasswd, int isolation) {
        try {
            Connection conn = DriverManager.getConnection(dataConnURL,
                    userName, userPasswd);
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolation);
            return conn;
        } catch (Exception e) {
	    log.severe("FAILED to get Connection: " + e);
            e.printStackTrace();
        }
        return null;
    }

    /*
     * Internal parameters for object ID generation:
     * 
     * defaultIdStart is where the object ID numbers start.  It is
     * only used to initialize the database and should never be used
     * directly.  (only IDs taken from the database are guaranteed
     * valid)
     * 
     * defaultIdBlockSize is the number of object IDs grabbed at one
     * time.  Should be at least 1000 for real use.
     */

    private final long defaultIdBlockSize = 1000;
    private final long defaultIdStart = defaultIdBlockSize;

    /*
     * Internal variables for object ID generation:
     * 
     * currentIdBlockBase is the start of the current block of OIDs that
     * this instance of an HADB can use to generate OIDs. It begins with
     * an illegal value to force a fetch from the database.
     * 
     * currentIdBlockOffset is the offset of the next OID to generate
     * within the current block.
     */

    private long currentIdBlockBase = DataSpace.INVALID_ID;
    private long currentIdBlockOffset = 0;

    private synchronized long getNextID() {

        /*
         * In order to minimize the overhead of creating new object IDs,
         * we allocate them in blocks rather than individually. If there
         * are still remaining object Ids available in the current block
         * owned by this reference to the DataSpace, then the next such
         * Id is allocated and returned. Otherwise, a new block is
         * accessed from the database.
         */

	log.finest("currentIdBlockBase = " + currentIdBlockBase);

        if ((currentIdBlockBase == DataSpace.INVALID_ID) ||
		(currentIdBlockOffset >= defaultIdBlockSize)) {
            long newIdBlockBase = 0;
            int newIdBlockSize = 0;
            boolean success = false;

	    log.fine("going to database to find the new blockBase");
            try {
                long backoffSleep = 0;
                while (!success) {

                    /*
                     * Get the current value of the NEXTIDBLOCKBASE from
                     * the database. It is a serious problem if this
                     * fails -- it might not be possible to recover from
                     * this condition.
                     */

                    ResultSet rs = getIdStmnt.executeQuery();
                    if (!rs.next()) {
                        log.severe("appID table entry absent for " + appID);
                        // XXX: FATAL unexpected error.
                    }
                    newIdBlockBase = rs.getLong("NEXTIDBLOCKBASE"); // "2"
                    newIdBlockSize = rs.getInt("IDBLOCKSIZE"); // "3"
		    rs.close();

		    log.fine("NEXTIDBLOCKBASE/IDBLOCKSIZE " +
			    newIdBlockBase + "/" + newIdBlockSize);

                    int rc;
                    updateInfoStmnt.setLong(1, newIdBlockBase + newIdBlockSize);
                    updateInfoStmnt.setLong(2, appID);
                    updateInfoStmnt.setLong(3, newIdBlockBase);
                    try {
                        rc = updateInfoStmnt.executeUpdate();
                        if (rc == 1) {
                            updateInfoStmnt.getConnection().commit();
                            success = true;
                        } else {
                            updateInfoStmnt.getConnection().rollback();
                            success = false;
                        }
                    } catch (SQLException e) {
                        if (e.getErrorCode() == 2097) {
                            log.info("contention for info table");
                            success = false;
                            updateInfoStmnt.getConnection().rollback();
                            try {
                                Thread.sleep(10);
                            } catch (Exception e2) {
                                // ignore
                            }
                        } else {
			    log.severe("unexpected exception: " + e);
                            e.printStackTrace();
                        }
                    }

                    /*
                     * If we didn't succeed, then try again, perhaps
                     * after a short pause. The pause backs off to a
                     * maximum of 5ms.
                     */
                    if (!success) {
                        if (backoffSleep > 0) {
                            try {
                                Thread.sleep(backoffSleep);
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                        backoffSleep++;
                        if (backoffSleep > 5) {
                            backoffSleep = 5;
                        }
                    }
                }
            } catch (SQLException e) {
		log.severe("unexpected exception: " + e);
                e.printStackTrace();
            }

            currentIdBlockBase = newIdBlockBase;
            currentIdBlockOffset = 0;
        }

        long newOID = currentIdBlockBase + currentIdBlockOffset++;
        log.finest("new ObjectID = " + newOID);

        /*
	 * For the sake of convenience, create the object lock
	 * immediately, instead of waiting for the atomic update to
	 * occur.  This streamlines the update (and allows us to lock
	 * objects that exist in the world but don't exist in the
	 * objtable).
         */

        try {
            insertObjLockStmnt.setLong(1, newOID);
            insertObjLockStmnt.setInt(2, 0);
            insertObjLockStmnt.executeUpdate();
            insertObjLockStmnt.getConnection().commit();
            log.finest("SUCCESS creating locks for ObjectID " + newOID);
        } catch (SQLException e) {
            try {
                insertObjLockStmnt.getConnection().rollback();
            } catch (SQLException e2) {
                log.severe("FAILED to rollback attempt to create locks " + e2);
            }
	    log.severe("FAILED to create locks for ObjectID " + newOID +
		    ": " + e);
            e.printStackTrace();
        }

        return newOID;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized byte[] getObjBytes(long objectID) {
        byte[] objbytes = null;
        try {
            getObjStmnt.setLong(1, objectID);
            ResultSet rs = getObjStmnt.executeQuery();
            // getObjStmnt.getConnection().commit();
            if (rs.next()) {
                Blob b = rs.getBlob("OBJBYTES");
                objbytes = b.getBytes(1L, (int) b.length());
            }
            rs.close(); // cleanup and free locks
        } catch (SQLException e) {
	    log.severe("failed to getObjBytes for object " + objectID +
		    " " + e);
            e.printStackTrace();
        }
        return objbytes;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void lock(long objectID)
            throws NonExistantObjectIDException {

        /*
         * This is ugly. I'd love to hear about a better approach.
         * 
         * Locks are stored in a table in the database, with one row per
         * object. To get a lock on an object, an attempt is made to
         * update the row for that object with a new value for the
         * locked column of that row. If the update succeeds, then this
         * update acquires the lock. If the update fails, then the
         * process is repeated until it succeeds or the caller gives up
         * in some manner (not part of the interface).
         * 
         * At present the lock table is a different table than the
         * object storage, but it could be squeezed into that table as
         * well. This may be an item to tune later.
         */

        int rc = -1;
        long backoffSleep = 0;

        for (;;) {

            /*
             * Is it necessary to reset the values of the parameters
             * each time, or do they survive execution? If the latter,
             * then the "set"s can be hoisted out of the loop. -DJE
             */

            rc = 0;
            try {
                updateObjLockStmnt.setLong(1, objectID);
                rc = updateObjLockStmnt.executeUpdate();
            } catch (SQLException e) {
                log.finer("Blocked on " + objectID + " " + e);
                e.printStackTrace();
            }

            if (rc == 1) {
		log.finest("Got the lock on " + objectID);
                return;
            } else {

		/*
		 * If we didn't get the lock, there are two possible
		 * reasons:  first, the lock doesn't exist, and
		 * second, that someone else is holding the lock.  We
		 * need to squander another query to discover which.
		 */

		try {
		    findObjLockStmnt.setLong(1, objectID);
		    ResultSet rs = findObjLockStmnt.executeQuery();
		    boolean foundSomething = rs.next();
		    rs.close();
		    if (!foundSomething) {
			throw new NonExistantObjectIDException(
				"nonexistant object " + objectID);
		    }
		} catch (SQLException e) {
		    log.severe("exception when finding a lock: " + e);
		    e.printStackTrace();
		}

                /*
		 * If we didn't succeed, then try again, perhaps after
		 * a short pause.  The pause backs off to a maximum of
		 * 5ms.
                 */

                if (backoffSleep > 0) {
                    try {
                        Thread.sleep(backoffSleep);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                backoffSleep++;
                if (backoffSleep > 5) {
                    backoffSleep = 5;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void release(long objectID)
            throws NonExistantObjectIDException {

        /*
	 * Similar to lock, except it doesn't poll.  If the update
	 * fails, it assumes that's because the lock is already
	 * unlocked, and rolls back.
         * 
	 * There might be a race condition here:  can't tell without
	 * looking at the actual packets, which is scary.  -DJE
         */

        int rc = -1;
        try {
            updateObjUnlockStmnt.setLong(1, objectID);
            rc = updateObjUnlockStmnt.executeUpdate();
        } catch (SQLException e) {
	    log.info("Tried to unlock (" + objectID +
		    "): already unlocked: " + e);
            e.printStackTrace();
        }

        if (rc == 1) {
            log.finest("Released the lock on " + objectID);
            return;
        } else {
            // XXX: not an error. This is a diagnostic only.
            log.finest("Didn't need to unlock " + objectID);
        }
    }

    /*
     * {@inheritDoc}
     */
    public void release(Set<Long> objectIDs)
            throws NonExistantObjectIDException {

	Long[] oids = new Long[objectIDs.size()];
	objectIDs.toArray(oids);

        /*
         * Similar to lock, except it doesn't poll. If the update fails,
         * it assumes that's because the lock is already unlocked, and
         * rolls back.
         * 
         * There might be a race condition here: can't tell without
         * looking at the actual packets, which is bad. -DJE
         */

        int[] updateCounts = new int[0];
        for (long oid : oids) {
            try {
                updateObjUnlockStmnt.setLong(1, oid);
                updateObjUnlockStmnt.addBatch();
            } catch (SQLException e) {
                log.severe("Failed to set batch parameters in release: " + e);
            }
        }

        try {
            updateCounts = updateObjUnlockStmnt.executeBatch();
        } catch (SQLException e) {
	    log.severe("Failed to execute release: " + e);
            e.printStackTrace();
        }

        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] != 1) {

		/*
		 * 3/21/06 - this is not necessarily an error because
		 * the current DataSpaceTransactionImpl.commit (and
		 * perhaps abort) can release the locks on objects it
		 * just deleted.  Therefore it's very common to see
		 * this exception during CORRECT execution.  This
		 * warning will nag someone to fix this after GDC. 
		 * -DJE
		 */

                log.warning("failed to release objectID " + oids[i]);
                throw new NonExistantObjectIDException("unknown obj released.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void atomicUpdate(boolean clear,
	    Map<Long, byte[]> updateMap, List<Long> deleteList)
            throws DataSpaceClosedException {
        if (closed) {
            throw new DataSpaceClosedException();
        }

        if (updateMap.entrySet().size() > 0) {
            for (Entry<Long, byte[]> e : updateMap.entrySet()) {
                long oid = e.getKey();
                byte[] data = e.getValue();

                if (deleteList.contains(oid)) {
                    continue;
                }

                try {
		    updateObjStmnt.setBytes(1, data);
		    updateObjStmnt.setLong(2, oid);
		    updateObjStmnt.addBatch();
                } catch (SQLException e1) {
                    // XXX: rollback and die
                    e1.printStackTrace();
                }
            }

            if (updateMap.entrySet().size() > 0) {
                try {
                    updateObjStmnt.executeBatch();
                } catch (SQLException e1) {
                    // XXX: rollback and die
                    e1.printStackTrace();
                }
            }
        }

        for (long oid : deleteList) {
            try {
                deleteObjStmnt.setLong(1, oid);
                deleteObjStmnt.execute();
                deleteObjLockStmnt.setLong(1, oid);
                deleteObjLockStmnt.execute();
		deleteNameStmnt.setLong(1, oid);
		deleteNameStmnt.executeUpdate();
            } catch (SQLException e1) {
                // XXX: rollback and die
                e1.printStackTrace();
            }
        }

        // There's got to be a better way.
        try {
            updateTransConn.commit();
            // deleteObjStmnt.getConnection().commit();
            // updateObjStmnt.getConnection().commit();
            // insertNameStmnt.getConnection().commit();
        } catch (SQLException e) {
            // XXX: rollback and die
            // Is there anything we can do here, other than weep?
            e.printStackTrace();
        }
    }

    /**
     * {inheritDoc}
     */
    public synchronized Long lookup(String name) {
        long oid = DataSpace.INVALID_ID;

        try {
            getNameStmnt.setString(1, name);
            ResultSet rs = getNameStmnt.executeQuery();
            // getNameStmnt.getConnection().commit();
            if (rs.next()) {
                oid = rs.getLong("OBJID");
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return oid;
    }

    /*
     * {@inheritDoc}
     */
    public synchronized long getAppID() {
        return appID;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void clear() {

        try {
            checkTables();
            clearTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void close() {

        try {
            idConn.commit();
            idConn.close();
        } catch (SQLException e) {
            log.warning("can't close idConn " + e);
        }

        try {
            updateTransConn.commit();
            updateTransConn.close();
        } catch (SQLException e) {
            log.warning("can't close updateTransConn " + e);
        }

        try {
            updateSingleConn.close();
        } catch (SQLException e) {
            log.warning("can't close updateSingleConn " + e);
        }

        try {
            schemaConn.close();
        } catch (SQLException e) {
            log.warning("can't close schemaConn " + e);
        }

        try {
            readConn.close();
        } catch (SQLException e) {
            log.warning("can't close readConn " + e);
        }

        log.info("DataSpace CLOSED");

        closed = true;
    }

    public long create(byte[] data, String name) {
        if (data == null) {
            throw new NullPointerException("data is null");
        }

        long oid = getNextID();

        if (name != null) {
            try {
                insertNameStmnt.setString(1, name);
                insertNameStmnt.setLong(2, oid);
                insertNameStmnt.executeUpdate();
            } catch (SQLException e) {
		try {
		    insertObjStmnt.getConnection().rollback();
		} catch (SQLException e2) {
		    log.severe("create: double error" + e);
		    return DataSpace.INVALID_ID;
		}

                // If there was already an identical name, then
		// abandon the attempt.

                if (e.getErrorCode() == 11939) {
		    return DataSpace.INVALID_ID;
		} else {
		    // XXX: what happpened?
		    // XXX: are there other cases we can handle?
		    e.printStackTrace();
		    return DataSpace.INVALID_ID;
		}
            }
        }

        try {
            insertObjStmnt.setLong(1, oid);
            insertObjStmnt.setBytes(2, data);
            insertObjStmnt.executeUpdate();

            insertObjStmnt.getConnection().commit();

            return oid;
        } catch (SQLException e) {
            try {
                insertObjStmnt.getConnection().rollback();
            } catch (SQLException e2) {
                // XXX: DO SOMETHING.
            }
            return DataSpace.INVALID_ID;
        }
    }
}
