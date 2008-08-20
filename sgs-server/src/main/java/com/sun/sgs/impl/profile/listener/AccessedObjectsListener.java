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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.math.BigInteger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * An implementation of {@code ProfileListener} that prints access detail.
 * For any transaction that fails due to conflict this will display the
 * accesses for that transaction and the accesses, if known, for the
 * transaction that caused the conflict.
 * <p>
 * Note that in this current implementation, conflict detail will only be
 * provided if the {@code AccessCoordinator} is using a backlog to track
 * finished transactions. See {@code AccessCoordinatorImpl} for more
 * detail.
 */
public class AccessedObjectsListener implements ProfileListener {

    // a local backlog of the past access detail
    private final BoundedLinkedHashMap<BigInteger,AccessedObjectsDetail>
        backlogMap;

    private static final String ACCESS_COUNT_PROPERTY = 
	AccessedObjectsListener.class.getName() + ".access.count";

    private static final int DEFAULT_ACCESS_COUNT = 20;

    /**
     * The number of accesses to show when outputting text
     */
    private int accessesToShow;

    /**
     * Creates an instance of {@code AccessedObjectsListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     */
    public AccessedObjectsListener(Properties properties, Identity owner,
				   ComponentRegistry registry) {
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        String backlogProp =
            properties.getProperty("com.sun.sgs.impl.kernel." +
                                   "AccessCoordinatorImpl.queue.size");
        if (backlogProp != null) {
            try {
                backlogMap = new BoundedLinkedHashMap
                    <BigInteger,AccessedObjectsDetail>(Integer.
                                                       parseInt(backlogProp));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Backlog size must be a " +
                                                   "number: " + backlogProp);
            }
        } else {
            backlogMap = null;
        }

	String accessCountStr = properties.getProperty(ACCESS_COUNT_PROPERTY);
	if (accessCountStr == null) {
	    accessesToShow = DEFAULT_ACCESS_COUNT;
	}
	else {
	    try {
		int accessesToShow = Integer.parseInt(accessCountStr);
		
	    } catch (NumberFormatException nfe) {
		throw new IllegalArgumentException("Access count moust be a " +
						   "number: " + accessCountStr);
	    }
	    
	}

    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        // get the access detail, or return if there is none available
	AccessedObjectsDetail detail = profileReport.getAccessedObjectsDetail();
	if (detail == null)
            return;

        // if a backlog is in use, then store the new detail
        if (backlogMap != null)
            backlogMap.put(profileReport.getTransactionId(), detail);

        // if there was conflict, then figure out what to display
        if (detail.getConflictType() != ConflictType.NONE) {

            // print out the detail for the failed transaction
	    System.out.printf("Task type %s failed due to conflict.  Details:"
			      + "%n  accesor id: %d, try count %d; objects "
			      + "accessed ordered by first access:%n%s" 
			      + "conflict type: %s%n",
			      profileReport.getTask().getBaseTaskType(),
			      profileReport.getTransactionId().longValue(), 
			      profileReport.getRetryCount(),
			      formatAccesses(detail.getAccessedObjects()),
			      detail.getConflictType());

            // see if the conflicting transaction is known, otherwise we've
            // shown all the detail we know
            BigInteger conflictingId = detail.getConflictingId();
            if (conflictingId == null) {
                System.out.printf("%n");
                return;
            }

            // if we're keeping a backlog, look through it to see if we
            // have the detail on the conflicting id
            if (backlogMap != null) {
                // look to see if we know about the conflicting transaction,
                // and add the new detail to the backlog
                AccessedObjectsDetail conflictingDetail =
                    backlogMap.get(conflictingId);

                // if we found the conflicting detail, display it and return
                if (conflictingDetail != null) {
                    System.out.printf("Conflicting transaction id: %d, objects"
                                      + " accessed, ordered by first access:"
                                      + "%n%s%n", conflictingId.longValue(),
                                      formatAccesses(conflictingDetail.
                                                     getAccessedObjects()));

                    return;
                }
            }

            // we don't know anything else, so just print out the id
            System.out.printf("ID of conflicting accessor %s%n%n",
                              conflictingId.toString());
	}
    }

    /** 
     * Returns a formatted list of locks with one lock per line.
     *
     * @param locks the locks to generated a formatted string for
     * 
     * @return a formatted list of locks
     */
    private String formatAccesses(List<AccessedObject> accessedObjects) {
	String formatted = "";
        int count = 0;

	for (AccessedObject object : accessedObjects) {
            if (count++ < accessesToShow) {
		try {
		    formatted += String.format("[source: %s] %-5s %s, "
					       +"desciption: %s%n",
					       object.getSource(),
					       object.getAccessType(),
					       object.getObjectId(),
					       object.getDescription());
		} catch (Throwable t) {
		    // the first three calls are guaranteed not to
		    // throw an exception since we control their
		    // implementation.  However, the description is
		    // provided at run time and therefore may have
		    // thrown an exception, so we mark it as such.
		    formatted += 
			String.format("[source %s] %-5s %s [%s.toString() threw"
				      + " exception: %s]%n", object.getSource(), 
				      object.getAccessType(), 
				      object.getObjectId(),					      
				      object.getDescription().getClass(), t);
		}
	    }
	    else {
		break;
	    }
	}

	if (count == accessesToShow)
	    formatted += String.format("[%d further accesses truncated]%n",
				       accessedObjects.size() - accessesToShow);

	return formatted;
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }

    /** 
     * A private implementation of {@code LinkedHashMap} that is
     * bounded in size.
     */
    private static class BoundedLinkedHashMap<K,V> extends LinkedHashMap<K,V> {
        // the bounding size
        private final int maxSize;
     
	/** Creates an instance of {@code BoundedLinkedHashMap}. */
        BoundedLinkedHashMap(int maxSize) {
            this.maxSize = maxSize;
        }
        /** Overrides to bound to a fixed size. */
        protected boolean removeEldestEntry(Entry<K,V> eldest) {
            return size() > maxSize;
        }
    }

}
