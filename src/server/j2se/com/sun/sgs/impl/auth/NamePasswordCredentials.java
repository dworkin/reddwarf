
package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.IdentityCredentials;


/**
 * This simple implementation of <code>IdentityCredentials</code> is used to
 * represent a name and password pair.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class NamePasswordCredentials implements IdentityCredentials
{

    // the name and password
    private final String name;
    private final char [] password;

    // identifier for the authenticating mechanism
    private final String authenticatorId;

    /**
     * Creates an instance of <code>NamePasswordCredentials</code> for use
     * with its default authentication mechanism.
     *
     * @param name the name
     * @param password the password
     */
    public NamePasswordCredentials(String name, char [] password) {
        this(name, password, NamePasswordAuthenticator.IDENTIFIER);
    }

    /**
     * Creates an instance of <code>NamePasswordCredentials</code> for use
     * with a specific authentication mechanism. This is typically used when
     * more than mechanism is implemented that knows how to do authentication
     * based on using a name and password.
     *
     * @param name the name
     * @param password the password
     */
    public NamePasswordCredentials(String name, char [] password,
                                   String authenticatorId) {
        this.name = name;
        this.password = password;
        this.authenticatorId = authenticatorId;
    }

    /**
     * {@inheritDoc}
     */
    public String getAuthenticatorId() {
        return authenticatorId;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the password.
     *
     * @return the password
     */
    public char [] getPassword() {
        return password;
    }

}
