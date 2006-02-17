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

import java.lang.ref.SoftReference;
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

import com.sun.gi.objectstore.NonExistantObjectIDException;

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
		String[] newNames;

		Long[] newNameIDs;

		Long[] deleteIDs;

		byte[][] updateData;

		Long[] updateIDs;

		long nextID;

		private Set<Long> insertSet;

		/**
		 * @param updateIDs2
		 * @param updateData2
		 * @param nameIDs
		 * @param names
		 * @param deleteIDs2
		 * @param id
		 */
		public DiskUpdateRecord(Long[] updateIDs, byte[][] updateData,
				Long[] nameIDs, String[] names, Long[] deleteIDs, long id,
				Set<Long> insertSet) {
			this.newNames = names;
			this.newNameIDs = nameIDs;
			this.deleteIDs = deleteIDs;
			this.updateIDs = updateIDs;
			this.updateData = updateData;
			this.insertSet = insertSet;
			this.nextID = id;
		}

	}

	private long appID;

	private Map<Long, SoftReference<byte[]>> dataSpace = new LinkedHashMap<Long, SoftReference<byte[]>>();

	private Map<String, Long> nameSpace = new LinkedHashMap<String, Long>();

	private Set<Long> lockSet = new HashSet<Long>();

	private Object idMutex = new Object();

	private long id = 1;

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

	private Queue<DiskUpdateRecord> diskUpdateQueue = new LinkedList<DiskUpdateRecord>();

	private Object closeWaitMutex = new Object();

	private static final boolean TRACEDISK=true;
	
	private int commitRegisterCounter=1;

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
					int commitCount=1;
					while (true) {
						DiskUpdateRecord rec = null;
						synchronized (diskUpdateQueue) {
							if (diskUpdateQueue.isEmpty()) {
								if (closed) {
									break;
								} else {
									try {
										diskUpdateQueue.wait();
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
							} else {
								rec = diskUpdateQueue.remove();
							}
						}
						if (rec != null) {
							if (TRACEDISK){
								System.out.println("      Doing Commit #"+commitCount++);
							}
							doDiskUpdate(rec);
						}
					}
					synchronized (closeWaitMutex) {
						done=true;
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
		if (TRACEDISK){
			System.out.println("      Starting commit");
		}
		if (TRACEDISK && (rec.newNames.length>0)){
			System.out.println("      Starting inserts");
		}
		for (int i = 0; i < rec.newNames.length; i++) {
			if (TRACEDISK){
				System.out.println("          Inserting " + rec.newNameIDs[i]);
			}
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
			if (TRACEDISK){
				System.out.println("          Updating " + rec.updateIDs[i]);
			}
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
			if (TRACEDISK){
				System.out.println("          Deleting " + delid);
			}
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

	/**
	 * @throws SQLException
	 * 
	 */
	private void checkTables() throws SQLException {
		conn = getDataConnection();		
		updateConn = getDataConnection();	
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
				+ " SET NEXTOBJID=? WHERE APPID=?");
		lockObjTableStmnt = conn.prepareStatement("LOCK TABLE "+OBJTBLNAME+" IN EXCLUSIVE MODE");
		clearObjTableStmnt = conn.prepareStatement("DELETE FROM "+OBJTBLNAME);
		lockNameTableStmnt = conn.prepareStatement("LOCK TABLE "+NAMETBLNAME+" IN EXCLUSIVE MODE");
		clearNameTableStmnt = conn.prepareStatement("DELETE FROM "+NAMETBLNAME);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long,
	 *      long)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long,
	 *      long)
	 */
	public byte[] getObjBytes(long objectID) {
		byte[] objbytes = null;
		synchronized (dataSpace) {
			SoftReference<byte[]> ref = dataSpace.get(new Long(objectID));
			if (ref != null) {
				objbytes = ref.get();
				if (objbytes == null) { // ref dead
					dataSpace.remove(ref);
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
					dataSpace.put(new Long(objectID),
							new SoftReference<byte[]>(objbytes));
				}
				rs.close(); // cleanup and free locks
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return objbytes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
	 */
	public void lock(long objectID) throws NonExistantObjectIDException {
		synchronized (dataSpace) {
			if (!dataSpace.containsKey(objectID)) {
				if (loadCache(objectID) == null) {
					throw new NonExistantObjectIDException("Can't find objectID " + objectID);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	public void release(long objectID) {
		synchronized (lockSet) {
			lockSet.remove(new Long(objectID));
			lockSet.notifyAll();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long,
	 *      boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long,
	 *      boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	public void atomicUpdate(boolean clear, Map<String, Long> newNames,
			Set<Long> deleteSet, Map<Long, byte[]> updateMap, Set<Long> insertIDs)
			throws DataSpaceClosedException {
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
			synchronized(diskUpdateQueue){
				synchronized(dataSpace){
					synchronized(nameSpace){
						dataSpace.clear();
						nameSpace.clear();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#close()
	 */
	public void close() {
		
		synchronized(diskUpdateQueue){
			closed = true;
			diskUpdateQueue.notifyAll();
		}
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
