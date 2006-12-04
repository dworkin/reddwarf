
package com.sun.sgs.auth;

import javax.security.auth.login.LoginException;


/**
 * A system component that is used to authenticate identities. This interface
 * is provided to <code>Service</code>s and other system components, and is
 * used to authenticate identies. Implementations of this interface use
 * <code>IdentityAuthenticator</code>s to actually perform authentication.
 * Any <code>IdentityManager</code> will ensure that the only authenticators
 * used are those available in the current context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface IdentityManager
{

    /**
     * Authenticates the given credentials.
     *
     * @return an authenticated <code>Identity</code> that has not been
     *         notified of login
     *
     * @throws LoginException if authentication fails
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException;

}
