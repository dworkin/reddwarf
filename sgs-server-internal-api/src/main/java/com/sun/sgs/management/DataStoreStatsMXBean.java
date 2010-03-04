/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.management;

import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.profile.AggregateProfileSample;

/**
 * The management interface for data store information.
 * <p>
 * An instance implementing this MBean can be obtained from the
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface DataStoreStatsMXBean 
{
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.service:type=DataStoreStats";

    /**
     * Returns the number of times 
     * {@link DataStore#createObject(Transaction) createObject} 
     * has been called.
     * @return the number of times {@code createObject} has been called
     */
    long getCreateObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#getBinding(Transaction, String) getBinding} 
     * has been called.
     * 
     * @return the number of times {@code getBinding} has been called
     */
    long getGetBindingCalls();
        
    /**
     * Returns the number of times
     * {@link DataStore#getClassId(Transaction, byte[]) getClassId} 
     * has been called.
     * 
     * @return the number of times {@code getClassId} has been called
     */
    long getGetClassIdCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#getClassInfo(Transaction, int) getClassInfo} 
     * has been called.
     * 
     * @return the number of times {@code getClassInfo} has been called
     */
    long getGetClassInfoCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#getObject(Transaction, long, boolean) getObject} 
     * has been called with a {@code false} argument.
     * 
     * @return the number of times {@code getObject} has been called
     */
    long getGetObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#getObject(Transaction, long, boolean) getObject} 
     * has been called with a {@code true} argument.
     * 
     * @return the number of times {@code getObject} has been called
     */
    long getGetObjectForUpdateCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#markForUpdate(Transaction, long) markForUpdate} 
     * has been called.
     * 
     * @return the number of times {@code markForUpdate} has been called
     */
    long getMarkForUpdateCalls();

    /**
     * Returns the number of times
     * {@link DataStore#nextBoundName(Transaction, String) nextBoundName} 
     * has been called.
     * 
     * @return the number of times {@code nextBoundName} has been called
     */
    long getNextBoundNameCalls();

    /**
     * Returns the number of times
     * {@link DataStore#nextObjectId(Transaction, long) nextObjectId} 
     * has been called.
     * 
     * @return the number of times {@code nextObjectId} has been called
     */
    long getNextObjectIdCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#removeBinding(Transaction, String) removeBinding} 
     * has been called.
     * 
     * @return the number of times {@code removeBinding} has been called
     */
    long getRemoveBindingCalls();

    /**
     * Returns the number of times
     * {@link DataStore#removeObject(Transaction, long) removeObject}
     * has been called.
     * 
     * @return the number of times {@code removeObject} has been called
     */
    long getRemoveObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#setBinding(Transaction, String, long) setBinding} 
     * has been called.
     * 
     * @return the number of times {@code setBinding} has been called
     */
    long getSetBindingCalls();

    /**
     * Returns the number of times
     * {@link DataStore#setObject(Transaction, long, byte[]) setObject} 
     * has been called.
     * 
     * @return the number of times {@code setObject} has been called
     */
    long getSetObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStore#setObjects(Transaction, long[], byte[][]) 
     * setObjects} has been called.
     * 
     * @return the number of times {@code setObjects} has been called
     */
    long getSetObjectsCalls();

    /**
     * Returns the total number of bytes read from the data store.
     * @return the total number of bytes read from the data store
     */
    long getReadBytesCount();
    
    /**
     * Returns the total number of objects read from the data store.
     * @return the total number of objects read from the data store
     */
    long getReadObjectsCount();
    
    /**
     * Returns the total number of bytes read written to data store.
     * @return the total number of bytes read written to data store
     */
    long getWrittenBytesCount();
    
    /**
     * Returns the total number of objects read written to data store.
     * @return the total number of objects read written to data store
     */
    long getWrittenObjectsCount();

    /**
     * Returns the smoothing factor in effect for the data store aggregate
     * statistics.
     * @return the smoothing factor
     */
    double getSmoothingFactor();
    
    /**
     * Sets the smoothing factor in effect for the data store aggregate
     * statistics.
     * @see AggregateProfileSample#setSmoothingFactor
     * @param smooth the smoothing factor
     */
    void setSmoothingFactor(double smooth);
    
    /**
     * Returns the maximum written byte sample value.
     * @return the maximum written byte sample value
     */
    long getMaxWrittenBytesSample();
    
    /**
     * Returns the mimimum written byte sample value.
     * @return the mimimum written byte sample value
     */
    long getMinWrittenBytesSample();
    
    /**
     * Returns the average written byte sample value, smoothed with the
     * smoothing factor.
     * @return the average written byte sample value.
     */
    double getAvgWrittenBytesSample();
    
    /**
     * Returns the maximum read byte sample value.
     * @return the maximum read byte sample value
     */
    long getMaxReadBytesSample();
    
    /**
     * Returns the mimimum read byte sample value.
     * @return the mimimum read byte sample value
     */
    long getMinReadBytesSample();
    
    /**
     * Returns the average read byte sample value, smoothed with the
     * smoothing factor.
     * @return the average read byte sample value.
     */
    double getAvgReadBytesSample();

}


