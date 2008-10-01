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

package com.sun.sgs.impl.service.data.store;
import com.sun.sgs.management.DataStoreStatsMXBean;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.service.ProfileService;

/**
 * Class DataStoreStats
 * 
 */

public class DataStoreStats implements DataStoreStatsMXBean {  

    /* -- Profile operations for the DataStore API -- */
   
    // JANE: much of this code could be automatically generated, I'd think,
    // especially for the operations.  Seems like a netbeans plug-in sort
    // of thing to do.

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
    
    public DataStoreStats(ProfileService profileService){
        // NOTE
        // Set up profiling stuff.  Can this be done statically?
        // What if I have a new ProfileStats (probably a bad name) object
        // that can be registered with the ProfileConsumer, and has to 
        // provide a getOperations method?  This is almost the same thing
        // that we have today (but backwards, if you will), but will need
        // profile* objects to be able to be instantiated, and they will
        // be in a state where they cannot operation until registered.
        // Or we could instantiate them with a ProfileConsumer, but it's
        // hard to see the advantage of that.
        ProfileConsumer consumer =
	    profileService.getProfileCollector().
                registerProfileProducer(DataStore.class.getName());
        ProfileLevel level = ProfileLevel.MAX;
        
	createObjectOp = consumer.registerOperation("createObject", level);
	markForUpdateOp = consumer.registerOperation("markForUpdate", level);
	getObjectOp = consumer.registerOperation("getObject", level);
	getObjectForUpdateOp =
	    consumer.registerOperation("getObjectForUpdate", level);
	setObjectOp = consumer.registerOperation("setObject", level);
	setObjectsOp = consumer.registerOperation("setObjects", level);
	removeObjectOp = consumer.registerOperation("removeObject", level);
	getBindingOp = consumer.registerOperation("getBinding", level);
	setBindingOp = consumer.registerOperation("setBinding", level);
	removeBindingOp = consumer.registerOperation("removeBinding", level);
	nextBoundNameOp = consumer.registerOperation("nextBoundName", level);
	getClassIdOp = consumer.registerOperation("getClassId", level);
	getClassInfoOp = consumer.registerOperation("getClassInfo", level);
	nextObjectIdOp = consumer.registerOperation("nextObjectIdOp", level);
	readBytesCounter = consumer.registerCounter("readBytes", true, level);
	readObjectsCounter =
	    consumer.registerCounter("readObjects", true, level);
	writtenBytesCounter =
	    consumer.registerCounter("writtenBytes", true, level);
	writtenObjectsCounter =
	    consumer.registerCounter("writtenObjects", true, level);
	readBytesSample = consumer.registerSampleSource(
	    "readBytes", true, Integer.MAX_VALUE, level);
	writtenBytesSample = consumer.registerSampleSource(
	    "writtenBytes", true, Integer.MAX_VALUE, level);
        
        // Set up JMX stuff.  Register MXBean with platform.
        // Or is this really handled by the  DataService???
        //   Will we always require a 1-1 ProfileConsumer/JMX registration?
    }
    
    
    // NOTE_   for non task profiling stuff, just add allow listeners
    // to get at MXBeans directly?  Might need task (unsync) and non-task
    // versions, to reduce synch?  But register one big JMX bean w/ platform.
    // Use Atomic...
    
    @Override
    public long getGetBindingCount() {
        return getBindingOp.getCount();
    }

    @Override
    public long getClassIdCount() {
        return getClassIdOp.getCount();
    }

    @Override
    public long getClassInfoCount() {
        return getClassInfoOp.getCount();
    }

    @Override
    public long getCreateObjectCount() {
        return createObjectOp.getCount();
    }

    @Override
    public long getMarkForUpdateCount() {
        return markForUpdateOp.getCount();
    }

    @Override
    public long getNextObjectIdCount() {
        return nextObjectIdOp.getCount();
    }

    @Override
    public long getObjectCount() {
        return getObjectOp.getCount();
    }

    @Override
    public long getObjectForUpdateCount() {
        return getObjectForUpdateOp.getCount();
    }

    @Override
    public long getReadBytesCount() {
        return readBytesCounter.getCount();
    }

    @Override
    public long getReadObjectsCount() {
        return readObjectsCounter.getCount();
    }

    @Override
    public long getWrittenBytesCount() {
        return writtenBytesCounter.getCount();
    }

    @Override
    public long getWrittenObjectsCount() {
        return writtenObjectsCounter.getCount();
    }

    @Override
    public long getRemoveBindingCount() {
        return removeBindingOp.getCount();
    }

    @Override
    public long getNextBoundNameCount() {
        return nextBoundNameOp.getCount();
    }

    @Override
    public long getRemoveObjectCount() {
        return removeObjectOp.getCount();
    }

    @Override
    public long getSetBindingCount() {
        return setBindingOp.getCount();
    }

    @Override
    public long getSetObjectCount() {
        return setObjectOp.getCount();
    }

    @Override
    public long getSetObjectsCount() {
        return setObjectsOp.getCount();
    }

}


