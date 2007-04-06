/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * Details final profile state associated with a single participant of a
 * transaction.
 */
public interface ProfileParticipantDetail {

    /**
     * Returns the name of this participant.
     *
     * @return the participant's name
     */
    public String getParticipantName();

    /**
     * Returns whether this participant finished preparing to commit at the
     * conclusion of the transaction.
     *
     * @return <code>true</code> if this participant finished preparing,
     *         <code>false</code> otherwise
     */
    public boolean wasPrepared();

    /**
     * Returns the vote that the participant returned from prepartion. If
     * <code>wasPrepared</code> is <code>false</code>, then this method's
     * return value has no meaning and will always return <code>false</code>.
     *
     * @return <code>true</code> if the participant returned from preparation
     *         with a vote of read-only, <code>false</code> otherwise
     */
    public boolean wasReadOnly();

    /**
     * Returns whether this participant was successfully committed.
     *
     * @return <code>true</code> if this participant committed,
     *         <code>false</code> otherwise
     */
    public boolean wasCommitted();

    /**
     * Returns whether this participant was called to prepare and commit in
     * one step (via <code>TransactionParticipant.preareAndCommit</code>).
     * If this returns <code>true</code> then both <code>getPrepareTime</code>
     * and <code>getCommitTime</code> will return the same value, as they
     * represent the same work. If the return from <code>wasCommitted</code>
     * is <code>false</code> then the call to <code>preareAndCommit</code>
     * failed.
     *
     * @return <code>true</code> if this participant was called directly to
     *         commit, <code>false</code> otherwise
     */
    public boolean wasCommittedDirectly();

    /**
     * Returns the length of time this participant spent preparing to commit,
     * regardless of whether preparation succeeded. If this participant was
     * called to commit directly, then this is the time it took both to
     * prepare to commit and to commit.
     *
     * @return the time in milliseconds this participant spent preparing to
     *         commit, or the time spent preparing and committing if called
     *         directly to commit
     */
    public long getPrepareTime();

    /**
     * Returns the length of time this participant spent committing. If the
     * participant failed in preparing to commit, or if the transaction was
     * aborted and therefore this participant never committed, then this
     * method will always return 0. If this participant was called to commit
     * directly, then this is the time it took both to prepare to commit and
     * to commit.
     *
     *  @return the time in milliseconds this participant spent committing,
     *          or the time spent preparing and committing if called directly
     *          to commit
     */
    public long getCommitTime();

    /**
     * Returns the length of time this participant spent aborting if the
     * transaction failed to commit. If the transaction committed, then this
     * method will always return 0.
     *
     * @return the time in milliseconds this participant spent aborting
     */
    public long getAbortTime();

}
