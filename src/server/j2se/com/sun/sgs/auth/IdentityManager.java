
package com.sun.sgs.auth;

import javax.security.auth.login.LoginException;


/**
 * A system component that is used to authenticate identities. This interface
 * is provided to <code>Service</code>s and other system components, and is
 * used to authenticate identities within a specific context. Implementations
 * of this interface use <code>IdentityAuthenticator</code>s to actually
 * perform authentication.
 * <p>
 * Note that the <code>IdentityManager</code> provided to
 * <code>Service</code>s via the <code>ComponentRegistry</code> field of
 * their constructor will only be able to authenticate identities within
 * that <code>Service</code>'s context. It is safe, however, to use
 * that <code>IdentityManager</code> in any context and outside of a
 * running transaction. <code>Service</code>s must not, however, use
 * their <code>IdentityManager</code> until <code>configure</code> is
 * called, because before this point the underlying context is not
 * valid and available to the <code>IdentityManager</code>.
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
