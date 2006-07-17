
/*
 * ConnectionListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Jul 14, 2006	 7:51:27 PM
 * Desc: 
 *
 */


package com.sun.sgs;


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
