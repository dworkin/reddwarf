
package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.Identity;

import java.util.Properties;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.CredentialException;


/**
 * This simple implementation provides authentication based on a name and
 * a password.
 * <p>
 * FIXME: no auctual authentication is implemented here yet. Users are
 * assumed to have provided the correct password. This needs to be fixed
 * once we decide the the interfaces look correct, and what we want to
 * use as the default mechanism. This will probably be a flat text file
 * with a user name and SHA-1 hashed password separated by whitespace.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class NamePasswordAuthenticator implements IdentityAuthenticator
{

    /**
     * The identifier for this <code>IdentityAuthenticator</code>.
     */
    public static final String IDENTIFIER = "DefaultNamePasswordAuthenticator";

    /**
     * Creates an instance of <code>NamePasswordAuthenticator</code>.
     *
     * @param properties the application's configuration properties
     */
    public NamePasswordAuthenticator(Properties properties) {

    }

    /**
     * {@inheritDoc}
     */
    public String getIdentifier() {
        return IDENTIFIER;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The provided <code>IdentityCredentials</code> must be an instance
     * of <code>NamePasswordCredentials</code>
     *
     * @throws AccountNotFoundException if the identity is unknown
     * @throws CredentialException if the credentials are invalid
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws AccountNotFoundException, CredentialException
    {
        if (! (credentials instanceof NamePasswordCredentials))
            throw new CredentialException("unsupported credentials");
        NamePasswordCredentials npc = (NamePasswordCredentials)credentials;
        return new IdentityImpl(npc.getName());
    }

}
