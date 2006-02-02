package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

/**
 * <p>Title: ClientConnectionManager
 * <p>Description: This interface defines the central client API for connecting
 * into the Darkstar system.  An instance of ClientConnectionManager represents
 * the context of a single user of the Darkstar server.  Multiple instances
 * maybe maintained by the same program.  (One example of where multiple users
 * might be needed is in a load-testing app.)</p>
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p> New look
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface ClientConnectionManager {

    /**
     * Sets a listener for ClientConnectionManager events.
     * Only one listener may be set at a time.  Setting a second
     * listener removes the first one.
     *
     * @param l  the object to listen for events.
     */
    public void setListener(ClientConnectionManagerListener l);

    /**
     * Returns the class names of all the UserManagers available to
     * connect to.
     * <p>
     * <b>NOTE: It is assumed that the game name and discovery
     * information was set in the constructor.</b>
     *
     * @return a String array containing the FQCNs of all the allowed
     * UserManagers for this game.
     */
    public String[] getUserManagerClassNames();

    /**
     * Makes a connection to a game in the Darkstar backend.  A return
     * value of <tt>true</tt> only means that a connection is being
     * attempted, not that connection has sucessfully completed.   To
     * know when you are fully connected, use the
     * ClientConnectionManagerListener.
     *
     * Because a server may linger in the discovery data for a period
     * after it actually dies, or may otherwise be unavailable even
     * though it is in the discovery list, the API will try to
     * initiate multiple connection attempts before giving up and
     * returning false.  The number of attempts it tries, and the tiem
     * it sleeps between attempts are controlled by the system
     * properties "sgs.clientconnmgr.connattempts" and
     * "sgs.clientconnmgr.connwait".  If these are unset default
     * values of 10 attempst and 100ms are used.
     *
     * @param userManagerClassName The FQCN of the UserManager to connect to.
     *
     * @return true if the conenction is being attempted, false if not.
     *         (For instance if the named userManagerClassName is not
     *         supported by the game.)
     *
     * @throws ClientAlreadyConnectedException if the ClientConnection
     * Manager is already connected to a game.
     *
     * @see ClientConnectionManagerListener
     */
    public boolean connect(String userManagerClassName)
	throws ClientAlreadyConnectedException;


    /**
     * Makes a connection to a game in the Darkstar backend.  A return
     * value of <tt>true</tt> only means that a connection is being
     * attempted, not that connection has sucessfully completed.   To
     * know when you are fully connected, use the
     * ClientConnectionManagerListener.
     *
     * Because a sever may linger in the discovery data for a period
     * after it actually dies, or may otherwise be unavailable, the
     * API will try to initiate multiple connection attempts before
     * giving up and returning false.  The number of attempts it
     * tries, and the tiem it sleeps between attempts is controlled by
     * the second and third parameter.
     *
     * @param userManagerClassName  the FQCN of the UserManager to connect to
     *
     * @param connectAttempts       how many times to try to
     *                              connect before returning false
     *
     * @param msBetweenAttempts     how many ms to sleep between
     *                              connection attempts
     *
     * @return true if the conenction is being attempted, false if not.
     *         (For instance if the named userManagerClassName is not
     *         supported by the game.)
     *
     * @throws ClientAlreadyConnectedException if the ClientConnection Manager is already connected to a game.
     *
     * @see ClientConnectionManagerListener
     */
    public boolean connect(String userManagerClassName, int connectAttempts,
	long msBetweenAttempts) throws ClientAlreadyConnectedException;

    /**
     * Initiates disconnection from a game in the Darkstar back end.
     * To know when disconnection has occurred, use the callbacks in
     * ClientConnectionManagerListener.
     *
     * @see ClientConnectionManagerListener
     */
    public void disconnect();

    /**
     * Provides validation information to the Darkstar backend that
     * was requested via a ClientConnectionManagerListener callback.
     *
     * @param cbs  the filled-in validation information
     * @see Callback
     */
    public void sendValidationResponse(Callback[] cbs);

    /**
     * Sends a data packet to the game logic residing on the Darkstar
     * server.  It will be processed by whatever Game Logic Object
     * (GLO) has been registered on the server-side to handle data
     * from this particular user.
     *
     * @param buff     the data to send
     * @param reliable true if this data should be transmitted reliably
     */
    public void sendToServer(ByteBuffer buff,boolean reliable);

    /**
     * Opens a channel.  Once opened the channel is returned for access
     * via a callback on the registered ClientConnectionManagerListener.
     *
     * @param channelName The name of the channel to open.
     *
     * @see ClientConnectionManagerListener
     */
    public void openChannel(String channelName);

    /**
     * Determines whether a packet's userID is that of the Darkstar backend.
     *
     * @param  userid userid to test
     * @return true iff userid is the Darkstar Server's user id
     */
    public boolean isServerID(byte[] userid);
}
