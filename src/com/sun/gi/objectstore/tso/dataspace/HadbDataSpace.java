/*
 * <p>Copyright: Copyright (c) 2006 Sun Microsystems, Inc.</p>
 */

package com.sun.gi.objectstore.tso.dataspace;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;

/**
 * A {@link DataSpace} based on HADB/JDBC.
 */
public class HadbDataSpace implements DataSpace {

    private long appID;
    private Object idMutex = new Object();
    private String dataConnURL;

    /*
     * HADB appears to convert all table and column names to
     * lower-case internally, and some of the methods for
     * accessing the metadata do not convert, so it's easier if
     * everything is simply lower-case to begin with.  (otherwise
     * it's possible to successfully create a table with a given
     * name, and then ask whether a table with that name exists,
     * and get the answer "no") -DJE
     */

    private static final String SCHEMA = "simserver";
    private static final String OBJBASETBL = "objects";
    private static final String OBJLOCKBASETBL = "objlocks";
    private static final String NAMEBASETBL = "namedirectory";
    private static final String INFOTBL = "appinfo";

    private static final String INFOTBLNAME = SCHEMA + "." + INFOTBL;

    private String NAMETBL;
    private String OBJTBL;
    private String NAMETBLNAME;
    private String OBJTBLNAME;
    private String OBJLOCKTBL;
    private String OBJLOCKTBLNAME;

    private Connection conn;
    private Connection updateConn;
    private Connection idConn;
    
    private PreparedStatement getIdStmnt;
    private PreparedStatement getObjStmnt;
    private PreparedStatement getNameStmnt;
    private PreparedStatement updateObjStmnt;
    private PreparedStatement insertObjStmnt;
    private PreparedStatement updateNameStmnt;
    private PreparedStatement insertNameStmnt;
    private PreparedStatement updateInfoStmnt;
    private PreparedStatement insertInfoStmnt;
    private PreparedStatement updateObjLockStmnt;
    private PreparedStatement insertObjLockStmnt;
    private PreparedStatement deleteObjStmnt;
    private PreparedStatement clearObjTableStmnt;
    private PreparedStatement lockObjTableStmnt;
    private PreparedStatement clearNameTableStmnt;
    private PreparedStatement lockNameTableStmnt;

    private volatile boolean closed = false;

    private static final boolean TRACEDISK=false;
    
    private int commitRegisterCounter=1;

    /**
     * Creates a DataSpace with the given appId, connected to the
     * "default" HADB instance.
     *
     * @param appId the application ID
     */
    public HadbDataSpace(long appId)
	    throws Exception
    {
	this(appId, false);
    }

    /**
     * Creates a DataSpace with the given appId, connected to the
     * "default" HADB instance.
     *
     * @param appId the application ID
     *
     * @param dropTables if <code>true</code>, then drop all tables
     * before begining.
     */
    public HadbDataSpace(long appID, boolean dropTables)
	    throws Exception
    {
	this.appID = appID;

	// If we can't load the driver, bail out immediately.
	try {
	    Class.forName("com.sun.hadb.jdbc.Driver");
	} catch (Exception e) { // XXX fix this
	    System.out.println(e);
	    throw e;
	}

	OBJTBL = OBJBASETBL + "_" + appID;
	OBJTBLNAME = SCHEMA + "." + OBJTBL;

	OBJLOCKTBL = OBJLOCKBASETBL + "_" + appID;
	OBJLOCKTBLNAME = SCHEMA + "." + OBJLOCKTBL;

	NAMETBL = NAMEBASETBL + "_" + appID;
	NAMETBLNAME = SCHEMA + "." + NAMETBL;

	try {
	    // XXX: MUST take these from a config file.

	    String hadbHosts = "129.148.75.63:15025,129.148.75.60:15005";
	    String userName = "system";
	    String userPasswd = "darkstar";

	    dataConnURL = "jdbc:sun:hadb:" + hadbHosts + ";create=true";

	    // XXX:  Do we really need two distinct connections for
	    // data and update?

	    conn = getConnection(userName, userPasswd,
		    Connection.TRANSACTION_READ_COMMITTED);
	    updateConn = getConnection(userName, userPasswd,
		    Connection.TRANSACTION_READ_COMMITTED);
	    idConn = getConnection(userName, userPasswd,
		    Connection.TRANSACTION_REPEATABLE_READ);

	    if (dropTables) {
		dropTables();
	    }

	    checkTables();
	    createPreparedStmnts();

	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    private void dropTables() {
	dropTable(OBJTBLNAME);
	dropTable(OBJLOCKTBLNAME);
	dropTable(NAMETBLNAME);
	dropTable(INFOTBLNAME);
    }

    private boolean dropTable(String tableName) {
	String s;
	Statement stmnt;

	s = "DROP TABLE " + tableName;
	try {
	    stmnt = conn.createStatement();
	    stmnt.execute(s);
	    stmnt.getConnection().commit();
	    System.out.println("Dropped " + tableName);
	    return true;
	} catch (SQLException e) {
	    // XXX ?
	    System.out.println("FAILED to drop " + tableName);
	    return false;
	}
    }

    /**
     * @throws SQLException
     */
    private void checkTables() throws SQLException {
	DatabaseMetaData md = conn.getMetaData();
	ResultSet rs;

	/*
	 * It is not possible, in HADB, to bring a schema into
	 * existance simply by defining tables within it.  Therefore
	 * we create it separately -- but this might fail, because the
	 * schema might already exist.  There doesn't seem to be a
	 * good way to distinguish this from a "real" failure, but
	 * there ought to be something better than this!  -DJE
	 */

	try {
	    System.out.println("Creating Schema");
	    String s = "CREATE SCHEMA " + SCHEMA;
	    Statement stmnt = conn.createStatement();
	    stmnt.execute(s);
	    stmnt.getConnection().commit();
	} catch (SQLException e) {

	    /*
	     * 11751 is "this schema already exists"
	     */

	    if (e.getErrorCode() != 11751) {
		System.out.println("SCHEMA issue: " + e);
		throw e;
	    }
	}

	HashSet<String> foundTables = new HashSet<String>();

	try {
	    String s = "select tablename from sysroot.alltables" +
		    " where schemaname like '" + SCHEMA + "%'";

	    Statement stmnt = conn.createStatement();
	    rs = stmnt.executeQuery(s);
	    while (rs.next()) {
		foundTables.add(rs.getString(1).trim());
	    }
	    rs.close();
	    stmnt.getConnection().commit();
	} catch (Exception e) {
	    // XXX: failure?
	    System.out.println("failure finding schema" + e);
	}

	if (foundTables.contains(OBJTBL.toLowerCase())) {
	    System.out.println("Found Objects table");
	} else {
	    System.out.println("Creating Objects table");
	    String s = "CREATE TABLE " + OBJTBLNAME + " (" +
			"OBJID DOUBLE INT NOT NULL," +
			"OBJBYTES BLOB," +
			"PRIMARY KEY (OBJID)" +
			")";
	    Statement stmnt = conn.createStatement();
	    stmnt.execute(s);
	    stmnt.getConnection().commit();
	}

	if (foundTables.contains(OBJLOCKTBL.toLowerCase())) {
	    System.out.println("Found object lock table");
	} else {
	    System.out.println("Creating object Lock table");
	    String s = "CREATE TABLE " + OBJLOCKTBLNAME + " (" +
			"OBJID DOUBLE INT NOT NULL," +
			"OBJLOCK INT NOT NULL," +
			"PRIMARY KEY (OBJID)" +
			")";
	    Statement stmnt = idConn.createStatement();
	    stmnt.execute(s);
	    stmnt.getConnection().commit();
	}

	if (foundTables.contains(NAMETBL.toLowerCase())) {
	    System.out.println("Found Name table");
	} else {
	    System.out.println("Creating Name table");
	    String s = "CREATE TABLE " + NAMETBLNAME + "(" +
			"NAME VARCHAR(255) NOT NULL, " +
			"OBJID DOUBLE INT NOT NULL," +
			"PRIMARY KEY (NAME)" +
			")";
	    Statement stmnt = conn.createStatement();
	    stmnt.execute(s);
	    stmnt.getConnection().commit();
	}

	if (foundTables.contains(INFOTBL.toLowerCase())) {
	    System.out.println("Found info table");
	} else {
	    System.out.println("Creating info table");
	    String s = "CREATE TABLE " + INFOTBLNAME + "(" +
			"APPID DOUBLE INT NOT NULL," +
			"NEXTIDBLOCKBASE DOUBLE INT, " +
			"IDBLOCKSIZE INT, " +
			"PRIMARY KEY(APPID)" +
			")";
	    Statement stmnt = conn.createStatement();
	    stmnt.execute(s);
	    stmnt.getConnection().commit();
	}

	getIdStmnt = idConn.prepareStatement("SELECT * FROM " +
		INFOTBLNAME + " I  " + "WHERE I.APPID = " + appID);
	rs = getIdStmnt.executeQuery();
	if (!rs.next()) { // entry does not exist
	    System.out.println("Creating new entry in info table for appID "
		    + appID);
	    Statement stmnt = getIdStmnt.getConnection().createStatement();
	    String s = "INSERT INTO " + INFOTBLNAME + " VALUES(" +
		    appID + "," + defaultIdStart + "," +
		    defaultIdBlockSize + ")";
	    stmnt.execute(s);
	}
	rs.close();
	getIdStmnt.getConnection().commit();
    }

    private void createPreparedStmnts() throws SQLException {

	getObjStmnt = conn.prepareStatement("SELECT * FROM " +
		OBJTBLNAME + " O  " + "WHERE O.OBJID = ?");
	getNameStmnt = conn.prepareStatement("SELECT * FROM " +
		NAMETBLNAME + " N  " + "WHERE N.NAME = ?");
	insertObjStmnt = updateConn.prepareStatement("INSERT INTO " +
		OBJTBLNAME + " VALUES(?,?)");
	insertNameStmnt = updateConn.prepareStatement("INSERT INTO " +
		NAMETBLNAME + " VALUES(?,?)");
	updateObjStmnt = updateConn.prepareStatement("UPDATE " +
		OBJTBLNAME + " SET OBJBYTES=? WHERE OBJID=?");
	updateNameStmnt = updateConn.prepareStatement("UPDATE " +
		NAMETBLNAME + " SET NAME=? WHERE OBJID=?");
	deleteObjStmnt = updateConn.prepareStatement("DELETE FROM " +
		OBJTBLNAME + " WHERE OBJID = ?");

	updateInfoStmnt = idConn.prepareStatement("UPDATE " +
		INFOTBLNAME + " SET NEXTIDBLOCKBASE=? WHERE APPID=?");

	updateObjLockStmnt = idConn.prepareStatement("UPDATE " +
		OBJLOCKTBLNAME +
		    " SET OBJLOCK=? WHERE OBJID=? AND OBJLOCK=?");
	insertObjLockStmnt = idConn.prepareStatement("INSERT INTO " +
			OBJLOCKTBLNAME + " VALUES(?,?)");

	// XXX: HADB does not implement table locking.
	// So, we NEED another way to do this.

	// lockObjTableStmnt = conn.prepareStatement(
	//	"LOCK TABLE "+OBJTBLNAME+" IN EXCLUSIVE MODE");
	// lockNameTableStmnt = conn.prepareStatement(
	//	"LOCK TABLE "+NAMETBLNAME+" IN EXCLUSIVE MODE");

	clearObjTableStmnt = conn.prepareStatement(
		"DELETE FROM " + OBJTBLNAME);
	clearNameTableStmnt = conn.prepareStatement(
		"DELETE FROM " + NAMETBLNAME);
    }

    private void clearTables() {
	String s;
	PreparedStatement stmnt;

	System.out.println("Dropping Objects table");

	try {
	    s = "DROP TABLE " + OBJTBLNAME;
	    stmnt = conn.prepareStatement(s);
	    stmnt.execute();
	    stmnt.getConnection().commit();
	} catch (Exception e) {
	    // XXX
	}

	System.out.println("Creating Objects table");

	try { 
	    s = "CREATE TABLE " + OBJTBLNAME + " (" +
		    "OBJID DOUBLE INT NOT NULL, " +
		    "OBJBYTES BLOB, " +
		    "PRIMARY KEY (OBJID))";
	    stmnt = conn.prepareStatement(s);
	    stmnt.execute();
	    stmnt.getConnection().commit();
	} catch (Exception e) {
	    // XXX
	}
    }

    /**
     * @return
     */
    private Connection getConnection(String userName, String userPasswd,
	    int isolation)
    {
	Connection conn;
	try {
	    conn = DriverManager.getConnection(dataConnURL,
		    userName, userPasswd);

	    conn.setAutoCommit(false);
	    conn.setTransactionIsolation(isolation);
	    return conn;
	} catch (Exception e) {
	    System.out.println(e);
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
     * time.  This is currently very small, for debugging.  Should be
     * at least 1000 for real use.
     */

    private final long defaultIdStart		= 10;
    private final long defaultIdBlockSize	= 10;

    /*
     * Internal variables for object ID generation:
     *
     * currentIdBlockBase is the start of the current block of OIDs
     * that this instance of an HADB can use to generate OIDs.  It
     * begins with an illegal value to force a fetch from the
     * database.
     *
     * currentIdBlockOffset is the offset of the next OID to generate
     * within the current block.
     */

    private long currentIdBlockBase	= DataSpace.INVALID_ID;
    private long currentIdBlockOffset	= 0;

    /**
     * {@inheritDoc}
     */
    public synchronized long getNextID() {

	/*
	 * In order to minimize the overhead of creating new object
	 * IDs, we allocate them in blocks rather than individually. 
	 * If there are still remaining object Ids available in the
	 * current block owned by this reference to the DataSpace,
	 * then the next such Id is allocated and returned. 
	 * Otherwise, a new block is accessed from the database.
	 */

	if ((currentIdBlockBase == DataSpace.INVALID_ID) ||
		(currentIdBlockOffset >= defaultIdBlockSize)) {
	    long newIdBlockBase = 0;
	    int newIdBlockSize = 0;
	    boolean success = false;

	    try {
		idConn.commit();
	    } catch (Exception e) {
		System.out.println("UNEXPECTED EXCEPTION: " + e);
	    }

	    try {
		long backoffSleep = 0;
		while (!success) {

		    /*
		     * Get the current value of the NEXTIDBLOCKBASE
		     * from the database.  It is a serious problem if
		     * this fails -- it might not be possible to
		     * recover from this condition.
		     */

		    ResultSet rs = getIdStmnt.executeQuery();
		    if (!rs.next()) {
			System.out.println("appID table entry absent for " +
				appID);
			// XXX: FATAL unexpected error.
		    }
		    newIdBlockBase = rs.getLong("NEXTIDBLOCKBASE"); // "2"
		    newIdBlockSize = rs.getInt("IDBLOCKSIZE"); // "3"

		    /*
		    System.out.println("NEXTIDBLOCKBASE/IDBLOCKSIZE " +
			    newIdBlockBase + "/" + newIdBlockSize);
		    */

		    updateInfoStmnt.setLong(1, newIdBlockBase + newIdBlockSize);
		    updateInfoStmnt.setLong(2, appID);
		    try {
			updateInfoStmnt.executeUpdate();
			updateInfoStmnt.getConnection().commit();
			// System.out.println("SUCCESS");
			success = true;
		    } catch (SQLException e) {
			if (e.getErrorCode() == 2097) {
			    success = false;
			    updateInfoStmnt.getConnection().rollback();
			    try {
				Thread.sleep(2);
			    } catch (Exception e2) {
			    }
			} else {
			    System.out.println("YY " + e + " " + e.getErrorCode());
			    e.printStackTrace();
			}
		    } finally {
			// System.out.println("\t\tClosing...");
			rs.close();
		    }

		    /*
		     * If we didn't succeed, then try again, perhaps
		     * after a short pause.  The pause backs off to a 
		     * maximum of 5ms.
		     */
		    if (!success) {
			if (backoffSleep > 0) {
			    try {
				Thread.sleep(backoffSleep);
			    } catch (InterruptedException e) {
			    }
			}
			backoffSleep++;
			if (backoffSleep > 5) {
			    backoffSleep = 5;
			}
		    }
		}
	    } catch (SQLException e) {
		// XXX
		System.out.println("TT " + e + " " + e.getErrorCode());
		e.printStackTrace();
	    }

	    currentIdBlockBase = newIdBlockBase;
	    currentIdBlockOffset = 0;
	}

	long newOID = currentIdBlockBase + currentIdBlockOffset++;
	// System.out.println("NEW OID " + newOID);

	/*
	 * For the sake of convenience, create the object lock
	 * immediately, instead of waiting for the atomic update to
	 * occur.  This streamlines the update (and allows us to lock
	 * objects that exist in the world but don't exist in the
	 * objtable).
	 */

	try {
	    // System.out.println("inserting oid " + newOID);
	    insertObjLockStmnt.setLong(1, newOID);
	    insertObjLockStmnt.setInt(2, 0);
	} catch (SQLException e) {
	    // XXX: ??
	}

	try {
	    insertObjLockStmnt.executeUpdate();
	    insertObjLockStmnt.getConnection().commit();
	} catch (SQLException e) {
	    try {
		updateObjLockStmnt.getConnection().rollback();
	    } catch (SQLException e2) {
		System.out.println("FAILED TO ROLLBACK");
		System.out.println(e2);
	    }
	    System.out.println("FAILURE for OID: " + newOID);
	    System.out.println(e);
	    e.printStackTrace();
	}

	return newOID;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	byte[] objbytes = null;
	try {
	    getObjStmnt.setLong(1, objectID);
	    ResultSet rs = getObjStmnt.executeQuery();
	    getObjStmnt.getConnection().commit();
	    if (rs.next()) {
		// objbytes = rs.getBytes("OBJBYTES");
		Blob b = rs.getBlob("OBJBYTES");
		objbytes = b.getBytes(1L, (int) b.length());
	    }
	    rs.close(); // cleanup and free locks
	} catch (SQLException e) {
	    System.out.println(e);
	    e.printStackTrace();
	}
	return objbytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID) throws NonExistantObjectIDException {

	/*
	 * This is ugly.  I'd love to hear about a better approach.
	 *
	 * Locks are stored in a table in the database, with one row
	 * per object.  To get a lock on an object, an attempt is made
	 * to update the row for that object with a new value for the
	 * locked column of that row.  If the update succeeds, then
	 * this update acquires the lock.  If the update fails, then
	 * the process is repeated until it succeeds or the caller
	 * gives up in some manner (not part of the interface).
	 *
	 * At present the lock table is a different table than the
	 * object storage, but it could be squeezed into that table as
	 * well.  This may be an item to tune later.
	 */

	 int rc = -1;
	 long backoffSleep = 0;

	 for (;;) {

	    /*
	     * Is it necessary to reset the values of the parameters
	     * each time, or do they survive execution?  If the latter,
	     * then the "set"s can be hoisted out of the loop. -DJE
	     */

	    try {
		updateObjLockStmnt.setInt(1, 1);
		updateObjLockStmnt.setLong(2, objectID);
		updateObjLockStmnt.setInt(3, 0);
	    } catch (SQLException e) {
		System.out.println("FAILED to set parameters");
	    }

	    rc = 0;
	    try {
		rc = updateObjLockStmnt.executeUpdate();
		if (rc == 1) {
		    updateObjLockStmnt.getConnection().commit();
		} else {
		    updateObjLockStmnt.getConnection().rollback();
		}
	    } catch (SQLException e) {
		try {
		    updateObjLockStmnt.getConnection().rollback();
		} catch (SQLException e2) {
		    System.out.println("FAILED TO ROLLBACK");
		    System.out.println(e2);
		}
		System.out.println("Blocked on " + objectID);
		System.out.println(e);
		e.printStackTrace();
	    }

	    if (rc == 1) {
		/*
		System.out.println("Got the lock on " +
			objectID + " rc = " + rc);
		*/
		return;
	    } else {
		System.out.println("Missed the lock on " +
			objectID + " rc = " + rc);

		/*
		 * If we didn't succeed, then try again, perhaps after
		 * a short pause.  The pause backs off to a maximum of
		 * 5ms.
		 */

		if (backoffSleep > 0) {
		    try {
			Thread.sleep(backoffSleep);
		    } catch (InterruptedException e) {
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
    public void release(long objectID) {
	
	/*
	 * Similar to lock, except it doesn't poll.  If the update
	 * fails, it assumes that's because the lock is already
	 * unlocked, and rolls back.
	 *
	 * There might be a race condition here:  can't tell without
	 * looking at the actual packets, which is bad.  -DJE
	 */

	int rc = -1;
	try {
	    updateObjLockStmnt.setInt(1, 0);
	    updateObjLockStmnt.setLong(2, objectID);
	    updateObjLockStmnt.setLong(3, 1);
	} catch (SQLException e) {
	    System.out.println("FAILED to set parameters");
	}

	try {
	    rc = updateObjLockStmnt.executeUpdate();
	    if (rc == 1) {
		updateObjLockStmnt.getConnection().commit();
	    } else {
		updateObjLockStmnt.getConnection().rollback();
	    }
	} catch (SQLException e) {
	    try {
		updateObjLockStmnt.getConnection().rollback();
	    } catch (SQLException e2) {
		System.out.println("FAILED TO ROLLBACK");
		System.out.println(e2);
	    }
	    System.out.println("Tried to unlock (" + objectID +
		    "): already unlocked.");
	    System.out.println(e);
	    e.printStackTrace();
	}

	if (rc == 1) {
	    // System.out.println("Released the lock on " + objectID + " rc = " + rc);
	    return;
	} else {
	    System.out.println("Didn't need to unlock " + objectID + " rc = " + rc);
	    // XXX: not an error.  This is a diagnostic only.
	}
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void atomicUpdate(boolean clear, Map<String, Long> newNames,
	    Set<Long> deleteSet, Map<Long, byte[]> updateMap,
	    Set<Long> insertIDs)
	throws DataSpaceClosedException
    {
	if (closed) {
	    throw new DataSpaceClosedException();
	}

	// Commit any outstanding transactions.  There SHOULDN'T be
	// any, but if there's a bug elsewhere, keep it out of this
	// transaction.

	try {
	    deleteObjStmnt.getConnection().commit();
	    updateObjStmnt.getConnection().commit();
	    insertObjStmnt.getConnection().commit();
	    insertNameStmnt.getConnection().commit();
	} catch (SQLException e) {
	    // XXX: rollback and die
	    // Is there anything we can do here, other than weep?
	    e.printStackTrace();
	}

	for (Entry<String, Long> e : newNames.entrySet()) {
	    long oid = e.getValue();
	    String name = e.getKey();

	    /*
	     * If something is in the deleteSet, then there's no need
	     * to insert it because we're going to remove it as part
	     * of this atomic op.
	     */

	    if (deleteSet.contains(oid)) {
		continue;
	    }

	    try {
		insertNameStmnt.setString(1, name);
		insertNameStmnt.setLong(2, oid);
		insertNameStmnt.execute();
	    } catch (SQLException e1) {
		// XXX: rollback and die
		e1.printStackTrace();
	    }
	}

	for (Entry<Long, byte[]> e : updateMap.entrySet()) {
	    long oid = e.getKey();
	    byte[] data = e.getValue();

	    if (deleteSet.contains(oid)) {
		continue;
	    }

	    try {
		if (insertIDs.contains(oid)) {
		    insertObjStmnt.setLong(1, oid);
		    insertObjStmnt.setBytes(2, data);
		    insertObjStmnt.execute();
		} else {
		    updateObjStmnt.setBytes(1, data);
		    updateObjStmnt.setLong(2, oid);
		    updateObjStmnt.execute();
		}
	    } catch (SQLException e1) {
		// XXX: rollback and die
		e1.printStackTrace();
	    }
	}

	for (long oid : deleteSet) {
	    if (insertIDs.contains(oid)) {
		continue;
	    }

	    try {
		deleteObjStmnt.setLong(1, oid);
		deleteObjStmnt.execute();
	    } catch (SQLException e1) {
		// XXX: rollback and die
		e1.printStackTrace();
	    }
	}

	// There's got to be a better way.
	try {
	    deleteObjStmnt.getConnection().commit();
	    updateObjStmnt.getConnection().commit();
	    insertObjStmnt.getConnection().commit();
	    insertNameStmnt.getConnection().commit();
	} catch (SQLException e) {
	    // XXX: rollback and die
	    // Is there anything we can do here, other than weep?
	    e.printStackTrace();
	}

    }

    /**
     * {inheritDoc}
     */
    public Long lookup(String name) {
	long oid = DataSpace.INVALID_ID;

	try {
	    getNameStmnt.setString(1, name);
	    ResultSet rs = getNameStmnt.executeQuery();
	    getNameStmnt.getConnection().commit();
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
    public long getAppID() {
	return appID;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {

	// XXX: INCOMPLETE.
	// XXX: why does deleting the contents of tables take so long?

	try {
	    // lockObjTableStmnt.execute();
	    // lockNameTableStmnt.execute();
	    // clearObjTableStmnt.execute();
	    // conn.commit();

	    // clearTables();
	    // conn.commit();

	    System.out.println("clearing nameTable");
	    clearNameTableStmnt.getConnection().commit();
	    clearNameTableStmnt.execute();
	    clearNameTableStmnt.getConnection().commit();
	    System.out.println("cleared nameTable");
	} catch (SQLException e) {
	    e.printStackTrace();
	}
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
	closed = true;
    }

}
