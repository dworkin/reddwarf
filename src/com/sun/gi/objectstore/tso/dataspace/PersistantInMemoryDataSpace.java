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

import java.lang.ref.SoftReference;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 * PersistantInMemoryDataSpace is a variation on InMemoryDataSpace that
 * asynchronously backs itself up to a Derby on-disc database.
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PersistantInMemoryDataSpace implements DataSpace {

    private static Logger log =
	    Logger.getLogger("com.sun.gi.objectstore.tso.dataspace");

    class DiskUpdateRecord {
        byte[][] updateDataRef;
        byte[][] updateDataCopy;
        long[] updateIDs;
	long[] deletedIDs;

        long nextID;

        /**
         * @param updateIDs2
         * @param updateData2
         * @param nameIDs
         * @param names
         * @param deleteIDs2
         * @param id
         */
        public DiskUpdateRecord(long[] updateIDs,
		byte[][] updateDataRef, byte[][] updateDataCopy,
		long[] deletedIDs, long id) {

            this.updateIDs = updateIDs;
            this.updateDataRef = updateDataRef;
            this.updateDataCopy = updateDataCopy;
	    this.deletedIDs = deletedIDs;

	    synchronized (idMutex) {
		this.nextID = id;
	    }
        }
    }

    private long appID;

    private Map<Long, SoftReference<byte[]>> dataSpace =
	    new LinkedHashMap<Long, SoftReference<byte[]>>();

    private Map<String, Long> nameSpace = new LinkedHashMap<String, Long>();
    private Map<Long, String> reverseNameSpace = new HashMap<Long, String>();

    private Set<Long> lockSet = new HashSet<Long>();

    /*
     * The graveyard is where oids go to die before are removed from
     * the database.
     */
    private Set<Long> graveyard = new HashSet<Long>();

    private Object idMutex = new Object();
    Object diskUpdateQueueMutex = new Object();

    private Object cachedStateMutex = new Object();


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

    private PreparedStatement insertNameStmnt;
    private PreparedStatement updateInfoStmnt;
    private PreparedStatement deleteObjStmnt;
    private PreparedStatement deleteNameStmnt;
    private PreparedStatement deleteNameByOIDStmnt;
    private PreparedStatement clearObjTableStmnt;
    private PreparedStatement lockObjTableStmnt;
    private PreparedStatement clearNameTableStmnt;
    private PreparedStatement lockNameTableStmnt;

    volatile boolean closed = false;
    volatile boolean done = false;

    volatile LinkedList<DiskUpdateRecord> diskUpdateQueue =
	    new LinkedList<DiskUpdateRecord>();

    Object closeWaitMutex = new Object();

    private static final boolean TRACEDISK = false;

    private volatile int commitRegisterCounter = 1;

    private boolean debug = true;

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
                                    log.severe("GRAVE-WARNING: diskUpdateQueue size : "
                                            + diskUpdateQueue.size());
                                } else if (diskUpdateQueue.size() > 100) {
                                    log.severe("WARNING: diskUpdateQueue size : "
                                            + diskUpdateQueue.size());
                                }

                                recList = diskUpdateQueue;
                                diskUpdateQueue = new LinkedList<DiskUpdateRecord>();
                            }
                        }
                        if (recList != null) {
                            while (recList.size() > 0) {
                                DiskUpdateRecord rec = recList.remove();
                                if (TRACEDISK) {
                                    log.finer("      Doing Commit #"
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

    protected void doDiskUpdate(DiskUpdateRecord rec) {
        if (TRACEDISK) {
            log.finer("      Starting commit");
        }

        if (TRACEDISK && (rec.updateDataRef.length > 0)) {
            log.finest("      Starting updates");
        }
        for (int i = 0; i < rec.updateDataRef.length; i++) {
            if (TRACEDISK) {
                log.finest("          Updating " + rec.updateIDs[i]);
            }
            try { // update
                updateObjStmnt.setBytes(1, rec.updateDataRef[i]);
                updateObjStmnt.setLong(2, rec.updateIDs[i]);
                updateObjStmnt.execute();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        }

	if (debug) {
	    for (int i = 0; i < rec.updateDataRef.length; i++) {
		if (!Arrays.equals(rec.updateDataCopy[i],
			rec.updateDataRef[i])) {
		    log.severe("Assumption violated: data changed: oid = " +
			    rec.updateIDs[i]);
		}
	    }
	}

	for (int i = 0; i < rec.deletedIDs.length; i++) {
            if (TRACEDISK) {
                log.finest("          Deleting " + rec.deletedIDs[i]);
            }
	    destroy(rec.deletedIDs[i]);
	    graveyard.remove(rec.deletedIDs[i]);
	}

        try {
            if (TRACEDISK) {
                log.finest("      Setting next ID = " + rec.nextID);
            }
            updateInfoStmnt.setLong(1, rec.nextID);
            updateInfoStmnt.setLong(2, appID);
            updateInfoStmnt.execute();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
        try {
            if (TRACEDISK) {
                log.finer("      Comitting trans");
            }
            updateConn.commit();
            if (TRACEDISK) {
                log.finer("      Trans comitted");
            }
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * @throws SQLException
     */
    private void checkTables() throws SQLException {
        conn = getDataConnection();
        updateConn = getDataConnection();
        deleteInsertConn = getDataConnection();
        DatabaseMetaData md = conn.getMetaData();
        ResultSet rs = md.getTables(null, SCHEMA, OBJTBL, null);
        if (rs.next()) {
            log.finer("Found Objects table");
        } else {
            log.finer("Creating Objects table");
            String s = "CREATE TABLE " + OBJTBLNAME + " ("
                    + "OBJID BIGINT NOT NULL," + "OBJBYTES BLOB,"
                    + "PRIMARY KEY (OBJID))";
            PreparedStatement stmnt = conn.prepareStatement(s);
            stmnt.execute();
        }
        rs.close();
        rs = md.getTables(null, SCHEMA, NAMETBL, null);
        if (rs.next()) {
            log.finer("Found Name table");
        } else {
            log.finer("Creating Name table");
            PreparedStatement stmnt = conn.prepareStatement("CREATE TABLE "
                    + NAMETBLNAME + "("
                    + "NAME VARCHAR(255) NOT NULL, OBJID BIGINT NOT NULL,"
                    + "PRIMARY KEY (NAME))");
            stmnt.execute();
        }
        rs.close();
        rs = md.getTables(null, SCHEMA, INFOTBL, null);
        if (rs.next()) {
            log.finer("Found info table");
        } else {
            log.finer("Creating info table");
            PreparedStatement stmnt = conn.prepareStatement("CREATE TABLE "
                    + INFOTBLNAME + "(" + "APPID BIGINT NOT NULL,"
                    + "NEXTOBJID BIGINT," + "PRIMARY KEY(APPID))");
            stmnt.execute();
        }
        PreparedStatement stmnt = conn.prepareStatement("SELECT * FROM "
                + INFOTBLNAME + " I  " + "WHERE I.APPID = " + appID);
        rs = stmnt.executeQuery();
        if (!rs.next()) { // entry does not exist
            log.finer("Creating new entry in info table for appID "
                    + appID);
            stmnt = conn.prepareStatement("INSERT INTO " + INFOTBLNAME
                    + " VALUES(" + appID + "," + id + ")");
            stmnt.execute();
        } else {
            id = rs.getLong("NEXTOBJID");
            log.finer("Found entry in info table for appID " + appID);
            log.finer("Next objID = " + id);
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
        deleteObjStmnt = deleteInsertConn.prepareStatement("DELETE FROM "
                + OBJTBLNAME + " WHERE OBJID = ?");
        deleteNameByOIDStmnt = deleteInsertConn.prepareStatement("DELETE FROM "
                + NAMETBLNAME + " WHERE OBJID = ?");
        deleteNameStmnt = deleteInsertConn.prepareStatement("DELETE FROM "
                + NAMETBLNAME + " WHERE NAME = ?");
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

    private Connection getDataConnection() {
        Connection theConn;
        try {
            theConn = DriverManager.getConnection(dataConnURL);
            // may want to put username/password
            theConn.setAutoCommit(false);
            theConn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            // CallableStatement cs = conn.prepareCall("{CALL
            // ttLockLevel('Row')}");
            // cs.execute();
            // cs = conn.prepareCall("{CALL ttLockWait(0)}");
            // cs.execute();
            return theConn;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // internal routines to the system, used by transactions
    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	log.finest("getObjBytes on " + objectID);
        byte[] objbytes = null;
        synchronized (cachedStateMutex) {
	    if (graveyard.contains(objectID)) {
		dataSpace.remove(objectID);
		return null;
	    }
            SoftReference<byte[]> ref = dataSpace.get(objectID);
            if (ref != null) {
                objbytes = ref.get();
                if (objbytes == null) { // ref dead
                    dataSpace.remove(objectID);
		    return null;
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
        synchronized (cachedStateMutex) {
            try {
                getObjStmnt.setLong(1, objectID);
                ResultSet rs = getObjStmnt.executeQuery();
                getObjStmnt.getConnection().commit();
                if (rs.next()) {
                    objbytes = rs.getBytes("OBJBYTES");
                    dataSpace.put(objectID, new SoftReference<byte[]>(objbytes));
                }
		/*
                if (objbytes == null) {
                    log.warning("GOT A NULL OBJBYTES in loadCache "
                            + objectID);
		    (new Exception()).printStackTrace();
                }
		*/
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

	synchronized (lockSet) {
	    while (lockSet.contains(objectID)) {
		try {
		    lockSet.wait();
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
	    }
	    synchronized (cachedStateMutex) {
		if (!dataSpace.containsKey(objectID)) {
		    if (graveyard.contains(objectID)
			    || loadCache(objectID) == null) {
			lockSet.notifyAll();
			throw new NonExistantObjectIDException(
				"Can't find objectID " + objectID);
		    }
		} else {
		    lockSet.add(new Long(objectID));
		}
	    }
	    lockSet.notifyAll();
	}
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) throws NonExistantObjectIDException {
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

	synchronized (lockSet) {

	    /*
	     * Attempt all of the releases.  Then if any of the releases
	     * threw an exception, pick the last one and rethrow it.  This
	     * is less than perfect.  -DJE
	     */

	    for (long oid : objectIDs) {
		try {
		    release(oid);
		} catch (NonExistantObjectIDException e) {
		    re = e;
		}
	    }
	    lockSet.notifyAll();

	    if (re != null) {
		throw re;
	    }
	}
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void atomicUpdate(boolean clear,
	    Map<Long, byte[]> updateMap, List<Long> deleted)
            throws DataSpaceClosedException {

	int i = 0;
	int queueLength = 0;

        if (closed) {
            throw new DataSpaceClosedException();
        }

	if (debug) {
	    Set<Long> oidSet = new HashSet<Long>();
	    for (Long oid : deleted) {
		if (oidSet.contains(oid)) {
		    log.warning("duplicate deleted oid: " + oid);
		} else {
		    oidSet.add(oid);
		}
	    }
	    oidSet.clear();

	    for (Long oid : updateMap.keySet()) {
		if (oidSet.contains(oid)) {
		    log.warning("duplicate update oid: " + oid);
		} else {
		    oidSet.add(oid);
		}
	    }
	    oidSet.clear();
	}

	/*
	 * DJE:  cloning the data should be unnecessary, but
	 * if a reference leaks out of the transaction and is
	 * modified after a commit, then we want to make sure
	 * that what we send to the disk is the state at the
	 * commit, not whatever someone put in there later. 
	 * So if debugging is turned on, then store not only a
	 * reference to the data, but a copy, and later check
	 * to see whether the two still match when it's time
	 * to write this stuff to the disk. 
	 */

	i = 0;
        long[] updateIDs = new long[updateMap.entrySet().size()];
        byte[][] updateDataRef = new byte[updateMap.entrySet().size()][];
	byte[][] updateDataCopy = (debug) ?
	    	    new byte[updateMap.entrySet().size()][] : null;

	for (Entry<Long, byte[]> e : updateMap.entrySet()) {
	    Long key = e.getKey();
	    byte[] value = e.getValue();

	    updateIDs[i] = key;
	    updateDataRef[i] = value;
	    if (debug) {
		updateDataCopy[i] = value.clone();
	    }
	    i++;
	}

	i = 0; 
	long[] deletedIDs = new long[deleted.size()];
	for (long oid : deleted) {
	    deletedIDs[i++] = oid;
	}

	synchronized (diskUpdateQueueMutex) {
	    synchronized (lockSet) {
		synchronized (cachedStateMutex) {

		    i = 0;
		    for (Entry<Long, byte[]> e : updateMap.entrySet()) {
			Long key = e.getKey();
			byte[] value = e.getValue();
			dataSpace.put(key, new SoftReference<byte[]>(value));
		    }

		    i = 0; 
		    for (long oid : deleted) {

			// destroyed objects lose their locks.
			lockSet.remove(oid);

			String name = reverseNameSpace.get(oid);
			if (name != null) {
			    reverseNameSpace.remove(oid);
			    nameSpace.remove(name);
			}
			graveyard.add(oid);
		    }
		}
		lockSet.notifyAll();
	    }

	    if (!closed) { // closed while we were processing
		if (TRACEDISK) {
		    log.finer("Queuing commit #"
			    + commitRegisterCounter++);
		}
		diskUpdateQueue.add(new DiskUpdateRecord(updateIDs,
			updateDataRef, updateDataCopy,
			deletedIDs, id));
	    }
	    queueLength = diskUpdateQueue.size();
	    diskUpdateQueueMutex.notifyAll();
	}

	throttle(queueLength);
    }

    /**
     * {@inheritDoc}
     */
    public Long lookup(String name) {
        Long retval = null;

        synchronized (cachedStateMutex) {
            retval = nameSpace.get(name);
	    if (graveyard.contains(retval)) {
		return null;
	    }

            if (retval == null) {
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
                synchronized (cachedStateMutex) {
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

	    closeWaitMutex.notifyAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    public long create(byte[] data, String name) {
        Long createId;

	/*
	 * XXX objects are NOT created locked.  If they need to be
	 * created and locked, then the entire cachedStateMutex block
	 * must be enclosed in a synchronized (lockSet) block, and
	 * this outer block must end with lockSet.add(createId). 
	 * Because of lock ordering, you must hold the lockSet lock
	 * before doing any of this, because you can't release the
	 * cachedStateMutex before acquiring the lockSet lock.  -DJE
	 */

	synchronized (cachedStateMutex) {
	    if (name != null) {
		createId = lookup(name);
		if (createId != null) {
		    return DataSpace.INVALID_ID;
		}

		createId = new Long(getNextID());
		nameSpace.put(name, createId);
		reverseNameSpace.put(createId, name);
	    } else {
		createId = new Long(getNextID());
	    }

            dataSpace.put(createId, new SoftReference<byte[]>(data));
	}

	/*
	 * Now that the cached state is updated, release all locks and
	 * update the database.
	 *
	 * There may be a potential race condition with deleted names
	 * going to the database and popping up during recovery.  I'm
	 * not sure that this is a problem, nor do a I see an easy way
	 * to tickle this.  But if a race appears, it may be necessary
	 * to protect these database ops inside the cachedStateMutex
	 * (and lockSet lock, if that turns out to be necessary).
	 */

	if (name != null) {
	    try {
		deleteNameStmnt.setString(1, name);
		deleteNameStmnt.execute();

		insertNameStmnt.setString(1, name);
		insertNameStmnt.setLong(2, createId);
		insertNameStmnt.execute();
	    } catch (SQLException e) {
		e.printStackTrace();
		return DataSpace.INVALID_ID;
	    }
	}

	try {
	    insertObjStmnt.setLong(1, createId);
	    insertObjStmnt.setBytes(2, data);
	    insertObjStmnt.execute();
	    insertObjStmnt.getConnection().commit();
	} catch (SQLException e) {
	    e.printStackTrace();
	    return DataSpace.INVALID_ID;
	}

        return createId;
    }

    /**
     * Destroys the object associated with objectID and removes the
     * name associated with that ID (if any).  <p>
     * 
     * @param objectID The objectID of the object to destroy
     */
    private void destroy(long objectID) {
	try {
	    deleteObjStmnt.setLong(1, objectID);
	    deleteObjStmnt.execute();
	    deleteNameByOIDStmnt.setLong(1, objectID);
	    deleteNameByOIDStmnt.execute();
	    deleteInsertConn.commit();
	} catch (SQLException e) {
	    e.printStackTrace();
	}
    }

    private long getNextID() {
        synchronized (idMutex) {
            return id++;
        }
    }

    private void throttle(int queueLength) {

        /*
	 * Try to throttle back:  if the queue is shorter than 40, let
	 * it go.  If the queue is 40-80, then sleep for 20ms, which
	 * should be long enough to move something off the queue (by
	 * rough guess), leaving the system in steady state.  As the
	 * queue gets longer, back off more and more aggressively to
	 * allow the system to catch up.
         * 
	 * Note that because the queue is swallowed whole by the
	 * draining thread it may appear to instantly go to zero
	 * length.  This should not have any impact on this heuristic,
	 * because we care how long the queue gets before the switch.
         */

        try {
            if (queueLength > 150) {
                log.warning("\t\tXXX XXX XXX XXX falling behind "
                        + queueLength);
                Thread.sleep(80);
            } else if (queueLength > 100) {
                log.warning("\t\tXXX XXX XXX falling behind "
			+ queueLength);
                Thread.sleep(55);
            } else if (queueLength > 70) {
                // log.warning("\t\tXXX XXX falling behind " +
                // queueLength);
                Thread.sleep(35);
            } else if (queueLength > 50) {
                // log.warning("\t\tXXX falling behind " +
                // queueLength);
                Thread.sleep(20);
            } else {
                // Carry on.
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
