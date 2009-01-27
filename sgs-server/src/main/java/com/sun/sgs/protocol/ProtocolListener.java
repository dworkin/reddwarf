/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.protocol;

import com.sun.sgs.auth.Identity;
import java.util.concurrent.ExecutionException;

/**
 * The listener for incoming protocol connections.
 */
public interface ProtocolListener {

    /**
     * Handles a new login request for the specified {@code identity} and
     * corresponding {@code protocol}, and returns a future for the result,
     * a {@link SessionProtocolHandler} for processing incoming requests
     * received by the protocol.
     *
     * <p>If the login request is processed successfully, then invoking the
     * {@link LoginCompletionFuture#get get} method on the returned future
     * returns the {@code SessionProtocolHandler} for processing incoming
     * requests received by the protocol.  If the login was unsuccessful,
     * the {@code get} method throws {@link ExecutionException} which
     * contains a <i>cause</i> that indicates why the login failed.
     *
     * @param	identity an identity
     * @param	protocol a session protocol
     * @return	a future for obtaining the protocol handler
     */
    LoginCompletionFuture newLogin(
	Identity identity, SessionProtocol protocol);
}
