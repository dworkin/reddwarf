/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.management;

import com.sun.sgs.impl.service.data.store.DataStoreImpl;

/**
 * The management interface for data store information.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #DATA_STORE_STATS_MXBEAN_NAME}.
 * 
 */
public interface DataStoreStatsMXBean 
{
    /** The name for uniquely identifying this MBean. */
    String DATA_STORE_STATS_MXBEAN_NAME = 
            "com.sun.sgs.service:type=DataStoreStats";

    /**
     * Returns the number of times 
     * {@link DataStoreImpl#createObject(Transaction) createObject} 
     * has been called.
     * @return the number of times {@code createObject} has been called
     */
    long getCreateObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#getBinding(Transaction, String) getBinding} 
     * has been called.
     * 
     * @return the number of times {@code getBinding} has been called
     */
    long getGetBindingCalls();
        
    /**
     * Returns the number of times
     * {@link DataStoreImpl#getClassId(Transaction, byte[]) getClassId} 
     * has been called.
     * 
     * @return the number of times {@code getClassId} has been called
     */
    long getClassIdCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#getClassInfo(Transaction, int) getClassInfo} 
     * has been called.
     * 
     * @return the number of times {@code getClassInfo} has been called
     */
    long getClassInfoCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#getObject(Transaction, long, boolean) getObject} 
     * has been called with a {@code false} argument.
     * 
     * @return the number of times {@code getObject} has been called
     */
    long getObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#getObject(Transaction, long, boolean) getObject} 
     * has been called with a {@code true} argument.
     * 
     * @return the number of times {@code getObject} has been called
     */
    long getObjectForUpdateCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#markForUpdate(Transaction, long) markForUpdate} 
     * has been called.
     * 
     * @return the number of times {@code markForUpdate} has been called
     */
    long getMarkForUpdateCalls();

    /**
     * Returns the number of times
     * {@link DataStoreImpl#nextBoundName(Transaction, String) nextBoundName} 
     * has been called.
     * 
     * @return the number of times {@code nextBoundName} has been called
     */
    long getNextBoundNameCalls();

    /**
     * Returns the number of times
     * {@link DataStoreImpl#nextObjectId(Transaction, long) nextObjectId} 
     * has been called.
     * 
     * @return the number of times {@code nextObjectId} has been called
     */
    long getNextObjectIdCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#removeBinding(Transaction, String) removeBinding} 
     * has been called.
     * 
     * @return the number of times {@code removeBinding} has been called
     */
    long getRemoveBindingCalls();

    /**
     * Returns the number of times
     * {@link DataStoreImpl#removeObject(Transaction, long) removeObject}
     * has been called.
     * 
     * @return the number of times {@code removeObject} has been called
     */
    long getRemoveObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#setBinding(Transaction, String, long) setBinding} 
     * has been called.
     * 
     * @return the number of times {@code setBinding} has been called
     */
    long getSetBindingCalls();

    /**
     * Returns the number of times
     * {@link DataStoreImpl#setObject(Transaction, long, byte[]) setObject} 
     * has been called.
     * 
     * @return the number of times {@code setObject} has been called
     */
    long getSetObjectCalls();
    
    /**
     * Returns the number of times
     * {@link DataStoreImpl#setObjects(Transaction, long[], byte[][]) 
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

//
//    /**
//     * Records a list of the number of bytes read by calls to the getObject
//     * method.
//     */
//    private final ProfileSample readBytesSample;
//
//    /**
//     * Records a list of the number of bytes written by calls to the setObject
//     * and setObjects methods.
//     */
//    private final ProfileSample writtenBytesSample;
}


