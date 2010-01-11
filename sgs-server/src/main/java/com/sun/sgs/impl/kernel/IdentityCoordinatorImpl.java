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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.auth.IdentityCredentials;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.login.CredentialException;
import javax.security.auth.login.LoginException;


/**
 * This package-private implementation of <code>IdentityCoordinator</code> is
 * the implementation used by the system when configuring an application. It
 * manages an ordered collection of <code>IdentityAuthenticator</code>s. The
 * ordering is used to determine precidence when more than one authenticator
 * can consume the same credentials.
 * <p>
 * This implementation is kept private to this package so that the context
 * can be assigned, safely, after the coordinator is created. An instance of the
 * coordinator is needed before the context can be created.
 */
class IdentityCoordinatorImpl implements IdentityCoordinator
{

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(IdentityCoordinatorImpl.
                                           class.getName()));

    // the available authenticators
    private final HashMap<String, List<IdentityAuthenticator>> authenticatorMap;

    // a unique collection of the authenticators, for management purposes,
    // and currently unused
    private final HashSet<IdentityAuthenticator> authenticatorSet;

    /**
     * Creates an instance of <code>IdentityCoordinatorImpl</code> that has
     * the given <code>IdentityAuthenticators</code> available to authenticate
     * identities. The order of the authenticators determines precidence
     * when more than one authenticator supports a given credentials type.
     *
     * @param authenticators the available <code>IdentityAuthenticator</code>s
     */
    public IdentityCoordinatorImpl(List<IdentityAuthenticator> authenticators) {
        if (authenticators == null) {
            throw new NullPointerException("Authenticators must not be null");
        }

        // add the authenticators in the right order
        authenticatorMap = new HashMap<String, List<IdentityAuthenticator>>();
        for (IdentityAuthenticator authenticator : authenticators) {
            String [] identifiers =
                authenticator.getSupportedCredentialTypes();
            for (String identifier : identifiers) {
                List<IdentityAuthenticator> list =
                    authenticatorMap.get(identifier);
                if (list == null) {
                    list = new ArrayList<IdentityAuthenticator>();
                    authenticatorMap.put(identifier, list);
                }
                list.add(authenticator);
            }
        }

        // keep track of the collection
        authenticatorSet = new HashSet<IdentityAuthenticator>(authenticators);
    }

    /**
     * {@inheritDoc}
     *
     * @throws CredentialException if the authenticator for the credentials
     *                             is unknown
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException
    {
        if (credentials == null) {
            throw new NullPointerException("Credentials must not be null");
        }

        List<IdentityAuthenticator> authenticators =
            authenticatorMap.get(credentials.getCredentialsType());
        if (authenticators == null) {
            throw new CredentialException("Unsupported credentials type: " +
                                          credentials.getCredentialsType());
        }

        for (IdentityAuthenticator authenticator : authenticators) {
            try {
                return authenticator.authenticateIdentity(credentials);
            } catch (LoginException le) {
                // NOTE: because there could be multiple authenticators, it's
                // not possible to return all errors, and besides it's not
                // generally meaningful to return errors from different
                // authenticators since some of them might be expected
                // behavior. So, for now, these errors are being ignored
                if (logger.isLoggable(Level.FINEST)) {
                    logger.logThrow(Level.FINEST, le, "Could not " +
                                    "authenticate credentials with " +
                                    "authenticator {0}",
                                    authenticator.getClass().getName());
                }
            }
        }

        // no authenticators worked
        throw new CredentialException("Could not authenticate identity");
    }

}
