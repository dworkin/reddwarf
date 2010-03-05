/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.profile;


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
    String getParticipantName();

    /**
     * Returns whether this participant finished preparing to commit at the
     * conclusion of the transaction.
     *
     * @return <code>true</code> if this participant finished preparing,
     *         <code>false</code> otherwise
     */
    boolean wasPrepared();

    /**
     * Returns the vote that the participant returned from preparation. If
     * <code>wasPrepared</code> is <code>false</code>, then this method's
     * return value has no meaning and will always return <code>false</code>.
     *
     * @return <code>true</code> if the participant returned from preparation
     *         with a vote of read-only, <code>false</code> otherwise
     */
    boolean wasReadOnly();

    /**
     * Returns whether this participant was successfully committed.
     *
     * @return <code>true</code> if this participant committed,
     *         <code>false</code> otherwise
     */
    boolean wasCommitted();

    /**
     * Returns whether this participant was called to prepare and commit in
     * one step (via <code>TransactionParticipant.prepareAndCommit</code>).
     * If this returns <code>true</code> then both <code>getPrepareTime</code>
     * and <code>getCommitTime</code> will return the same value, as they
     * represent the same work. If the return from <code>wasCommitted</code>
     * is <code>false</code> then the call to <code>prepareAndCommit</code>
     * failed.
     *
     * @return <code>true</code> if this participant was called directly to
     *         commit, <code>false</code> otherwise
     */
    boolean wasCommittedDirectly();

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
    long getPrepareTime();

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
    long getCommitTime();

    /**
     * Returns the length of time this participant spent aborting if the
     * transaction failed to commit. If the transaction committed, then this
     * method will always return 0.
     *
     * @return the time in milliseconds this participant spent aborting
     */
    long getAbortTime();

}
