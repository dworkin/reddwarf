

package com.sun.sgs.manager.listen;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.User;


/**
 * This is a callback used to listen for connection events. It is called
 * when any user joins or leaves the server.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ConnectionListener extends ManagedObject
{

    /**
     * Called when some user joins the server.
     *
     * @param user the user who joined
     */
    public void joined(User user);

    /**
     * Called when some user leaves the server.
     *
     * @param user the user who left
     */
    public void left(User user);

}
