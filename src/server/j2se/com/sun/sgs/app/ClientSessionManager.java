/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app;


/**
 * Manager for obtaining active client sessions.
 */
public interface ClientSessionManager {

    /**
     * Returns the client session for the specified {@code user}, or
     * {@code null} if the user is not active.
     *
     * <p><i>Note: should the parameter be an identity instead?</i>
     *
     * @param	user a user name
     *
     * @return	the client session for the specified {@code user}, or
     *		{@code null}
     *
     * @throws	TransactionException if the operation failed because of
     * 		a problem with the current transaction
     */
    ClientSession getClientSession(String user);
}


