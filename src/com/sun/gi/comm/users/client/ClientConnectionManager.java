/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.comm.users.client;

import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

/**
 * ClientConnectionManager defines the central client API for connecting
 * into the SGS system. An instance of ClientConnectionManager
 * represents the context of a single user of the SGS server.
 * Multiple instances maybe maintained by the same program. (One example
 * of where multiple users might be needed is in a load-testing app.)
 * 
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface ClientConnectionManager {

    /**
     * Sets a listener for ClientConnectionManager events. Only one
     * listener may be set at a time. Setting a second listener removes
     * the first one.
     * 
     * @param l the object to listen for events.
     */
    public void setListener(ClientConnectionManagerListener l);

    /**
     * Returns the class names of all the UserManagers available to
     * connect to.
     * <p>
     * <b>NOTE: It is assumed that the game name and discovery
     * information was set in the constructor.</b>
     * 
     * @return a String array containing the fully-qualified class names
     * of all the allowed UserManagers for this game.
     */
    public String[] getUserManagerClassNames();

    /**
     * Makes a connection to a game in the SGS backend. A return value
     * of <tt>true</tt> only means that a connection is being
     * attempted, not that connection has sucessfully completed. To know
     * when you are fully connected, use the
     * ClientConnectionManagerListener.
     * 
     * Because a server may linger in the discovery data for a period
     * after it actually dies, or may otherwise be unavailable even
     * though it is in the discovery list, the API will try to initiate
     * multiple connection attempts before giving up and returning
     * false. The number of attempts it tries, and the tiem it sleeps
     * between attempts are controlled by the system properties
     * "sgs.clientconnmgr.connattempts" and
     * "sgs.clientconnmgr.connwait". If these are unset default values
     * of 10 attempst and 100ms are used.
     * 
     * @param userManagerClassName The fully-qualified class name of the
     * UserManager to connect to.
     * 
     * @return true if the conenction is being attempted, false if not.
     * (For instance if the named userManagerClassName is not supported
     * by the game.)
     * 
     * @throws ClientAlreadyConnectedException if the ClientConnection
     * Manager is already connected to a game.
     * 
     * @see ClientConnectionManagerListener
     */
    public boolean connect(String userManagerClassName)
            throws ClientAlreadyConnectedException;

    /**
     * Makes a connection to a game in the SGS backend. A return value
     * of <tt>true</tt> only means that a connection is being
     * attempted, not that connection has sucessfully completed. To know
     * when you are fully connected, use the
     * ClientConnectionManagerListener.
     * 
     * Because a sever may linger in the discovery data for a period
     * after it actually dies, or may otherwise be unavailable, the API
     * will try to initiate multiple connection attempts before giving
     * up and returning false. The number of attempts it tries, and the
     * tiem it sleeps between attempts is controlled by the second and
     * third parameter.
     * 
     * @param userManagerClassName the fully-qualified class name of the
     * UserManager to connect to
     * 
     * @param connectAttempts how many times to try to connect before
     * returning false
     * 
     * @param msBetweenAttempts how many ms to sleep between connection
     * attempts
     * 
     * @return true if the conenction is being attempted, false if not.
     * (For instance if the named userManagerClassName is not supported
     * by the game.)
     * 
     * @throws ClientAlreadyConnectedException if the ClientConnection
     * Manager is already connected to a game.
     * 
     * @see ClientConnectionManagerListener
     */
    public boolean connect(String userManagerClassName, int connectAttempts,
            long msBetweenAttempts) throws ClientAlreadyConnectedException;

    /**
     * Initiates disconnection from a game in the SGS back end. To know
     * when disconnection has occurred, use the callbacks in
     * ClientConnectionManagerListener.
     * 
     * @see ClientConnectionManagerListener
     */
    public void disconnect();

    /**
     * Provides validation information to the SGS backend that was
     * requested via a ClientConnectionManagerListener callback.
     * 
     * @param cbs the filled-in validation information
     * @see Callback
     */
    public void sendValidationResponse(Callback[] cbs);

    /**
     * Sends a data packet to the game logic residing on the SGS server.
     * It will be processed by whatever Game Logic Object (GLO) has been
     * registered on the server-side to handle data from this user.
     * 
     * @param buff the data to send
     * @param reliable true if this data should be transmitted reliably
     */
    public void sendToServer(ByteBuffer buff, boolean reliable);

    /**
     * Opens a channel. Once opened the channel is returned for access
     * via a callback on the registered ClientConnectionManagerListener.
     * 
     * @param channelName The name of the channel to open.
     * 
     * @see ClientConnectionManagerListener
     */
    public void openChannel(String channelName);

    /**
     * Determines whether a packet's userID is that of the SGS backend.
     * 
     * @param userid userid to test
     * @return true iff userid is the SGS server's user id
     */
    public boolean isServerID(byte[] userid);

    public static final SGSUUID SERVER_ID = new StatisticalUUID(-1, -1);
}
