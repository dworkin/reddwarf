/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util.lock;

/** The type of a lock conflict detected by a {@link LockManager}. */
public enum LockConflictType {

    /** The request is currently blocked. */
    BLOCKED,

    /** The request timed out. */
    TIMEOUT,

    /** The request was denied. */
    DENIED,

    /** The request was interrupted. */
    INTERRUPTED,

    /** The request resulted in deadlock and was chosen to be aborted. */
    DEADLOCK;
}
