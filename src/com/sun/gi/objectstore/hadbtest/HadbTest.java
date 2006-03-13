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

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceClosedException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

public class HadbTest {

    Object dataSpace = new Object();
    private long appID;
    private Map<String, Long> nameSpace = new LinkedHashMap<String, Long>();
    private Set<Long> lockSet = new HashSet<Long>();
    private Object idMutex = new Object();
    private String dataConnURL;

    private long id = DataSpace.INVALID_ID;

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

    public HadbTest(long appID) throws Exception {
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
	    String dbaseName = "darkstar";
	    String userName = "system";
	    String userPasswd = "darkstar";

	    // dataConnURL = "jdbc:sun:hadb:" + dbaseName + ":" +
	    // 	    hadbHosts + ";create=true";
	    dataConnURL = "jdbc:sun:hadb:" +
		    hadbHosts + ";create=true";

	    // XXX:  Do we really need two distinct connections for
	    // data and update?

	    conn = getConnection(userName, userPasswd,
		    Connection.TRANSACTION_READ_COMMITTED);
	    conn.setAutoCommit(true);
	    // conn.setTransactionIsolation(isolation);

	    updateConn = getConnection(userName, userPasswd,
		    Connection.TRANSACTION_READ_COMMITTED);
	    idConn = getConnection(userName, userPasswd,
		    Connection.TRANSACTION_REPEATABLE_READ);

	    checkTables();

	    conn.setAutoCommit(true);

	} catch (Exception e) {
	    e.printStackTrace();
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
	    String s = "CREATE SCHEMA " + SCHEMA;
	    PreparedStatement stmnt = conn.prepareStatement(s);
	    stmnt.execute();
	} catch (Exception e) {
	    System.out.println("SCHEMA issue: " + e);
	}

	HashSet<String> foundTables = new HashSet<String>();

	try {
	    String s = "select tablename from sysroot.alltables" +
		    " where schemaname like '" + SCHEMA + "%'";

	    // System.out.println("(" + s + ")");

	    PreparedStatement stmnt = conn.prepareStatement(s);
	    rs = stmnt.executeQuery();
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
	    PreparedStatement stmnt = conn.prepareStatement(
		    "CREATE TABLE " + OBJTBLNAME + " (" +
			"OBJID DOUBLE INT NOT NULL," +
			"OBJBYTES BLOB," +
			"PRIMARY KEY (OBJID)" +
			")");
	    stmnt.execute();
	}

	if (foundTables.contains(NAMETBL.toLowerCase())) {
	    System.out.println("Found Name table");
	} else {
	    System.out.println("Creating Name table");
	    PreparedStatement stmnt = conn.prepareStatement(
		    "CREATE TABLE " + NAMETBLNAME + "(" +
			"NAME VARCHAR(255) NOT NULL, " +
			"OBJID DOUBLE INT NOT NULL," +
			"PRIMARY KEY (NAME)" +
			")");
		stmnt.execute();
	}

	if (foundTables.contains(INFOTBL.toLowerCase())) {
	    System.out.println("Found info table");
	} else {
	    System.out.println("Creating info table");
	    PreparedStatement stmnt = conn.prepareStatement(
		    "CREATE TABLE " + INFOTBLNAME + "(" +
			"APPID DOUBLE INT NOT NULL," +
			"NEXTOBJID DOUBLE INT," +
			"PRIMARY KEY(APPID)" +
			")");
	    stmnt.execute();
	}

	getIdStmnt = idConn.prepareStatement("SELECT * FROM " +
		INFOTBLNAME + " I  " + "WHERE I.APPID = " + appID);
	rs = getIdStmnt.executeQuery();
	if (!rs.next()) { // entry does not exist
	    System.out.println("Creating new entry in info table for appID "
		    + appID);
	    PreparedStatement stmnt =
		    conn.prepareStatement("INSERT INTO " + INFOTBLNAME +
			" VALUES(" + appID + "," + id + ")");
	    stmnt.execute();
	} else {
	    System.out.println("Found entry in info table for appID " + appID);
	    System.out.println("Next objID = " + rs.getLong("NEXTOBJID"));
	}
	rs.close();
	idConn.commit();

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

	// XXX: was updateConn.
	updateInfoStmnt = idConn.prepareStatement("UPDATE " + INFOTBLNAME
			+ " SET NEXTOBJID=? WHERE APPID=?");

	// XXX: HADB does not implement table locking.
	// So, we NEED another way to do this.

	// lockObjTableStmnt = conn.prepareStatement("LOCK TABLE "+OBJTBLNAME+" IN EXCLUSIVE MODE");
	clearObjTableStmnt = conn.prepareStatement("DELETE FROM "+OBJTBLNAME);
	// lockNameTableStmnt = conn.prepareStatement("LOCK TABLE "+NAMETBLNAME+" IN EXCLUSIVE MODE");
	clearNameTableStmnt = conn.prepareStatement("DELETE FROM "+NAMETBLNAME);
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


    // internal routines to the system, used by transactions

    // purposefully very small, for debugging.  Should be at least
    // 1000 for real use.

    private long idBlockSize = 2;

    // dummy: should be initialized from the database.
    private long currentIdBlockBase = idBlockSize;

    private long currentIdBlockOffset = 0;

    /**
     * {@inheritDoc}
     */
    public synchronized long getNextID() {

	/*
	 * In order to minimize the overhead of creating new object IDs,
	 * we allocate them in blocks rather than individually.  If there
	 * are still remaining object Ids available in the current block
	 * owned by this reference to the DataSpace, then the next such
	 * Id is allocated and returned.  Otherwise, a new block is accessed
	 * from the database.
	 */

	long newIdBlockBase = 0;

	if ((id == DataSpace.INVALID_ID) || (currentIdBlockOffset >= idBlockSize)) {
	    try {
		boolean success = false;

		while (!success) {
		    ResultSet rs = getIdStmnt.executeQuery();
		    if (!rs.next()) { // entry does not exist
			System.out.println("appID table entry absent for " + appID);
			// XXX?
		    }
		    newIdBlockBase = rs.getLong("NEXTOBJID"); // "2"
		    rs.close();

		    try {
			Thread.sleep(2);
		    } catch (Exception e) {
		    }

		    updateInfoStmnt.setLong(1, newIdBlockBase + idBlockSize);
		    updateInfoStmnt.setLong(2, appID);
		    try {
			updateInfoStmnt.execute();
			success = true;
		    } catch (SQLException e) {
			if (e.getErrorCode() == 2097) {
			    success = false;
			    idConn.rollback();
			}
			// System.out.println("YY " + e + " " + e.getErrorCode());
		    }

		    if (success) {
			try {
			    idConn.commit();
			} catch (SQLException e) {
			    System.out.println("XX " + e + " " + e.getErrorCode());
			    e.printStackTrace();
			}

			// System.out.println("newIdBlockBase = " + newIdBlockBase);
		    }
		}
	    } catch (SQLException e) {
		// XXX
		System.out.println("TT " + e + " " + e.getErrorCode());
		// e.printStackTrace();
	    }

	    currentIdBlockBase = newIdBlockBase;
	    currentIdBlockOffset = 0;
	    id = currentIdBlockBase;
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
	/*
	synchronized (dataSpace) {
	    if (!dataSpace.containsKey(objectID)) {
		if (loadCache(objectID) == null) {
			throw new NonExistantObjectIDException();
		}
	    }
	}
	synchronized (lockSet) {
	    while (lockSet.contains(objectID)) {
		try {
		    lockSet.wait();
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
	    }
	    lockSet.add(new Long(objectID));
	}
	*/
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) {
	synchronized (lockSet) {
	    lockSet.remove(new Long(objectID));
	    lockSet.notifyAll();
	}
    }

    /**
     * {@inheritDoc}
     */
    /*
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
    }
    */

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lookup(java.lang.String)
     */
    public Long lookup(String name) {
	    Long retval = null;
	    synchronized (nameSpace) {
		    retval = nameSpace.get(name);
		    if (retval == null) {
			    retval = loadNameCache(name);
		    }
	    }
	    return retval;
    }

    /**
     * @param name
     * @return
     */
    private Long loadNameCache(String name) {
	    long retval = DataSpace.INVALID_ID;
	    synchronized (nameSpace) {
		    try {
			    getNameStmnt.setString(1, name);
			    ResultSet rs = getNameStmnt.executeQuery();
			    conn.commit();
			    if (rs.next()) {
				    retval = rs.getLong("OBJID");
				    nameSpace.put(name, new Long(retval));
			    }
			    rs.close();
		    } catch (SQLException e) {
			    e.printStackTrace();
		    }
	    }
	    return retval;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getAppID()
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
					    // dataSpace.clear();
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
