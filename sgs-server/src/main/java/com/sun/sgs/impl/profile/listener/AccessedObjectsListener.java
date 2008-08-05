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

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileProperties;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.math.BigInteger;

import java.util.List;
import java.util.Properties;

/**
 *
 * @see ProfileProperties
 */
public class AccessedObjectsListener implements ProfileListener {


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
	
	AccessedObjectsDetail detail = 
	    profileReport.getAccessedObjectsDetail();
	
	if (detail != null && detail.failedOnContention()) {

	    List<AccessedObject> accessedObjects = 
		detail.getAccessedObjects();

	    System.out.printf("Task type %s failed due to contention.  Details:"
			      + "\n  accesor id: %d, try count %d; objects "
			      + "accessed ordered by first access:\n%s" 
			      + "conflict type: %s, ID of contending " 
			      + "accessor %d\n",
			      profileReport.getTask().getBaseTaskType(),
			      detail.getId(), 
			      profileReport.getRetryCount(),
			      formatAccesses(accessedObjects),
			      detail.getConflictType(),
			      detail.getContendingId());
	}	       
    }

    /** 
     * Returns a formatted list of locks with one lock per line.
     *
     * @param locks the locks to generated a formatted string for
     * 
     * @return a formatted list of locks
     */
    private static String formatAccesses(List<AccessedObject> accessedObjects) {
	String formatted = "";
	for (AccessedObject object : accessedObjects) {
	    formatted += String.format("[source: %s] %-5s %s, desciption %s\n",
				       object.getSource(),
				       object.getAccessType(),
				       object.getObject(),
				       object.getDescription());
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
