/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.management.DataStoreStatsMXBean;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileSample;

/**
 * Implementation of JMX MBean for the data store.
 * 
 */

class DataStoreStats implements DataStoreStatsMXBean {  

    /* -- Profile operations for the DataStore API -- */

    final ProfileOperation createObjectOp;
    final ProfileOperation markForUpdateOp;
    final ProfileOperation getObjectOp;
    final ProfileOperation getObjectForUpdateOp;
    final ProfileOperation setObjectOp;
    final ProfileOperation setObjectsOp;
    final ProfileOperation removeObjectOp;
    final ProfileOperation getBindingOp;
    final ProfileOperation setBindingOp;
    final ProfileOperation removeBindingOp;
    final ProfileOperation nextBoundNameOp;
    final ProfileOperation getClassIdOp;
    final ProfileOperation getClassInfoOp;
    final ProfileOperation nextObjectIdOp;

    /** Records the number of bytes read by the getObject method. */
    final ProfileCounter readBytesCounter;

    /** Records the number of objects read by the getObject method. */
    final ProfileCounter readObjectsCounter;

    /**
     * Records the number of bytes written by the setObject and setObjects
     * methods.
     */
    final ProfileCounter writtenBytesCounter;

    /**
     * Records the number of objects written by the setObject and setObjects
     * methods.
     */
    final ProfileCounter writtenObjectsCounter;

    /**
     * Records a list of the number of bytes read by calls to the getObject
     * method.
     */
    final ProfileSample readBytesSample;

    /**
     * Records a list of the number of bytes written by calls to the setObject
     * and setObjects methods.
     */
    final ProfileSample writtenBytesSample;
    
    /**
     * Create a data store statistics object.
     * @param collector the profile collector used to create profiling
     *     objects and register the MBean with JMX
     */
    DataStoreStats(ProfileCollector collector) {
        ProfileConsumer consumer = 
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX 
                                  + "DataStore");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
        
	createObjectOp = 
            consumer.createOperation("createObject", type, level);
	markForUpdateOp = 
            consumer.createOperation("markForUpdate", type, level);
	getObjectOp = consumer.createOperation("getObject", type, level);
	getObjectForUpdateOp =
	    consumer.createOperation("getObjectForUpdate", type, level);
	setObjectOp = consumer.createOperation("setObject", type, level);
	setObjectsOp = consumer.createOperation("setObjects", type, level);
	removeObjectOp = 
            consumer.createOperation("removeObject", type, level);
	getBindingOp = consumer.createOperation("getBinding", type, level);
	setBindingOp = consumer.createOperation("setBinding", type, level);
	removeBindingOp = 
            consumer.createOperation("removeBinding", type, level);
	nextBoundNameOp = 
            consumer.createOperation("nextBoundName", type, level);
	getClassIdOp = consumer.createOperation("getClassId", type, level);
	getClassInfoOp = 
            consumer.createOperation("getClassInfo", type, level);
	nextObjectIdOp =
            consumer.createOperation("nextObjectIdOp", type, level);
        
        // Counters
	readBytesCounter = consumer.createCounter("readBytes", type, level);
	readObjectsCounter =
	    consumer.createCounter("readObjects", type, level);
	writtenBytesCounter =
	    consumer.createCounter("writtenBytes", type, level);
	writtenObjectsCounter =
	    consumer.createCounter("writtenObjects", type, level);
        
        // Samples
	readBytesSample = 
            consumer.createSample("readBytes", type, level);
	writtenBytesSample = 
            consumer.createSample("writtenBytes", type, level);
    }
    
    /** {@inheritDoc} */
    public long getGetBindingCalls() {
        return ((AggregateProfileOperation) getBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetClassIdCalls() {
        return ((AggregateProfileOperation) getClassIdOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetClassInfoCalls() {
        return ((AggregateProfileOperation) getClassInfoOp).getCount();
    }

    /** {@inheritDoc} */
    public long getCreateObjectCalls() {
        return ((AggregateProfileOperation) createObjectOp).getCount();
    }

    /** {@inheritDoc} */
    public long getMarkForUpdateCalls() {
        return ((AggregateProfileOperation) markForUpdateOp).getCount();
    }

    /** {@inheritDoc} */
    public long getNextObjectIdCalls() {
        return ((AggregateProfileOperation) nextObjectIdOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetObjectCalls() {
        return ((AggregateProfileOperation) getObjectOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetObjectForUpdateCalls() {
        return ((AggregateProfileOperation) getObjectForUpdateOp).getCount();
    }

    /** {@inheritDoc} */
    public long getReadBytesCount() {
        return ((AggregateProfileCounter) readBytesCounter).getCount();
    }

    /** {@inheritDoc} */
    public long getReadObjectsCount() {
        return ((AggregateProfileCounter) readObjectsCounter).getCount();
    }

    /** {@inheritDoc} */
    public long getWrittenBytesCount() {
        return ((AggregateProfileCounter) writtenBytesCounter).getCount();
    }

    /** {@inheritDoc} */
    public long getWrittenObjectsCount() {
        return ((AggregateProfileCounter) writtenObjectsCounter).getCount();
    }

    /** {@inheritDoc} */
    public long getRemoveBindingCalls() {
        return ((AggregateProfileOperation) removeBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getNextBoundNameCalls() {
        return ((AggregateProfileOperation) nextBoundNameOp).getCount();
    }

    /** {@inheritDoc} */
    public long getRemoveObjectCalls() {
        return ((AggregateProfileOperation) removeObjectOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSetBindingCalls() {
        return ((AggregateProfileOperation) setBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSetObjectCalls() {
        return ((AggregateProfileOperation) setObjectOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSetObjectsCalls() {
        return ((AggregateProfileOperation) setObjectsOp).getCount();
    }

    /** {@inheritDoc} */
    public double getAvgReadBytesSample() {
        return ((AggregateProfileSample) readBytesSample).getAverage();
    }

    /** {@inheritDoc} */
    public double getAvgWrittenBytesSample() {
        return ((AggregateProfileSample) writtenBytesSample).getAverage();
    }

    /** {@inheritDoc} */
    public long getMaxReadBytesSample() {
        return ((AggregateProfileSample) readBytesSample).getMaxSample();
    }

    /** {@inheritDoc} */
    public long getMaxWrittenBytesSample() {
        return ((AggregateProfileSample) writtenBytesSample).getMaxSample();
    }

    /** {@inheritDoc} */
    public long getMinReadBytesSample() {
        return ((AggregateProfileSample) readBytesSample).getMinSample();
    }

    /** {@inheritDoc} */
    public long getMinWrittenBytesSample() {
        return ((AggregateProfileSample) writtenBytesSample).getMinSample();
    }

    /** {@inheritDoc} */
    public double getSmoothingFactor() {
        return ((AggregateProfileSample) readBytesSample).getSmoothingFactor();
    }

    /** {@inheritDoc} */
    public void setSmoothingFactor(double smooth) {
        ((AggregateProfileSample) readBytesSample).setSmoothingFactor(smooth);
        ((AggregateProfileSample) writtenBytesSample).
                                                   setSmoothingFactor(smooth);
    }
}
