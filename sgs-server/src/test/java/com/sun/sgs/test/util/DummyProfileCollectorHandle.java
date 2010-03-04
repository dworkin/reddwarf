/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
