/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;

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
    /** The associated data store. */
    private final DataStore dataStore;

    /** The associated transaction participant. */
    private final TransactionParticipant participant;

    /* -- Profile operations for the DataStore API -- */

    private final ProfileOperation createObjectOp;
    private final ProfileOperation markForUpdateOp;
    private final ProfileOperation getObjectOp;
    private final ProfileOperation getObjectForUpdateOp;
    private final ProfileOperation setObjectOp;
    private final ProfileOperation setObjectsOp;
    private final ProfileOperation removeObjectOp;
    private final ProfileOperation getBindingOp;
    private final ProfileOperation setBindingOp;
    private final ProfileOperation removeBindingOp;
    private final ProfileOperation nextBoundNameOp;
    private final ProfileOperation getClassIdOp;
    private final ProfileOperation getClassInfoOp;
    private final ProfileOperation nextObjectIdOp;

    /** Records the number of bytes read by the getObject method. */
    private final ProfileCounter readBytesCounter;

    /** Records the number of objects read by the getObject method. */
    private final ProfileCounter readObjectsCounter;

    /**
     * Records the number of bytes written by the setObject and setObjects
     * methods.
     */
    private final ProfileCounter writtenBytesCounter;

    /**
     * Records the number of objects written by the setObject and setObjects
     * methods.
     */
    private final ProfileCounter writtenObjectsCounter;

    /**
     * Records a list of the number of bytes read by calls to the getObject
     * method.
     */
    private final ProfileSample readBytesSample;

    /**
     * Records a list of the number of bytes written by calls to the setObject
     * and setObjects methods.
     */
    private final ProfileSample writtenBytesSample;

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
        ProfileConsumer consumer = collector.getConsumer(
                ProfileCollectorImpl.CONSUMER_PREFIX + "DataStore");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
	createObjectOp = consumer.createOperation("createObject", type, level);
	markForUpdateOp = 
            consumer.createOperation("markForUpdate", type, level);
	getObjectOp = consumer.createOperation("getObject", type, level);
	getObjectForUpdateOp =
	    consumer.createOperation("getObjectForUpdate", type, level);
	setObjectOp = consumer.createOperation("setObject", type, level);
	setObjectsOp = consumer.createOperation("setObjects", type, level);
	removeObjectOp = consumer.createOperation("removeObject", type, level);
	getBindingOp = consumer.createOperation("getBinding", type, level);
	setBindingOp = consumer.createOperation("setBinding", type, level);
	removeBindingOp = 
            consumer.createOperation("removeBinding", type, level);
	nextBoundNameOp = 
            consumer.createOperation("nextBoundName", type, level);
	getClassIdOp = consumer.createOperation("getClassId", type, level);
	getClassInfoOp = consumer.createOperation("getClassInfo", type, level);
	nextObjectIdOp = 
            consumer.createOperation("nextObjectIdOp", type, level);
	readBytesCounter = consumer.createCounter("readBytes", type, level);
	readObjectsCounter =
	    consumer.createCounter("readObjects", type, level);
	writtenBytesCounter =
	    consumer.createCounter("writtenBytes", type, level);
	writtenObjectsCounter =
	    consumer.createCounter("writtenObjects", type, level);
	readBytesSample = consumer.createSample("readBytes", type, level);
	writtenBytesSample = consumer.createSample("writtenBytes", type, level);
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
    public long createObject(Transaction txn) {
	long result = dataStore.createObject(txn);
	createObjectOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public void markForUpdate(Transaction txn, long oid) {
	dataStore.markForUpdate(txn, oid);
	markForUpdateOp.report();
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
	ProfileOperation op = forUpdate ? getObjectForUpdateOp : getObjectOp;
	op.report();
	readBytesCounter.incrementCount(result.length);
	readObjectsCounter.incrementCount();
	readBytesSample.addSample(result.length);
	return result;
    }

    /** {@inheritDoc} */
    public void setObject(Transaction txn, long oid, byte[] data) {
	dataStore.setObject(txn, oid, data);
	setObjectOp.report();
	writtenBytesCounter.incrementCount(data.length);
	writtenObjectsCounter.incrementCount();
	writtenBytesSample.addSample(data.length);
    }

    /** {@inheritDoc} */
    public void setObjects(Transaction txn, long[] oids, byte[][] dataArray) {
	dataStore.setObjects(txn, oids, dataArray);
	setObjectsOp.report();
	for (byte[] data : dataArray) {
	    writtenBytesCounter.incrementCount(data.length);
	    writtenObjectsCounter.incrementCount();
	    writtenBytesSample.addSample(data.length);
	}
    }

    /** {@inheritDoc} */
    public void removeObject(Transaction txn, long oid) {
	dataStore.removeObject(txn, oid);
	removeObjectOp.report();
    }

    /** {@inheritDoc} */
    public long getBinding(Transaction txn, String name) {
	long result = dataStore.getBinding(txn, name);
	getBindingOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public void setBinding(Transaction txn, String name, long oid) {
	dataStore.setBinding(txn, name, oid);
	setBindingOp.report();
    }

    /** {@inheritDoc} */
    public void removeBinding(Transaction txn, String name) {
	dataStore.removeBinding(txn, name);
	removeBindingOp.report();
    }

    /** {@inheritDoc} */
    public String nextBoundName(Transaction txn, String name) {
	String result = dataStore.nextBoundName(txn, name);
	nextBoundNameOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	/* No profiling for this operation -- it only happens once */
	return dataStore.shutdown();
    }

    /** {@inheritDoc} */
    public int getClassId(Transaction txn, byte[] classInfo) {
	int result = dataStore.getClassId(txn, classInfo);
	getClassIdOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public byte[] getClassInfo(Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	byte[] result = dataStore.getClassInfo(txn, classId);
	getClassInfoOp.report();
	return result;
    }

    /** {@inheritDoc} */
    public long nextObjectId(Transaction txn, long oid) {
	long result = dataStore.nextObjectId(txn, oid);
	nextObjectIdOp.report();
	return result;
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
