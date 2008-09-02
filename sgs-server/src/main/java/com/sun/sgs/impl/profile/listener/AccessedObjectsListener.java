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

import com.sun.sgs.impl.profile.util.TransactionId;

import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;


/**
 * An implementation of {@code ProfileListener} that prints access detail.
 * For any transaction that fails due to conflict this will display the
 * accesses for that transaction and the accesses, if known, for the
 * transaction that caused the conflict. This detail is displaye to standard
 * out.
 * <p>
 * This listener can keep a bounded backlog of finished transactions. This is
 * used, when a transaction fails due to conflict, to see if the conflicting
 * transaction has already completed and therefore if its access detail is
 * available to display. Depending on the {@code ConflictChecker} being
 * used, keeping a backlog may or may not be helpful. A longer backlog may
 * produce more detail about a failure because a conflicting transaction
 * is more likely to still be in the backlog at any given time. By
 * default no backlog is tracked, but it may be enabled by setting the
 * {@code com.sun.sgs.impl.profile.listener.AccessedObjectsListener.backlog.size}
 * property to a non-negative value representing the number of past transactions
 * to track in the backlog.
 * <p>
 * For each failed transaction an ordered list of the accessed objects is
 * shown. By default, this list will be limited to the first 20 accesss. The
 * number of diplayed acesses can be changed by setting the
 * {@code com.sun.sgs.impl.profile.listener.AccessedObjectsListener.access.count}
 * property to a positive integer. 
 */
public class AccessedObjectsListener implements ProfileListener {

    // an optional local backlog of the past access detail
    private final BoundedLinkedHashMap<TransactionId,AccessedObjectsDetail>
        backlogMap;

    /** Property that defines the non-negative size of the backlog. */
    public static final String BACKLOG_PROPERTY =
        AccessedObjectsListener.class.getName() + ".backlog.size";

    /** Property that defines the maximum number of accesses to display. */
    public static final String ACCESS_COUNT_PROPERTY = 
	AccessedObjectsListener.class.getName() + ".access.count";

    // the default and configured number of accesses to display
    private static final int DEFAULT_ACCESS_COUNT = 20;
    private final int accessesToShow;

    /**
     * Creates an instance of {@code AccessedObjectsListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     * @throws IllegalArgumentException if either of the backlog or count
     *                                  properties is provided but invalid
     */
    public AccessedObjectsListener(Properties properties, Identity owner,
				   ComponentRegistry registry) {
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        String backlogProp = properties.getProperty(BACKLOG_PROPERTY);
        if (backlogProp != null) {
            try {
                int size = Integer.parseInt(backlogProp);
                if (size != 0) {
                    backlogMap = new BoundedLinkedHashMap
                        <TransactionId,AccessedObjectsDetail>(size);
                } else {
                    backlogMap = null;
                }
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Backlog size must be a " +
                                                   "number: " + backlogProp);
            }
        } else {
            backlogMap = null;
        }

        String countProp = properties.getProperty(ACCESS_COUNT_PROPERTY);
        if (countProp != null) {
            try {
                accessesToShow = Integer.parseInt(countProp);
                if (accessesToShow < 0)
                    throw new IllegalArgumentException("Access count must be " +
                                                       "non-negative: " +
                                                       accessesToShow);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Access count moust be a " +
                                                   "number: " + countProp);
            }
        } else {
            accessesToShow = DEFAULT_ACCESS_COUNT;
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
        TransactionId txnId = null;
        if (backlogMap != null) {
            txnId = new TransactionId(profileReport.getTransactionId());
            backlogMap.put(txnId, detail);
        }

        // if there was conflict, then figure out what to display
        if (detail.getConflictType() != ConflictType.NONE) {
            if (txnId == null)
                txnId = new TransactionId(profileReport.getTransactionId());
            // get the root cause of the failure
            Throwable cause = profileReport.getFailureCause();
            Throwable t;
            while ((t = cause.getCause()) != null)
                cause = t;
            // print out the detail for the failed transaction
	    System.out.printf("Task type %s failed due to conflict.%n"
                              + "  Message: %s%n  Details:"
			      + " accesor id: %s, try count %d; objects "
			      + "accessed ordered by first access:%n%s" 
			      + "conflict type: %s%n",
			      profileReport.getTask().getBaseTaskType(),
                              cause.getMessage(),
			      txnId, profileReport.getRetryCount(),
			      formatAccesses(detail.getAccessedObjects()),
			      detail.getConflictType());

            // see if the conflicting transaction is known, otherwise we've
            // shown all the detail we know
            byte [] conflictingBytes = detail.getConflictingId();
            if (conflictingBytes == null) {
                System.out.printf("%n");
                return;
            }

            TransactionId conflictingId = new TransactionId(conflictingBytes);

            // if we're keeping a backlog, look through it to see if we
            // have the detail on the conflicting id
            if (backlogMap != null) {
                // look to see if we know about the conflicting transaction,
                // and add the new detail to the backlog
                AccessedObjectsDetail conflictingDetail =
                    backlogMap.get(conflictingId);

                // if we found the conflicting detail, display it and return
                if (conflictingDetail != null) {
                    System.out.printf("Conflicting transaction id: %s, objects"
                                      + " accessed, ordered by first access:"
                                      + "%n%s%n", conflictingId,
                                      formatAccesses(conflictingDetail.
                                                     getAccessedObjects()));

                    return;
                }
            }

            // we don't know anything else, so just print out the id
            System.out.printf("ID of conflicting accessor %s%n%n",
                              conflictingId);
	}
    }

    /** 
     * Returns a formatted list of accesses with one access per line.
     *
     * @param accessedObjects a {@code List} of {@code AccessedObject}
     * 
     * @return a formatted representation of the accessed objects
     */
    private String formatAccesses(List<AccessedObject> accessedObjects) {
        StringBuilder formatted = new StringBuilder();
        int count = 0;

	for (AccessedObject object : accessedObjects) {
            if (++count > accessesToShow)
                break;

            try {
                formatted.append(String.format("[source: %s] %-5s %s, " +
                                               "description: %s%n",
                                               object.getSource(),
                                               object.getAccessType(),
                                               object.getObjectId(),
                                               object.getDescription()));
            } catch (Throwable t) {
                // calling toString() on the object id or the description
                // may have failed, though in practice (in the current
                // implementation) only the description will have caused
                // any trouble, so we can include some detail about
                // both the access and the failure
                formatted.append(String.format("[source: %s] %-5s %s [%s." +
                                               "toString() threw: %s]%n",
                                               object.getSource(),
                                               object.getAccessType(),
                                               object.getObjectId(),
                                               object.getDescription().
                                               getClass(), t));
            }
        }

        // if we went over the max count then it means there was still
        // more to show, so add a message about the truncation
	if (--count == accessesToShow)
	    formatted.
                append(String.format("[%d further accesses truncated]%n",
                                     accessedObjects.size() - accessesToShow));

	return formatted.toString();
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
        private static final long serialVersionUID = 1;

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
