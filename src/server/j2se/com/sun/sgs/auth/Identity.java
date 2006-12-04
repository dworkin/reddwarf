
package com.sun.sgs.auth;


/**
 * This interface represents a single identity in a form used only by
 * <code>Service</code>s, the kernel, and other system-level components.
 * Implementations must also implement <code>Serializable</code>,
 * <code>equals</code>, and <code>hashCode</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Identity {

    /**
     * Returns the name associated with this identity.
     *
     * @return the identity's name
     */
    public String getName();

    /**
     * Notifies the system that this identity has logged in under the
     * current context. Typically this is done shortly after authenticating
     * the identity. Note that it is valid to authenticate an identity that
     * does not log into the system.
     */
    public void notifyLoggedIn();

    /**
     * Notifies the system that this identity has logged out from the
     * current context. Typically this is done after a client disconnects
     * from the system.
     */
    public void notifyLoggedOut();

}
