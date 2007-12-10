/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

/**
 * A future to be notified when recovery operations for an associated
 * {@link RecoveryListener} are complete.
 *
 * @see RecoveryListener#recover(Node,RecoveryCompleteFuture)
 */
public interface RecoveryCompleteFuture {

    /**
     * Notifies this future that the recovery operations initiated by
     * the {@link RecoveryListener} associated with this future are
     * complete.
     */
    void done();

    /**
     * Returns {@code true} if the {@link #done done} method of this
     * future has been invoked, and {@code false} otherwise.
     *
     * @return	{@code true} if {@code done} has been invoked, and
     *		{@code false} otherwise 
     */
    boolean isDone();
}
