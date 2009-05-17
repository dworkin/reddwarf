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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
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


