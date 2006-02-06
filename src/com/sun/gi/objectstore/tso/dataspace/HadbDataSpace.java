/*
 * <p>Copyright: Copyright (c) 2006 Sun Microsystems, Inc.</p>
 */

package com.sun.gi.objectstore.tso.dataspace;

import java.lang.ref.SoftReference;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 * A {@link DataSpace} based on HADB/JDBC.
 */
public class HadbDataSpace implements DataSpace {

    private long appID;
    private Map<Long, SoftReference<byte[]>> dataSpace = new LinkedHashMap<Long, SoftReference<byte[]>>();
    private Map<String, Long> nameSpace = new LinkedHashMap<String, Long>();
    private Set<Long> lockSet = new HashSet<Long>();
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
    private static final String NAMEBASETBL = "namedirectory";
    private static final String INFOTBL = "appinfo";

    private static final String INFOTBLNAME = SCHEMA + "." + INFOTBL;

    private String NAMETBL;
    private String OBJTBL;
    private String NAMETBLNAME;
    private String OBJTBLNAME;

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
    private PreparedStatement deleteObjStmnt;
    private PreparedStatement clearObjTableStmnt;
    private PreparedStatement lockObjTableStmnt;
    private PreparedStatement clearNameTableStmnt;
    private PreparedStatement lockNameTableStmnt;

    private boolean closed = false;

    private boolean done = false;

    private Object closeWaitMutex = new Object();

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
	    conn.commit();
	    System.out.println("Dropped " + tableName);
	    return true;
	} catch (SQLException e) {
	    // XXX ?
	    System.out.println("FAILED to drop " + tableName);
	    return false;
	}
    }

    /**
     * @param newNames
     * @param updateList
     * @param deleteList
     */
     /*
    protected void doDiskUpdate(DiskUpdateRecord rec) {
	if (TRACEDISK){
	    System.out.println("      Starting commit");
	}
	if (TRACEDISK && (rec.newNames.length>0)){
	    System.out.println("      Starting inserts");
	}
	for (int i = 0; i < rec.newNames.length; i++) {
	    try {
		insertNameStmnt.setString(1, rec.newNames[i]);
		insertNameStmnt.setLong(2, rec.newNameIDs[i]);
		insertNameStmnt.execute();
	    } catch (SQLException e1) {
		e1.printStackTrace();
	    }
	}
	if (TRACEDISK && (rec.updateData.length>0)){
	    System.out.println("      Starting updates");
	}
	for (int i = 0; i < rec.updateData.length; i++) {
	    try {
		if (rec.insertSet.contains(rec.updateIDs[i])) { // new
		    insertObjStmnt.setLong(1, rec.updateIDs[i]);
		    insertObjStmnt.setBytes(2, rec.updateData[i]);
		    insertObjStmnt.execute();
		} else { // update
		    updateObjStmnt.setBytes(1, rec.updateData[i]);
		    updateObjStmnt.setLong(2, rec.updateIDs[i]);
		    updateObjStmnt.execute();
		}
	    } catch (SQLException e1) {
		e1.printStackTrace();
	    }
	}
	if (TRACEDISK && (rec.deleteIDs.length>0)){
	    System.out.println("      Starting deletes");
	}
	for (Long delid : rec.deleteIDs) {
	    try {
		deleteObjStmnt.setLong(1, delid);
		deleteObjStmnt.execute();
	    } catch (SQLException e1) {
		e1.printStackTrace();
	    }
	}
	try {
	    if (TRACEDISK){
		System.out.println("      Setting next ID = "+rec.nextID);
	    }
	    updateInfoStmnt.setLong(1, rec.nextID);
	    updateInfoStmnt.setLong(2, appID);
	    updateInfoStmnt.execute();
	} catch (SQLException e1) {
	    e1.printStackTrace();
	}
	try {
	    if (TRACEDISK){
		System.out.println("      COmitting trans");
	    }			
	    updateConn.commit();
	    if (TRACEDISK){
		System.out.println("      Trans comitted");
	    }
	} catch (SQLException e1) {
	    e1.printStackTrace();
	}
    }
    */

    /**
     * @throws SQLException
     */
    private void checkTables() throws SQLException {
	DatabaseMetaData md = conn.getMetaData();
	ResultSet rs;

	// XXX:  It is not possible, in HADB, to bring a schema into
	// existance simply by defining tables within it.  Therefore
	// we create it separately -- but this might fail, because the
	// schema might already exist.  There doesn't seem to be a
	// good way to distinguish this from a "real" failure, but
	// there ought to be something better than this!  -DJE

	try {
	    System.out.println("Creating Schema");
	    String s = "CREATE SCHEMA " + SCHEMA;
	    Statement stmnt = conn.createStatement();
	    stmnt.execute(s);
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
	    conn.commit();
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
	    conn.commit();
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
	    conn.commit();
	}

	getIdStmnt = idConn.prepareStatement("SELECT * FROM " +
		INFOTBLNAME + " I  " + "WHERE I.APPID = " + appID);
	rs = getIdStmnt.executeQuery();
	if (!rs.next()) { // entry does not exist
	    System.out.println("Creating new entry in info table for appID "
		    + appID);
	    PreparedStatement stmnt =
		    conn.prepareStatement("INSERT INTO " + INFOTBLNAME +
			" VALUES(" + appID + "," + defaultIdStart +
			    "," + defaultIdBlockSize + ")");
	    stmnt.execute();
	}
	rs.close();
	idConn.commit();
    }

    private void createPreparedStmnts() throws SQLException {

	getObjStmnt = conn.prepareStatement("SELECT * FROM " + OBJTBLNAME
			+ " O  " + "WHERE O.OBJID = ?");
	getNameStmnt = conn.prepareStatement("SELECT * FROM " + NAMETBLNAME
			+ " N  " + "WHERE N.NAME = ?");
	insertObjStmnt = updateConn.prepareStatement("INSERT INTO "
			+ OBJTBLNAME + " VALUES(?,?)");
	insertNameStmnt = updateConn.prepareStatement("INSERT INTO "
			+ NAMETBLNAME + " VALUES(?,?)");
	updateObjStmnt = updateConn.prepareStatement("UPDATE " + OBJTBLNAME
			+ " SET OBJBYTES=? WHERE OBJID=?");
	updateNameStmnt = updateConn.prepareStatement("UPDATE " + NAMETBLNAME
			+ " SET NAME=? WHERE OBJID=?");
	deleteObjStmnt = updateConn.prepareStatement("DELETE FROM "
			+ OBJTBLNAME + " WHERE OBJID = ?");
	updateInfoStmnt = updateConn.prepareStatement("UPDATE " + INFOTBLNAME
			+ " SET NEXTIDBLOCKBASE=? WHERE APPID=?");

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
	} catch (Exception e) {
	    // XXX
	}

	System.out.println("Creating Objects table");

	try { 
	    s = "CREATE TABLE " + OBJTBLNAME + " ("
		    + "OBJID DOUBLE INT NOT NULL," + "OBJBYTES BLOB,"
		    + "PRIMARY KEY (OBJID))";
	    stmnt = conn.prepareStatement(s);
	    stmnt.execute();
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
		    rs.close();

		    updateInfoStmnt.setLong(1, newIdBlockBase + newIdBlockSize);
		    updateInfoStmnt.setLong(2, appID);
		    try {
			updateInfoStmnt.execute();
			idConn.commit();
			success = true;
		    } catch (SQLException e) {
			if (e.getErrorCode() == 2097) {
			    success = false;
			    idConn.rollback();
			    try {
				Thread.sleep(2);
			    } catch (Exception e2) {
			    }
			}
			// System.out.println("YY " + e + " " + e.getErrorCode());
		    }
		}
	    } catch (SQLException e) {
		// XXX
		System.out.println("TT " + e + " " + e.getErrorCode());
		// e.printStackTrace();
	    }

	    currentIdBlockBase = newIdBlockBase;
	    currentIdBlockOffset = 0;
	}

	return (currentIdBlockBase + currentIdBlockOffset++);
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	byte[] objbytes = null;
	try {
	    getObjStmnt.setLong(1, objectID);
	    ResultSet rs = getObjStmnt.executeQuery();
	    conn.commit();
	    if (rs.next()) {
		objbytes = rs.getBytes("OBJBYTES");
	    }
	    rs.close(); // cleanup and free locks
	} catch (SQLException e) {
	    e.printStackTrace();
	}
	return objbytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID) throws NonExistantObjectIDException {

	// XXX: Do something.
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) {

	// XXX: Do something.
    }

    /**
     * {@inheritDoc}
     */
    public void atomicUpdate(boolean clear, Map<String, Long> newNames,
	    Set<Long> deleteSet, Map<Long, byte[]> updateMap, Set<Long> insertIDs)
	    throws DataSpaceClosedException
    {
	if (closed) {
		throw new DataSpaceClosedException();
	}
	synchronized (dataSpace) {
		synchronized (nameSpace) {
			for (Entry<Long, byte[]> e : updateMap.entrySet()) {
				dataSpace.put(e.getKey(), new SoftReference<byte[]>(e
						.getValue()));
			}
			nameSpace.putAll(newNames);
			for (Long id : deleteSet) {
				dataSpace.remove(id);
			}
		}
	}
	// asynchronously update the persistant storage
	// IMPORTANT: This update record will pin the objects in memory and thus
	// in the cache until it is complete This is VERY important so that
	// things
	// don't get cleaned out of the cache until they have been persisted.
	// It is acceptable to lose transactions, if the entire system dies, but
	// that
	// is the only time. Even in this case those lost must be atomic (all or
	// nothing.)

	Long[] nameIDs = new Long[newNames.values().size()];
	String[] names = new String[newNames.keySet().size()];
	int i = 0;
	for (Entry<String, Long> e : newNames.entrySet()) {
		nameIDs[i] = e.getValue();
		names[i++] = e.getKey();
	}
	Long[] deleteIDs = new Long[deleteSet.size()];
	i = 0;
	for (Long l : deleteSet) {
		deleteIDs[i++] = l;
	}
	Long[] updateIDs = new Long[updateMap.entrySet().size()];
	byte[][] updateData = new byte[updateMap.entrySet().size()][];
	i = 0;
	for (Entry<Long, byte[]> e : updateMap.entrySet()) {
		updateIDs[i] = e.getKey();
		updateData[i++] = e.getValue();
	}
	Set<Long> insertSet = new HashSet<Long>(insertIDs);
	/* DJE
	synchronized (diskUpdateQueue) {
		if(!closed){ //closed while we were processing
			if (TRACEDISK){
				System.out.println("Queuing commit #"+commitRegisterCounter++);
			}
			diskUpdateQueue.add(new DiskUpdateRecord(updateIDs, updateData,
				nameIDs, names, deleteIDs, id, insertSet));
			diskUpdateQueue.notifyAll();
		}
	}
	*/

    }

    /**
     * {inheritDoc}
     */
    public Long lookup(String name) {
	long oid = DataSpace.INVALID_ID;

	try {
	    getNameStmnt.setString(1, name);
	    ResultSet rs = getNameStmnt.executeQuery();
	    conn.commit();
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

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#clear()
     */
    public void clear() {
	    try {
/* 		    synchronized(diskUpdateQueue){ */
			    synchronized(dataSpace){
				    synchronized(nameSpace){
					    dataSpace.clear();
					    System.out.println("cleared dataSpace");
					    nameSpace.clear();
					    System.out.println("cleared nameSpace");
/* 					    diskUpdateQueue.clear(); */
					    System.out.println("cleared diskUpdateQueue");
					    // lockObjTableStmnt.execute();
					    // lockNameTableStmnt.execute();
					    // clearObjTableStmnt.execute();
					    // conn.commit();

					    clearTables();
					    // conn.commit();

					    System.out.println("cleared objTable");
					    clearNameTableStmnt.execute();
					    // conn.commit();
					    System.out.println("cleared nameTable");
					    
				    }
			    }
/* 		    } */

	    } catch (SQLException e) {
		    
		    e.printStackTrace();
	    }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#close()
     */
    public void close() {
	    
	/*
	    synchronized(diskUpdateQueue){
		    closed = true;
		    diskUpdateQueue.notifyAll();
	    }
	*/
	    synchronized (closeWaitMutex) {
		    while (!done) {
			    try {
				    closeWaitMutex.wait();
			    } catch (InterruptedException e) {
				    e.printStackTrace();
			    }
		    }
	    }
    }

}
