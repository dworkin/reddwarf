/**
 *
 * <p>Title: InMemoryDataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import java.lang.ref.SoftReference;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 
 * <p>
 * Title: InMemoryDataSpace.java
 * </p>
 * <p>
 * Description: This is a version of the InMemoryDataSpace that asynchronously
 * backs itself up to a Derby on-disc database.
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PersistantInMemoryDataSpace implements DataSpace {

	class DiskUpdateRecord {

		byte[][] updateData;

		Long[] updateIDs;

		long nextID;

		/**
		 * @param updateIDs2
		 * @param updateData2
		 * @param nameIDs
		 * @param names
		 * @param deleteIDs2
		 * @param id
		 */
		public DiskUpdateRecord(Long[] updateIDs, byte[][] updateData, long id) {

			this.updateIDs = updateIDs;
			this.updateData = updateData;
			this.nextID = id;
		}

	}

	private long appID;

	private Map<Long, SoftReference<byte[]>> dataSpace = new LinkedHashMap<Long, SoftReference<byte[]>>();

	private Map<String, Long> nameSpace = new LinkedHashMap<String, Long>();
	private Map<Long,String> reverseNameSpace = new HashMap<Long,String>();

	private Set<Long> lockSet = new HashSet<Long>();

	private Object idMutex = new Object();
	private Object diskUpdateQueueMutex = new Object();

	private volatile long id = 1;

	private String dataConnURL;

	private static final String SCHEMA = "SIMSERVER";

	private static final String OBJBASETBL = "OBJECTS";

	private static final String NAMEBASETBL = "NAMEDIRECTORY";

	private static final String INFOTBL = "APPINFO";

	private static final String INFOTBLNAME = SCHEMA + "." + INFOTBL;

	private String NAMETBL;

	private String NAMETBLNAME;

	private String OBJTBL;

	private String OBJTBLNAME;

	private Connection conn;

	private Connection updateConn;

	private Connection deleteInsertConn;

	private PreparedStatement getObjStmnt;

	private PreparedStatement getNameStmnt;

	private PreparedStatement updateObjStmnt;

	private PreparedStatement insertObjStmnt;

	private PreparedStatement updateNameStmnt;

	private PreparedStatement insertNameStmnt;

	private PreparedStatement updateInfoStmnt;

	private PreparedStatement insertInfoStmnt;

	private PreparedStatement deleteObjStmnt;
	private PreparedStatement deleteNameStmnt;

	private PreparedStatement clearObjTableStmnt;

	private PreparedStatement lockObjTableStmnt;

	private PreparedStatement clearNameTableStmnt;

	private PreparedStatement lockNameTableStmnt;

	private volatile boolean closed = false;

	private volatile boolean done = false;

	private volatile LinkedList<DiskUpdateRecord> diskUpdateQueue = new LinkedList<DiskUpdateRecord>();

	private Object closeWaitMutex = new Object();

	private static final boolean TRACEDISK = false;

	private volatile int commitRegisterCounter = 1;

	public PersistantInMemoryDataSpace(long appID) {
		this.appID = appID;
		OBJTBL = OBJBASETBL + "_" + appID;
		OBJTBLNAME = SCHEMA + "." + OBJTBL;
		NAMETBL = NAMEBASETBL + "_" + appID;
		NAMETBLNAME = SCHEMA + "." + NAMETBL;
		try {
			// ****** Load embedded Derby JDBC Driver *********
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
			String derbyDB = System.getProperty("ostore.derby.datasource");
			if (derbyDB == null) {
				derbyDB = "persistant_store";
			}
			// create if necessary
			dataConnURL = "jdbc:derby:" + derbyDB + ";create=true";
			// objectIDManager = new SharedDataObjectIDManager(
			// new JRMSSharedDataManager(),this);
			checkTables();

			// start update thread
			new Thread(new Runnable() {
				public void run() {
					int commitCount = 1;
					while (true) {
						LinkedList<DiskUpdateRecord> recList = null;
						synchronized (diskUpdateQueueMutex) {
							if (diskUpdateQueue.isEmpty()) {
								recList = null;
								if (closed) {
									break;
								} else {
									try {
										diskUpdateQueueMutex.wait();
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
							} else {
								if (diskUpdateQueue.size() > 200) {
									System.out.println(
										"GRAVE-WARNING: diskUpdateQueue size : " +
											diskUpdateQueue.size());
								} else if (diskUpdateQueue.size() > 100) {
									System.out.println(
										"WARNING: diskUpdateQueue size : " +
											diskUpdateQueue.size());
								}

								recList = diskUpdateQueue;
								diskUpdateQueue = new LinkedList<DiskUpdateRecord>();
							}
						}
						if (recList != null) {
							while (recList.size() > 0) {
								DiskUpdateRecord rec = recList.remove();
								if (TRACEDISK) {
									System.out.println("      Doing Commit #"
											+ commitCount++);
								}
								doDiskUpdate(rec);
							}
						}
					}
					synchronized (closeWaitMutex) {
						done = true;
						closeWaitMutex.notifyAll();
					}
				}
			}).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param newNames
	 * @param updateList
	 * @param deleteList
	 */
	protected void doDiskUpdate(DiskUpdateRecord rec) {
		if (TRACEDISK) {
			System.out.println("      Starting commit");
		}

		if (TRACEDISK && (rec.updateData.length > 0)) {
			System.out.println("      Starting updates");
		}
		for (int i = 0; i < rec.updateData.length; i++) {
			if (TRACEDISK) {
				System.out.println("          Updating " + rec.updateIDs[i]);
			}
			try { // update
				updateObjStmnt.setBytes(1, rec.updateData[i]);
				updateObjStmnt.setLong(2, rec.updateIDs[i]);
				updateObjStmnt.execute();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}

		try {
			if (TRACEDISK) {
				System.out.println("      Setting next ID = " + rec.nextID);
			}
			updateInfoStmnt.setLong(1, rec.nextID);
			updateInfoStmnt.setLong(2, appID);
			updateInfoStmnt.execute();
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		try {
			if (TRACEDISK) {
				System.out.println("      COmitting trans");
			}
			updateConn.commit();
			if (TRACEDISK) {
				System.out.println("      Trans comitted");
			}
		} catch (SQLException e1) {

			e1.printStackTrace();
		}
	}

	/**
	 * @throws SQLException
	 * 
	 */
	private void checkTables() throws SQLException {
		conn = getDataConnection();
		updateConn = getDataConnection();
		deleteInsertConn = getDataConnection();
		DatabaseMetaData md = conn.getMetaData();
		ResultSet rs = md.getTables(null, SCHEMA, OBJTBL, null);
		if (rs.next()) {
			System.out.println("Found Objects table");
		} else {
			System.out.println("Creating Objects table");
			String s = "CREATE TABLE " + OBJTBLNAME + " ("
					+ "OBJID BIGINT NOT NULL," + "OBJBYTES BLOB,"
					+ "PRIMARY KEY (OBJID))";
			PreparedStatement stmnt = conn.prepareStatement(s);
			stmnt.execute();
		}
		rs.close();
		rs = md.getTables(null, SCHEMA, NAMETBL, null);
		if (rs.next()) {
			System.out.println("Found Name table");
		} else {
			System.out.println("Creating Name table");
			PreparedStatement stmnt = conn.prepareStatement("CREATE TABLE "
					+ NAMETBLNAME + "("
					+ "NAME VARCHAR(255) NOT NULL, OBJID BIGINT NOT NULL,"
					+ "PRIMARY KEY (NAME))");
			stmnt.execute();
		}
		rs.close();
		rs = md.getTables(null, SCHEMA, INFOTBL, null);
		if (rs.next()) {
			System.out.println("Found info table");
		} else {
			System.out.println("Creating info table");
			PreparedStatement stmnt = conn.prepareStatement("CREATE TABLE "
					+ INFOTBLNAME + "(" + "APPID BIGINT NOT NULL,"
					+ "NEXTOBJID BIGINT," + "PRIMARY KEY(APPID))");
			stmnt.execute();
		}
		PreparedStatement stmnt = conn.prepareStatement("SELECT * FROM "
				+ INFOTBLNAME + " I  " + "WHERE I.APPID = " + appID);
		rs = stmnt.executeQuery();
		if (!rs.next()) { // entry does not exist
			System.out.println("Creating new entry in info table for appID "
					+ appID);
			stmnt = conn.prepareStatement("INSERT INTO " + INFOTBLNAME
					+ " VALUES(" + appID + "," + id + ")");
			stmnt.execute();
		} else {
			id = rs.getLong("NEXTOBJID");
			System.out.println("Found entry in info table for appID " + appID);
			System.out.println("Next objID = " + id);
		}
		rs.close();
		conn.commit();
		getObjStmnt = conn.prepareStatement("SELECT * FROM " + OBJTBLNAME
				+ " O  " + "WHERE O.OBJID = ?");
		getNameStmnt = conn.prepareStatement("SELECT * FROM " + NAMETBLNAME
				+ " N  " + "WHERE N.NAME = ?");
		insertObjStmnt = deleteInsertConn.prepareStatement("INSERT INTO "
				+ OBJTBLNAME + " VALUES(?,?)");
		insertNameStmnt = deleteInsertConn.prepareStatement("INSERT INTO "
				+ NAMETBLNAME + " VALUES(?,?)");
		updateObjStmnt = updateConn.prepareStatement("UPDATE " + OBJTBLNAME
				+ " SET OBJBYTES=? WHERE OBJID=?");
		updateNameStmnt = updateConn.prepareStatement("UPDATE " + NAMETBLNAME
				+ " SET NAME=? WHERE OBJID=?");
		deleteObjStmnt = deleteInsertConn.prepareStatement("DELETE FROM "
				+ OBJTBLNAME + " WHERE OBJID = ?");
		deleteNameStmnt = deleteInsertConn.prepareStatement("DELETE FROM "
				+ NAMETBLNAME + " WHERE OBJID = ?");
		updateInfoStmnt = updateConn.prepareStatement("UPDATE " + INFOTBLNAME
				+ " SET NEXTOBJID=? WHERE APPID=?");
		lockObjTableStmnt = conn.prepareStatement("LOCK TABLE " + OBJTBLNAME
				+ " IN EXCLUSIVE MODE");
		clearObjTableStmnt = conn.prepareStatement("DELETE FROM " + OBJTBLNAME);
		lockNameTableStmnt = conn.prepareStatement("LOCK TABLE " + NAMETBLNAME
				+ " IN EXCLUSIVE MODE");
		clearNameTableStmnt = conn.prepareStatement("DELETE FROM "
				+ NAMETBLNAME);
	}

	/**
	 * @return
	 */
	private Connection getDataConnection() {
		Connection conn;
		try {
			conn = DriverManager.getConnection(dataConnURL);
			// may want to put username/password
			conn.setAutoCommit(false);
			conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			// CallableStatement cs = conn.prepareCall("{CALL
			// ttLockLevel('Row')}");
			// cs.execute();
			// cs = conn.prepareCall("{CALL ttLockWait(0)}");
			// cs.execute();
			return conn;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// internal routines to the system, used by transactions
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	public long getNextID() {
		synchronized (idMutex) {
			return id++;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public byte[] getObjBytes(long objectID) {
		byte[] objbytes = null;
		synchronized (dataSpace) {
			SoftReference<byte[]> ref = dataSpace.get(objectID);
			if (ref != null) {
				objbytes = ref.get();
				if (objbytes == null) { // ref dead
					dataSpace.remove(objectID);
				}
			}
			if (objbytes == null) {
				objbytes = loadCache(objectID);
			}
		}
		return objbytes;

	}

	private byte[] loadCache(long objectID) {
		byte[] objbytes = null;
		synchronized (dataSpace) {
			try {
				getObjStmnt.setLong(1, objectID);
				ResultSet rs = getObjStmnt.executeQuery();
				conn.commit();
				if (rs.next()) {
					objbytes = rs.getBytes("OBJBYTES");
					dataSpace.put(objectID,
							new SoftReference<byte[]>(objbytes));
				}
				if (objbytes == null) {
					System.out.println("GOT A NULL OBJBYTES in loadCache " + objectID);
				}
				rs.close(); // cleanup and free locks
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return objbytes;
	}

	/**
	 * {@inheritDoc}
	 */
	public void lock(long objectID) throws NonExistantObjectIDException {
		synchronized (dataSpace) {
			if (!dataSpace.containsKey(objectID)) {
				if (loadCache(objectID) == null) {
					throw new NonExistantObjectIDException(
							"Can't find objectID " + objectID);
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
	}

	/**
	 * {@inheritDoc}
	 */
	public void release(long objectID)
			throws NonExistantObjectIDException
	{
		synchronized (lockSet) {
			lockSet.remove(new Long(objectID));
			lockSet.notifyAll();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	public void release(Set<Long> objectIDs)
			throws NonExistantObjectIDException
	{
	    NonExistantObjectIDException re = null;

	    for (long oid : objectIDs) {
		try {
		    release(oid);
		} catch (NonExistantObjectIDException e) {
		    re = e;
		}
	    }

	    // If any of the releases threw an exception, throw it
	    // here.

	    if (re != null) {
		throw re;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap)
			throws DataSpaceClosedException {
		if (closed) {
			throw new DataSpaceClosedException();
		}
		synchronized (dataSpace) {

			for (Entry<Long, byte[]> e : updateMap.entrySet()) {
				dataSpace.put(e.getKey(),
					new SoftReference<byte[]>(e.getValue()));
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

		Long[] updateIDs = new Long[updateMap.entrySet().size()];
		byte[][] updateData = new byte[updateMap.entrySet().size()][];
		int i = 0;
		for (Entry<Long, byte[]> e : updateMap.entrySet()) {
			updateIDs[i] = e.getKey();
			updateData[i++] = e.getValue();
		}

		/*
		 * Try to throttle back:  if the queue is shorter than
		 * 40, let it go.  If the queue is 40-80, then sleep
		 * for 20ms, which should be long enough to move
		 * something off the queue (by rough guess), leaving
		 * the system in steady state.  As the queue gets
		 * longer, back off more and more aggressively to
		 * allow the system to catch up.
		 *
		 * Note that because the queue is swallowed whole by
		 * the draining thread it may appear to instantly go
		 * to zero length.  This should not have any impact on
		 * this heuristic, because we care how long the queue
		 * gets before the switch.
		 */

		int queueLength;
		synchronized (diskUpdateQueueMutex) {
			queueLength = diskUpdateQueue.size();
		}

		try {
			if (queueLength > 150) {
				System.out.println("\t\tXXX XXX XXX XXX falling behind " + queueLength);
				Thread.sleep(80);
			} else if (queueLength > 100) {
				// System.out.println("\t\tXXX XXX XXX falling behind " + queueLength);
				Thread.sleep(55);
			} else if (queueLength > 70) {
				// System.out.println("\t\tXXX XXX falling behind " + queueLength);
				Thread.sleep(35);
			} else if (queueLength > 50) {
				// System.out.println("\t\tXXX falling behind " + queueLength);
				Thread.sleep(20);
			} else {
				// Carry on.
			}
		} catch (Exception e) {
		}

		synchronized (diskUpdateQueueMutex) {
			if (!closed) { // closed while we were processing
				if (TRACEDISK) {
					System.out.println("Queuing commit #"
							+ commitRegisterCounter++);
				}
				diskUpdateQueue.add(new DiskUpdateRecord(updateIDs, updateData,
						id));
				diskUpdateQueueMutex.notifyAll();
			}
		}

	}

	/**
	 * {@inheritDoc}
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
		Long retval = null;
		synchronized (nameSpace) {
			try {
				getNameStmnt.setString(1, name);
				ResultSet rs = getNameStmnt.executeQuery();
				conn.commit();
				if (rs.next()) {
					retval = rs.getLong("OBJID");
					nameSpace.put(name, retval);
				}
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return retval;
	}

	/**
	 * {@inheritDoc}
	 */
	public long getAppID() {
		return appID;
	}

	/**
	 * {@inheritDoc}
	 */
	public void clear() {
		try {
			synchronized (diskUpdateQueueMutex) {
				synchronized (dataSpace) {
					synchronized (nameSpace) {
						dataSpace.clear();
						nameSpace.clear();
						reverseNameSpace.clear();
						diskUpdateQueue.clear();
						lockObjTableStmnt.execute();
						lockNameTableStmnt.execute();
						clearObjTableStmnt.execute();
						clearNameTableStmnt.execute();
						conn.commit();

					}
				}
			}

		} catch (SQLException e) {

			e.printStackTrace();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	public void close() {

		synchronized (diskUpdateQueueMutex) {
			closed = true;
			diskUpdateQueueMutex.notifyAll();
		}
		synchronized (closeWaitMutex) {
			while (!done) {
				try {
					closeWaitMutex.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			try {
			    conn.close();
			} catch (SQLException e) {
			    // XXX:
			}

			try {
			    updateConn.close();
			} catch (SQLException e) {
			    // XXX:
			}

			try {
			    deleteInsertConn.close();
			} catch (SQLException e) {
			    // XXX:
			}

			diskUpdateQueue.clear();
			diskUpdateQueue = null;

			dataSpace.clear();
			dataSpace = null;

			nameSpace.clear();
			nameSpace = null;

			reverseNameSpace.clear();
			reverseNameSpace = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public long create(byte[] data, String name) {
		Long id;
		if (name != null) {
			synchronized (nameSpace) {
				id = lookup(name);
				if (id != null) {
					return DataSpace.INVALID_ID;
				}

				id = new Long(getNextID());
				nameSpace.put(name, id);
				reverseNameSpace.put(id,name);
			}
			try {
				insertNameStmnt.setString(1, name);
				insertNameStmnt.setLong(2, id);
				insertNameStmnt.execute();
			} catch (SQLException e) {
				e.printStackTrace();
				return DataSpace.INVALID_ID;
			}
		} else {
			id = new Long(getNextID());
		}
		synchronized (dataSpace) {
			dataSpace.put(id, new SoftReference<byte[]>(data));
		}
		try {
			insertObjStmnt.setLong(1, id);
			insertObjStmnt.setBytes(2, data);
			insertObjStmnt.execute();
			deleteInsertConn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			return DataSpace.INVALID_ID;
		}
		return id;
	}

	/**
	 * {@inheritDoc}
	 */
	public void destroy(long objectID) {
		synchronized(nameSpace){
			String name = reverseNameSpace.get(objectID);
			if (name!=null){
				reverseNameSpace.remove(objectID);
				nameSpace.remove(name);
			}
		}
		synchronized(dataSpace){
			dataSpace.remove(objectID);
		}
		synchronized(deleteInsertConn){
			try {
				deleteObjStmnt.setLong(1,objectID);
				deleteObjStmnt.execute();
				deleteNameStmnt.setLong(1,objectID);
				deleteNameStmnt.execute();
				deleteInsertConn.commit();
			} catch (SQLException e) {
				
				e.printStackTrace();
			}
			
		}
	}
}
