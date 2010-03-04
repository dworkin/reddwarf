/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.service;

/**
 * A handler to be notified when operations for an associated
 * request are complete.
 *
 * @see ClientSessionStatusListener#prepareToRelocate(
 *		BigInteger,long,SimpleCompletionHandler)
 * @see IdentityRelocationListener#prepareToRelocate(
 *		Identity,long,SimpleCompletionHandler)
 * @see RecoveryListener#recover(Node,SimpleCompletionHandler)
 */
public interface SimpleCompletionHandler {

    /**
     * Notifies this handler that the operations initiated by the
     * request associated with this future are complete.  This
     * method is idempotent and can be called multiple times.
     */
    void completed();
}
