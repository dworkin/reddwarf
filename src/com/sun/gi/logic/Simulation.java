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

package com.sun.gi.logic;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.ObjectStore;

/**
 * Simulation defines the API for a wrapper class for all the game
 * specific resources needed to run a game in the backend slice. One of
 * these is instanced for each game and it in turn holds a refernce back
 * to the SimKernel
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface Simulation {

    /**
     * Adds an object as a listener for users joining or leaving this
     * particular game app. When an event ocurrs, a SmTask is queued for
     * each listenr that will invoke the apropriate event method on the
     * listening GLO.
     * 
     * Important: Any GLO that is going to listene for user events must
     * implement the SimUserListener interface.
     * 
     * @param ref A reference to a GLO to add as a user listener
     */
    public void addUserListener(GLOReference<? extends SimUserListener> ref);

    public void addUserListener(long l);

    /**
     * Register a user data listener. For more information see the
     * SimTask class.
     * 
     * @param uid The user ID associated with this listener.
     * @param ref The reference to the GLO to actually handle the
     * events.
     */
    public void addUserDataListener(UserID uid,
            GLOReference<? extends SimUserDataListener> ref);

    /**
     * Creates a SimTask object that can then be queued for executon.
     * The AccessType for the target object will be ACCESS_GET
     * 
     * @param ref A reference to the GLO to invoke to start the task
     * @param methodToCall The method to invoke on the GLO
     * @param params The parameters to pass to that method
     * 
     * @return the created SimTask.
     */
    public SimTask newTask(GLOReference<? extends GLO> ref,
            Method methodToCall, Object[] params);

    /**
     * Creates a SimTask object that can then be queued for executon.
     * The target object will be fetched from the ObjectStore for
     * execution according to the passed in AccessType. ACCESS_GET will
     * wait for a lock on ref. ACCESS_PEEK will fetch the last comitted
     * value of ref as a task-local copy. ACCESS_ATTEMPT will try to
     * lock the object, if it cannot then the entire task will be
     * discared.
     * 
     * @param access The kind of access to use to fetch ref for
     * execution
     * @param ref A reference to the GLO to invoke to start the task
     * @param methodToCall The method to invoke on the GLO
     * @param params The parameters to pass to that method
     * 
     * @return the created SimTask.
     */
    public SimTask newTask(ACCESS_TYPE access, GLOReference<? extends GLO> ref,
            Method methodToCall, Object[] params);

    /**
     * Returns the string that has been assigned as the name of the game
     * app this simulation object was created for.
     * 
     * @return String The name of the game
     */
    public String getAppName();

    /**
     * Returns the long integer ID that was assigend to this game app
     * when it was installed into the backend.
     * 
     * @return long The ID.
     */
    public long getAppID();

    /**
     * sendUnicastData
     * 
     * @param cid
     * @param target
     * @param buff
     * @param reliable
     */
    public void sendUnicastData(ChannelID cid, UserID target, ByteBuffer buff,
            boolean reliable);

    /**
     * sendMulticastData
     * 
     * @param cid
     * @param targets
     * @param buff
     * @param reliable
     */
    public void sendMulticastData(ChannelID cid, UserID[] targets,
            ByteBuffer buff, boolean reliable);

    /**
     * sendBroadcastData
     * 
     * @param cid
     * @param buff
     * @param reliable
     */
    public void sendBroadcastData(ChannelID cid, ByteBuffer buff,
            boolean reliable);

    public boolean hasTasks();

    public SimTask nextTask();

    /**
     * @param impl
     */
    public void queueTask(SimTask impl);

    /**
     * @param string
     * @return ChannelID a new or existing (possibly non-unique) channel
     * ID for the channel with the given name.
     */
    public ChannelID openChannel(String string);

    public long registerTimerEvent(long tid, ACCESS_TYPE access, long objID,
            long delay, boolean repeat);

    /**
     * @return ObjectStore the object store
     */
    public ObjectStore getObjectStore();

    // Hooks into the RawSocketManager, added 1/16/2006

    /**
     * Requests that a socket be opened at the given host on the given
     * port. The returned ID can be used for future communication with
     * the socket that will be opened. The socket ID will not be valid,
     * and therefore should not be used until the connection is
     * complete. Connection is complete once the
     * SimRawSocketListener.socketOpened() call back is called.
     * 
     * @param access the access type (GET, PEEK, or ATTEMPT)
     * @param objID a reference to the GLO initiating the connection.
     * @param host a String representation of the remote host.
     * @param port the remote port.
     * @param reliable if true, the connection will use a reliable
     * protocol.
     * 
     * @return an identifier that can be used for future communication
     * with the socket.
     */
    public long openSocket(long socketID, ACCESS_TYPE access, long objID,
            String host, int port, boolean reliable);

    /**
     * Sends data on the socket mapped to the given socketID. This
     * method will not return until the entire buffer has been drained.
     * 
     * @param socketID the socket identifier
     * @param data the data to send. The buffer should be in a ready
     * state, i.e. flipped if necessary.
     * 
     * @return the number of bytes sent.
     */
    public long sendRawSocketData(long socketID, ByteBuffer data);

    /**
     * Requests that the socket matching the given socketID be closed.
     * The socket should not be assumed to be closed, however, until the
     * call back SimRawSocketListener.socketClosed() is called.
     * 
     * @param socketID the identifier of the socket.
     */
    public void closeSocket(long socketID);

    /**
     * @return long the next timer ID in the sequence.
     */
    public long getNextTimerID();

    /**
     * @return long the next socket id in the sequence.
     */
    public long getNextSocketID();

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     * 
     * @param user the user
     * @param id the ChannelID
     */
    public void join(UserID user, ChannelID id);

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param user the user
     * @param id the ChannelID
     */
    public void leave(UserID user, ChannelID id);

    /**
     * Locks the given channel based on shouldLock. Users cannot
     * join/leave locked channels except by way of the Router.
     * 
     * @param cid the channel ID
     * @param shouldLock if true, will lock the channel, otherwise
     * unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock);

    /**
     * Closes the local view of the channel mapped to ChannelID. Any
     * remaining users will be notified as the channel is closing.
     * 
     * @param id the ID of the channel to close.
     */
    public void closeChannel(ChannelID id);

    /**
     * Normally the server only gets packets sent specifically to it in
     * the SimUserDataListener.dataArrivedFromChannel callback. However
     * if evesdropping is turned on for a channel,user tuple then every
     * packet sent by that user in that channel will be sent to the
     * SimUserDataListener for that user.
     * 
     * @param uid User to evesdrop on
     * @param cid Channel to evesdrop on
     * @param setting Whether to evesdrop or not
     */
    public void enableEvesdropping(UserID uid, ChannelID cid, boolean setting);
}
