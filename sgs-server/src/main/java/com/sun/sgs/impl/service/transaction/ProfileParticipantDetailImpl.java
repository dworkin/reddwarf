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

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.profile.ProfileParticipantDetail;


/**
 * Simple implementation of <code>ProfileParticipantDetail</code> that
 * is package-private and used by <code>TransactionImpl</code> to report
 * detail associated with each participant.
 */
class ProfileParticipantDetailImpl implements ProfileParticipantDetail {

    // the name of the participant
    private final String name;

    // whether the participant successfully prepared, and how long it took
    // regardless of success
    private boolean prepared = false;
    private long prepareTime = 0;

    // the result from preparation
    private boolean readOnly = false;

    // whether the participant successfully committed, and how long it took
    // if the participant committed
    private boolean committed = false;
    private long commitTime = 0;

    // whether prepareAndCommit was called on this participant
    private boolean committedDirectly = false;

    // if the participant was aborted, how long it took to abort
    private long abortTime = 0;

    /**
     * Creates an instance of <code>ProfileParticipantDetailImpl</code> for
     * the given named participant.
     *
     * @param participantName the name of the participant associated with
     *                        this detail
     */
    ProfileParticipantDetailImpl(String participantName) {
	this.name = participantName;
    }

    /**
     * {@inheritDoc}
     */
    public String getParticipantName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean wasPrepared() {
        return prepared;
    }

    /**
     * {@inheritDoc}
     */
    public boolean wasReadOnly() {
        return readOnly;
    }

    /**
     * {@inheritDoc}
     */
    public boolean wasCommitted() {
        return committed;
    }

    /**
     * {@inheritDoc}
     */
    public boolean wasCommittedDirectly() {
	return committedDirectly;
    }

    /**
     * {@inheritDoc}
     */
    public long getPrepareTime() {
        return prepareTime;
    }

    /**
     * {@inheritDoc}
     */
    public long getCommitTime() {
        return commitTime;
    }

    /**
     * {@inheritDoc}
     */
    public long getAbortTime() {
        return abortTime;
    }

    /**
     * Sets the detail as associated with a participant that has been
     * prepared successfully. If <code>readOnlyParticipant</code> is
     * <code>true</code> then none of the other mutator methods should
     * be called after calling <code>setPrepared</code>.
     *
     * @param time the time in milliseconds that the participant spent
     *             preparing
     * @param readOnlyParticipant whether preparation ended with the
     *                            participant voting read-only
     */
    void setPrepared(long time, boolean readOnlyParticipant) {
	prepareTime = time;
	prepared = true;
	readOnly = readOnlyParticipant;
    }

    /**
     * Sets the detail as associated with a participant that has been
     * committed successfully. None of the other mutator methods should be
     * called after calling <code>setCommitted</code>.
     *
     * @param time the time in milliseconds that the participant spent
     *             committing
     */
    void setCommitted(long time) {
	committed = true;
	commitTime = time;
    }

    /**
     * Sets the detail as associated with a participant that has been
     * directly committed successfully. None of the other mutator methods
     * should be called after calling <code>setCommittedDirectly</code>.
     *
     * @param time the time in milliseconds that the participant spent
     *             preparing and committing
     */
    void setCommittedDirectly(long time) {
	setPrepared(time, false);
	setCommitted(time);
	committedDirectly = true;
    }

    /**
     * Sets the detail as associated with a participant that has been
     * aborted. None of the other mutator methods should be called after
     * calling <code>setAborted</code>.
     *
     * @param time the time in milliseconds that the participant spent
     *             aborting
     */
    void setAborted(long time) {
	abortTime = time;
    }

}
