
package com.sun.sgs.auth;

import com.sun.sgs.kernel.KernelAppContext;

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
     * Returns the identifiers for this <code>IdentityAuthenticator</code>'s
     * supported credential types. This may contain any number of
     * identifiers, which are matched against the identifier of
     * <code>IdentityCredential</code>s to determine if this
     * <code>IdentityAuthenticator</code> can consume those credentials.
     *
     * @return the identifiers for the supported credential types
     */
    public String [] getSupportedCredentialTypes();

    /**
     * Tells this <code>IdentityAuthenticator</code> the context in which
     * it is running. This should only be called once for the lifetime of
     * this authenticator.
     *
     * @param context the context in which identities are authenticated
     *
     * @throws IllegalStateException if the context has already been assigned
     */
    public void assignContext(KernelAppContext context);

    /**
     * Authenticates the given credentials. The returned <code>Identity</code>
     * is valid, but has not yet been notified as logged in.
     *
     * @param credentials the <code>IdentityCredentials</code> to authenticate
     *
     * @return an authenticated <code>Identity</code>
     *
     * @throws LoginException if authentication fails
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException;

}
