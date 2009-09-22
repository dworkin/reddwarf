/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.management.DataStoreStatsMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import com.sun.sgs.service.store.DataStore;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * Implements a {@link DataStore} that reports profiling information about data
 * store operations under the name
 * "com.sun.sgs.impl.service.data.store.DataStore", implementing {@code
 * DataStore} operations and transaction participant methods by delegating them
 * to another data store.
 */
public class DataStoreProfileProducer
    implements DataStore, TransactionParticipant
{
    /** The name of this class. */
    private static final String CLASSNAME =
        "com.sun.sgs.impl.service.data.store.DataStoreProfileProducer";

     /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(CLASSNAME));
    
    /** The associated data store. */
    private final DataStore dataStore;

    /** The associated transaction participant. */
    private final TransactionParticipant participant;

    /* -- Profile operations for the DataStore API -- */
    private final DataStoreStats stats;

    /**
     * Creates an instance that delegates all {@link DataStore} and {@link
     * TransactionParticipant} methods to {@code dataStore}.
     *
     * @param	dataStore the object for delegating operations
     * @param	collector the object for collecting profile data
     * @throws	IllegalArgumentException if {@code dataStore} does not
     *		implement {@code TransactionParticipant}
     */
    public DataStoreProfileProducer(DataStore dataStore,
				    ProfileCollector collector)
    {
	if (dataStore == null) {
	    throw new NullPointerException(
		"The dataStore argument must not be null");
	} else if (!(dataStore instanceof TransactionParticipant)) {
	    throw new IllegalArgumentException(
		"The dataStore argument must implement" +
		" TransactionParticipant");
	}
	this.dataStore = dataStore;
	participant = (TransactionParticipant) dataStore;

        stats = new DataStoreStats(collector);
        try {
            collector.registerMBean(stats, DataStoreStatsMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
    }

    /* -- Implement DataStore -- */

    /*
     * Note that the methods report profile information after delegating to the
     * data store for the main operation.  That way, we can rely on the
     * delegation to do error checking, meaning that there should be no
     * exceptions in the calls to ProfileOperation.report.  -tjb@sun.com
     * (08/05/2008)
     */

    /** {@inheritDoc} */
    public void ready() throws Exception {
	/* No profiling for this operation -- it only happens once */
	dataStore.ready();
    }

    /** {@inheritDoc} */
    public long getLocalNodeId() {
	/* No profiling for this operation -- it only happens once */
	return dataStore.getLocalNodeId();
    }

    /** {@inheritDoc} */
    public long createObject(Transaction txn) {
	long result = dataStore.createObject(txn);
	stats.createObjectOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public void markForUpdate(Transaction txn, long oid) {
	dataStore.markForUpdate(txn, oid);
	stats.markForUpdateOp.report();
	/*
	 * Note that the DataStore's implementation of markForUpdate may
	 * actually read the contents of the object, so we might want to
	 * increment the read objects and bytes counters here, but we don't
	 * have access to that information within the implementation.
	 * -tjb@sun.com (08/05/2008)
	 */
    }

    /** {@inheritDoc} */
    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	byte[] result = dataStore.getObject(txn, oid, forUpdate);
	ProfileOperation op = 
                forUpdate ? stats.getObjectForUpdateOp : stats.getObjectOp;
	op.report();
	stats.readBytesCounter.incrementCount(result.length);
	stats.readObjectsCounter.incrementCount();
	stats.readBytesSample.addSample(result.length);
	return result;
    }

    /** {@inheritDoc} */
    public void setObject(Transaction txn, long oid, byte[] data) {
	dataStore.setObject(txn, oid, data);
	stats.setObjectOp.report();
	stats.writtenBytesCounter.incrementCount(data.length);
	stats.writtenObjectsCounter.incrementCount();
	stats.writtenBytesSample.addSample(data.length);
    }

    /** {@inheritDoc} */
    public void setObjects(Transaction txn, long[] oids, byte[][] dataArray) {
	dataStore.setObjects(txn, oids, dataArray);
	stats.setObjectsOp.report();
	for (byte[] data : dataArray) {
	    stats.writtenBytesCounter.incrementCount(data.length);
	    stats.writtenObjectsCounter.incrementCount();
	    stats.writtenBytesSample.addSample(data.length);
	}
    }

    /** {@inheritDoc} */
    public void removeObject(Transaction txn, long oid) {
	dataStore.removeObject(txn, oid);
	stats.removeObjectOp.report();
    }

    /** {@inheritDoc} */
    public long getBinding(Transaction txn, String name) {
	long result = dataStore.getBinding(txn, name);
	stats.getBindingOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public void setBinding(Transaction txn, String name, long oid) {
	dataStore.setBinding(txn, name, oid);
	stats.setBindingOp.report();
    }

    /** {@inheritDoc} */
    public void removeBinding(Transaction txn, String name) {
	dataStore.removeBinding(txn, name);
	stats.removeBindingOp.report();
    }

    /** {@inheritDoc} */
    public String nextBoundName(Transaction txn, String name) {
	String result = dataStore.nextBoundName(txn, name);
	stats.nextBoundNameOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public void shutdown() {
	/* No profiling for this operation -- it only happens once */
	dataStore.shutdown();
    }

    /** {@inheritDoc} */
    public int getClassId(Transaction txn, byte[] classInfo) {
	int result = dataStore.getClassId(txn, classInfo);
	stats.getClassIdOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public byte[] getClassInfo(Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	byte[] result = dataStore.getClassInfo(txn, classId);
	stats.getClassInfoOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public long nextObjectId(Transaction txn, long oid) {
	long result = dataStore.nextObjectId(txn, oid);
	stats.nextObjectIdOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public void setBindingDescription(
	Transaction txn, String name, Object description)
    {
	/* No need for profiling here */
	dataStore.setBindingDescription(txn, name, description);
    }

    /** {@inheritDoc} */
    public void setObjectDescription(
	Transaction txn, long oid, Object description)
    {
	/* No need for profiling here */
	dataStore.setObjectDescription(txn, oid, description);
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	return participant.prepare(txn);
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	participant.commit(txn);
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
	participant.prepareAndCommit(txn);
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	participant.abort(txn);
    }

    /** {@inheritDoc} */
    public String getTypeName() {
	return participant.getTypeName();
    }

    /* -- Other public methods -- */
    
    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "DataStoreProfileProducer[" + dataStore + "]";
    }

    /**
     * Returns the {@code DataStore} that this instance delegates to.
     *
     * @return	the {@code DataStore} that this instance delegates to
     */
    public DataStore getDataStore() {
	return dataStore;
    }
}
