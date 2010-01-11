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

package com.sun.sgs.impl.profile;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.TransactionListenerDetail;

/**
 * The portion of a profile collector that allows the kernel to modify
 * the state of a particular collection record (initiates one, finishes one,
 * that sort of thing).  Only particular classes are given access to instances
 * of this class by the kernel.
 */
public class ProfileCollectorHandleImpl implements ProfileCollectorHandle {

    private ProfileCollectorImpl profileCollector;
    
    /**
     * Instantiate a ProfileCollectorHandle.
     * @param profileCollector the backing profile collector
     */
    public ProfileCollectorHandleImpl(ProfileCollectorImpl profileCollector) {
        this.profileCollector = profileCollector;
    }
    
    /** {@inheritDoc} */
    public void notifyThreadAdded() {
        profileCollector.notifyThreadAdded();
    }

    /** {@inheritDoc} */
    public void notifyThreadRemoved() {
        profileCollector.notifyThreadRemoved();
    }

    /** {@inheritDoc} */
    public void notifyNodeIdAssigned(long nodeId) {
        profileCollector.notifyNodeIdAssigned(nodeId);
    }

    /** {@inheritDoc} */
    public void startTask(KernelRunnable task, Identity owner,
            long scheduledStartTime, int readyCount)
    {
        profileCollector.startTask(task, owner, scheduledStartTime, readyCount);
    }

    /** {@inheritDoc} */
    public void noteTransactional(byte[] transactionId) {
        profileCollector.noteTransactional(transactionId);
    }

    /** {@inheritDoc} */
    public void addParticipant(ProfileParticipantDetail participantDetail) {
        profileCollector.addParticipant(participantDetail);
    }

    /** {@inheritDoc} */
    public void addListener(TransactionListenerDetail listenerDetail) {
        profileCollector.addTransactionListener(listenerDetail);
    }

    /** {@inheritDoc} */
    public void setAccessedObjectsDetail(AccessedObjectsDetail detail) {
        profileCollector.setAccessedObjectsDetail(detail);
    }

    /** {@inheritDoc} */
    public void finishTask(int tryCount) {
        profileCollector.finishTask(tryCount);
    }

    /** {@inheritDoc} */
    public void finishTask(int tryCount, Throwable t) {
        profileCollector.finishTask(tryCount, t);
    }
    
    /** {@inheritDoc} */
    public ProfileCollector getCollector() {
        return profileCollector;
    }
}
