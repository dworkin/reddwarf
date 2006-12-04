
package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.IdentityManager;

import java.util.HashMap;
import java.util.Set;

import javax.security.auth.login.CredentialException;
import javax.security.auth.login.LoginException;


/**
 * This simple implementation of <code>IdentityManager</code> manages a
 * collection of <code>IdentityAuthenticator</code>s.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class IdentityManagerImpl implements IdentityManager
{

    // the available authenticators
    private HashMap<String,IdentityAuthenticator> authenticatorMap;

    /**
     * Creates an instance of <code>IdentityManagerImpl</code> that has
     * the given authenticators available.
     *
     * @param authenticators the available <code>IdentityAuthenticator</code>s
     *
     * @throws IllegalArgumentException if there is more than one authenticator
     *                                  with a given identifier
     */
    public IdentityManagerImpl(Set<IdentityAuthenticator> authenticators) {
        if (authenticators == null)
            throw new NullPointerException("Authenticators must not be null");

        // add the authenticators, making sure that the authenticators
        // are all unique
        authenticatorMap = new HashMap<String,IdentityAuthenticator>();
        for (IdentityAuthenticator authenticator : authenticators) {
            String identifier = authenticator.getIdentifier();

            if (authenticatorMap.containsKey(identifier))
                throw new IllegalArgumentException("Duplicate authenticator " +
                                                   "id: " + identifier);

            authenticatorMap.put(identifier, authenticator);
        }
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
        if (credentials == null)
            throw new NullPointerException("Credentials must not be null");

        IdentityAuthenticator authenticator =
            authenticatorMap.get(credentials.getAuthenticatorId());
        if (authenticator == null)
            throw new CredentialException("Unknown authenticator: " +
                                          credentials.getAuthenticatorId());

        return authenticator.authenticateIdentity(credentials);
    }

}
