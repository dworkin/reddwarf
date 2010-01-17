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

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.TransactionListenerDetail;
import com.sun.sgs.test.util.DummyProfileCollectorHandle;

/**
 * A dummy implementation of {@code ProfileCollectorHandle}, just to
 * accept and provide access to the AccessedObjectsDetail.
 */
public class DummyProfileCollectorHandle implements ProfileCollectorHandle {
    private AccessedObjectsDetail detail;

    public DummyProfileCollectorHandle() { }

    /**
     * Returns the AccessedObjectsDetail last supplied to a call to
     * setAccessedObjectsDetail.
     */
    public synchronized AccessedObjectsDetail getAccessedObjectsDetail() {
	AccessedObjectsDetail result = detail;
	detail = null;
	return result;
    }

    /* -- Implement ProfileCollectorHandle -- */

    public synchronized void setAccessedObjectsDetail(
	AccessedObjectsDetail detail)
    {
	this.detail = detail;
    }

    /* -- Unsupported methods -- */

    public void notifyThreadAdded() {
	throw new UnsupportedOperationException();
    }
    public void notifyThreadRemoved() {
	throw new UnsupportedOperationException();
    }
    public void notifyNodeIdAssigned(long id) {
        throw new UnsupportedOperationException();
    }
    public void startTask(KernelRunnable task, Identity owner,
			  long scheduledStartTime, int readyCount)
    {
	throw new UnsupportedOperationException();
    }
    public void noteTransactional(byte[] transactionId) {
	throw new UnsupportedOperationException();
    }
    public void addParticipant(
	ProfileParticipantDetail participantDetail)
    {
	throw new UnsupportedOperationException();
    }
    public void addListener(TransactionListenerDetail listenerDetail) {
        throw new UnsupportedOperationException();
    }
    public void finishTask(int tryCount) {
	throw new UnsupportedOperationException();
    }
    public void finishTask(int tryCount, Throwable t) {
	throw new UnsupportedOperationException();
    }
    public ProfileCollector getCollector() {
	throw new UnsupportedOperationException();
    }
}
