
package com.sun.sgs.auth;


/**
 * Represents credentials that can be used for authentication. These
 * credentials are tied to a particular mechanism for authentication.
 * Implementations of <code>IdentityCredentials</code> should not
 * actually contain any authentication logic. This should instead be
 * part of the consuming <code>IdentityAuthenticator</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface IdentityCredentials
{

    /**
     * Returns the identifier for the mechanism that uses these credentials
     * to authenticate an identity. Typically, this is identifiying an
     * <code>IdentityAuthenticator</code>.
     *
     * @return an identifier for the consumer of these credentials
     */
    public String getAuthenticatorId();

}
