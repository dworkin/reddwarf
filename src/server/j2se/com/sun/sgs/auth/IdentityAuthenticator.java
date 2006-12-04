
package com.sun.sgs.auth;

import javax.security.auth.login.LoginException;


/**
 * This interface is used to define modules that know how to authenticate
 * an identity based on provided credentials. The credentials must be of
 * a form recognizable to the implementation. Note that each application
 * context has its own instances of <code>IdentityAuthenticator</code>s.
 * Typically implementations of <code>IdentityAuthenticator</code> are
 * invoked by a containing <code>IdentityManager</code>.
 * <p>
 * All implementations of <code>IdentityAuthenticator</code> must have a
 * constructor that accepts an instance of <code>java.util.Properties</code>.
 * The provided properties are part of the application's configuration.
 * <p>
 * FIXME: When the IO interfaces are ready, these should also be provided
 * to the constructor.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface IdentityAuthenticator
{

    /**
     * Returns this <code>IdentityAuthenticator</code>s identifier. This
     * is the same identifier used by <code>IdentityCredentials</code>s to
     * identify their authentication mechanism.
     *
     * @return this authenticator's identifier
     */
    public String getIdentifier();

    /**
     * Authenticates the given credentials. The returned <code>Identity</code>
     * is valid, but has not yet been notified as logged in.
     *
     * @return an authenticated <code>Identity</code>
     *
     * @throws LoginException if authentication fails
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException;

}
