/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.kernel.ProfileParticipantDetail;


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
    boolean prepared = false;
    long prepareTime = 0;

    // the result from preparation
    boolean readOnly = false;

    // whether the participant succesfully committed, and how long it took
    // if the participant committed
    boolean committed = false;
    long commitTime = 0;

    // whether prepareAndCommit was called on this participant
    boolean committedDirectly = false;

    // if the participant was aborted, how long it took to abort
    long abortTime = 0;

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

}
