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

import com.sun.sgs.contention.ContentionReport;
import com.sun.sgs.contention.ContentionReport.ConflictType;
import com.sun.sgs.contention.LockInfo;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileProperties;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.math.BigInteger;

import java.util.Collection;
import java.util.Properties;

/**
 *
 * @see ProfileProperties
 */
public class ProfileContentionListener implements ProfileListener {


    /**
     * Creates an instance of {@code ProfileContentionListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     */
    public ProfileContentionListener(Properties properties, Identity owner,
				     ComponentRegistry registry) {
	
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
	
	ContentionReport contentionReport = profileReport.getContentionReport();
	if (contentionReport != null && 
	    (!(contentionReport.getConflictType().equals(ConflictType.UNKNOWN))
	     || !(contentionReport.getContendedLocks().isEmpty()))) {

	    Collection<LockInfo> contendedLocks = 
		contentionReport.getContendedLocks();

	    System.out.printf("Task type %s with (txn id: %d, "
			      + "try count %d) locks held in order of "
			      + "acquisition:\n%s" 
			      + "was aborted for %s reasons due to the following"
			      + " lock(s):\n%s"
			      + "The conflicting task was of type %s "
			      + "(txn id: %d)\n\n",
			      profileReport.getTask().getBaseTaskType(),
			      contentionReport.getTransactionID(), 
			      profileReport.getRetryCount(),
			      formatLocks(contentionReport.getAcquiredLocks()),
			      contentionReport.getConflictType(),
			      (contendedLocks.isEmpty() ? "  [Unknown]\n" : 
			       formatLocks(contendedLocks)),
			      contentionReport.getConflictingTaskType(),
			      contentionReport.getConflictingTransactionID());
	}	       
    }

    /** 
     * Returns a formatted list of locks with one lock per line.
     *
     * @param locks the locks to generated a formatted string for
     * 
     * @return a formatted list of locks
     */
    private static String formatLocks(Collection<LockInfo> locks) {
	String formatted = "";
	for (LockInfo info : locks) {
	    Object o = info.getObject();
	    BigInteger oid = info.getObjectID();
	    String id = (oid == null) 
		? info.getBoundName() 
		: String.valueOf(oid.longValue());
	    String type, obj;
	    if (o == null) {
		type = "Unknown";
		obj = "Unknown";
	    }
	    else {
		type = o.getClass().getSimpleName();
		try {       	
		    obj = o.toString();
		}
		catch (Throwable t) {
		    obj = "toString() threw " + t.getClass().getName();
		}
	    }
	    formatted += String.format("  %-5s %6s:%-30s toString():%-30s\n",
				       info.getLockType(), id, type, obj);
	}
	return formatted;
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }

}
