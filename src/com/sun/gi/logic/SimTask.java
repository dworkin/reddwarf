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
import com.sun.gi.framework.rawsocket.SimRawSocketListener;
import com.sun.gi.objectstore.Transaction;

/**
 * @author Jeff Kesselman
 * @version 1.0
 */
public abstract class SimTask {

    private static ThreadLocal<SimTask> current = new ThreadLocal<SimTask>();

    /**
     * This ENUM is used to describe what kind of access you want callback to
     * occur with. They match the simialrly named functions on GLOReference.
     * 
     * @see GLOReference
     */
    public enum ACCESS_TYPE {

	/** Acquire an exclusive write-lock on a GLO */
	GET,

	/** Acquire a shared read-lock on a GLO */
	PEEK,

	/** Attempt an exclusive write-lock, non-blocking */
	ATTEMPT
    }

    /**
     * Called to transfer the calling thread of control to the execution of the
     * task.
     */
    public abstract void execute();

    /**
     * Gets the transaction associated with this SimTask. A SimTask only has a
     * transaction associated with it during execution.
     * 
     * @return the associated transaction or NULL if the SimTask is not
     *         currently executing.
     */
    public abstract Transaction getTransaction();

    // client functions
    // All the functions from here down are used by game application
    // code
    // to talk to its execution context and request services from it.

    /**
     * Returns the applciation ID assigned to the game to which this task
     * belongs.
     * 
     * @return the app ID
     */
    public abstract long getAppID();

    /**
     * Registers a GLO as a listener to user join/left events. The listening GLO
     * must implement the SimUserListener interface.
     * 
     * @param ref
     *            A reference to the GLO to be registered.
     */
    public abstract void addUserListener(
	    GLOReference<? extends SimUserListener> ref);

    /**
     * Registers a GLO as a listener to data packet arrival events. 
     * It listens for data adressed to the UserID passed.
     * 
     * The listening GLO must implement the SimUserDataListener
     * interface.
     * 
     * @param id The UserID that data will be adressed to to trigger
     * this listener
     * 
     * @param ref A reference to the GLO to be registered
     */
    public abstract void addUserDataListener(UserID id,
		    GLOReference<? extends SimUserDataListener> ref);

    /**
     * Send data to a single user on a particular channel.
     * 
     * @param cid the channel ID
     *
     * @param to the message recipient
     *
     * @param data the data packet to send
     *
     * @param reliable true if the data should be sent reliably
     */
    public abstract void sendData(ChannelID cid, UserID to, ByteBuffer data,
	    boolean reliable);

    /**
     * Send data to a set of users by their IDs, on a particular
     * channel.  This actually maps to the sendMulticast call down in
     * the router layer by calling the user manager created to handle
     * this particular game.
     * 
     * @param cid the channel ID
     *
     * @param to the list of message recipients
     *
     * @param data the data packet to send
     *
     * @param reliable true if the data should be sent reliably
     */
    public abstract void sendData(ChannelID cid, UserID[] to, ByteBuffer data,
		    boolean reliable);

    /**
     * Send data to all users on a particular channel.
     * 
     * @param cid the channel ID
     *
     * @param data the data packet to send
     *
     * @param reliable true if the data should be sent reliably
     */
    public abstract void broadcastData(ChannelID cid, ByteBuffer data,
	    boolean reliable);

    /**
     * Creates a GLO in the objectstore from the given template
     * object.
     * 
     * @param simObject the "template object" to insert into the
     * object store, if a GLO does not already exist with the given
     * name.  This object should *not* be used after being passed to
     * the createGLO call -- instead, call get() on the returned
     * GLOReference to get the newly created object.
     * 
     * @param name an optional symbolic reference to assign to the
     * object, or null to create an anonymous GLO.
     * 
     * @return A GLOReference that references the newly created GLO.
     */
    public abstract <T extends GLO> GLOReference<T> createGLO(T simObject,
	    String name);

    /**
     * Create an anonymous GLO in the objectstore.
     * 
     * @param simObject the "template object" to insert into the
     * object store.  This object should *not* be used after being
     * passed to the createGLO call -- instead, call get() on the
     * returned GLOReference to get the newly created object.
     * 
     * @return A GLOReference that references the newly created GLO
     */
    public abstract <T extends GLO> GLOReference<T> createGLO(T simObject);

    // data access functions
    // These functions are used by games to get data from the
    // ObjectStore

    /**
     * This method is used to retrieve an GLReference based on the
     * symbolic name assigned to the GLO at the time of creation in
     * the objectstore.
     * 
     * As is everything else is the obejctstore, symbolic names are
     * specific to a game context.  (The Game's App ID is an implicit
     * part of the key.)
     * 
     * @param gloName The symbolic name to look up.
     * 
     * @return A reference to the GLO if found, null if not found.
     */
    public abstract <T extends GLO> GLOReference<T> findGLO(String gloName);

    /**
     * Destroy all persistence data for the GLO referred to by ref.
     * 
     * @param ref A GLOReference to the GLO to be destroyed.
     */
    public abstract void destroyGLO(GLOReference<? extends GLO> ref);

    /**
     * This method opens a comm channel and returns an ID for it.  If
     * the channel is already open, return a valid ChannelID for the
     * existing channel.  Note that channels may be referred to by
     * more than one channelID.
     * 
     * @param channelName the name of the channel to create
     * 
     * @return a new ChannelID for the channel created.  ChannelIDs
     * are not unique -- a channel may be referred to by more than one
     * channelID.
     */
    public abstract ChannelID openChannel(String channelName);

    /**
     * This function is used to schedule a Task to be launched on a
     * stack-local heartbat timer.  This version of the call schedules
     * the Task to be launched with exclusive access to the called
     * back GLO.  (a "get")
     * 
     * It is a primative function that in of itself will not scale. 
     * APplications should generally use theincluded Persistant
     * Destributed Timer utility (which itself uses this) PDTimer.
     * 
     * @see PDTimer
     * 
     * @param delay Length of time, in seconds, between heartbeat
     * callbacks
     *
     * @param repeat If <code>true</code> then repeat every delay-seconds,
     * if <code>false</code> then only do this once
     *
     * @param ref The SimTimerListener with which to start the Task.
     * 
     * @return an ID for this timer event registration
     */
    public abstract long registerTimerEvent(long delay, boolean repeat,
	    GLOReference<? extends SimTimerListener> ref);

    /**
     * This function is used to schedule a Task to be launched on a
     * stack-local heartbat timer.
     * 
     * It is a primative function that in of itself will not scale. 
     * APplications should generally use theincluded Persistant
     * Destributed Timer utility (which itself uses this) PDTimer.
     * 
     * @see PDTimer
     * 
     * @param access What kind of access (get/peek/attempt) the Task
     * will use to acquire the GLO referred to by ref
     *
     * @param delay Length of time in seconds between heartbeat
     * callbacks
     *
     * @param repeat If <code>true</code> then repeat every delay-seconds,
     * if <code>false</code> then only do this once
     *
     * @param reference The SimTimerListener with which to start the Task.
     * 
     * @return an ID for this timer event registration
     */
    public abstract long registerTimerEvent(ACCESS_TYPE access, long delay,
	    boolean repeat, GLOReference<? extends SimTimerListener> reference);

    /**
     * Returns the GLOReference from which the given GLO was obtained
     * previously in this SimTask.
     * 
     * Note: this operation is only valid for GLOs that have been acquired
     * previously in the course of this SimTask, using get, peek, or attempt.
     * If called with a GLOReference that has not been dereferenced in the
     * current SimTask, null will be returned.
     * 
     * @param glo The GLO to make a GLOreference to.
     * 
     * @return the GLOReference that was used to obtain <i>glo</i> during this
     * SimTask's execution, or null if the GLOReference has not been
     * dereferenced before.
     */
    public abstract <T extends GLO> GLOReference<T> lookupReferenceFor(T glo);

    /**
     * This method is used to queue a child task for execution.  A
     * child task will <em>never</em> be started til after the
     * parent-task has finished processing.
     * 
     * @param accessType Access type used to acquire the initial GLO
     *
     * @param target The Task's initial GLO
     *
     * @param method The method to call upon that GLo to start the
     * Task
     *
     * @param parameters Parameters to pass in that starting call.
     */
    public abstract void queueTask(ACCESS_TYPE accessType,
	    GLOReference<? extends GLO> target, Method method,
	    Object[] parameters);

    /**
     * This is a convenience method, the same as queueTask above but
     * without an Access type parameter.  GET access is assumed if you
     * use this call.
     * 
     * @param target The Task's initial GLO
     *
     * @param method The method to call upon that GLo to start the
     * Task
     *
     * @param parameters Parameters to pass in that starting call.
     */
    public abstract void queueTask(GLOReference<? extends GLO> target,
	    Method method, Object[] parameters);

    /**
     * GLC code is often written with an assumption about the access
     * type that the containg GLO will be acquired with.  For
     * instance, a method that modifies a GLO's state may not want to
     * be called in Access Type PEEK because then the changes will not
     * be saved.
     * 
     * Thsi method provides a way for the GLC code to enforce the
     * access type.  If the access type passed in with thsi call is
     * *not* the current access type of the passed GLO, this method
     * will throw an AccessTypeViolationException which will result in
     * the run of the Task being aborted if not caught by the
     * surrounding GLC code.
     * 
     * @param accessType The required Access Type
     * 
     * @param glo The GLO to check the Access Type of
     * 
     * @throws AccessTypeViolationException
     */
    public abstract void access_check(ACCESS_TYPE accessType, GLO glo);

    // Hooks into the RawSocketManager, added 1/16/2006

    /**
     * Requests that a socket be opened at the given host on the given
     * port.  The returned ID can be used for future communication
     * with the socket that will be opened.  The socket ID will not be
     * valid, and therefore should not be used until the connection is
     * complete.  Connection is complete once the
     * SimRawSocketListener.socketOpened() call back is called.
     * 
     * @param access the access type (GET, PEEK, or ATTEMPT)
     *
     * @param ref a reference to the GLO initiating the connection
     *
     * @param host a String representation of the remote host
     *
     * @param port the remote port
     *
     * @param reliable if true, the connection will use a reliable
     * protocol
     * 
     * @return an identifier that can be used for future communication
     * with the socket
     */
    public abstract long openSocket(ACCESS_TYPE access,
	    GLOReference<? extends SimRawSocketListener> ref, String host,
	    int port, boolean reliable);

    /**
     * Sends data on the socket mapped to the given socketID.  This
     * method will not return until the entire buffer has been
     * drained.
     * 
     * @param socketID the socket identifier.
     *
     * @param data the data to send.  The buffer should be in a ready
     * state, i.e. flipped if necessary.
     */
    public abstract void sendRawSocketData(long socketID, ByteBuffer data);

    /**
     * Requests that the socket matching the given socketID be closed. 
     * The socket should not be assumed to be closed, however, until
     * the call back SimRawSocketListener.socketClosed() is called.
     * 
     * @param socketID the identifier of the socket.
     */
    public abstract void closeSocket(long socketID);

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     * 
     * @param user the user
     *
     * @param id the ChannelID
     */
    public abstract void join(UserID user, ChannelID id);

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param user the user
     *
     * @param id the ChannelID
     */
    public abstract void leave(UserID user, ChannelID id);

    /**
     * Locks the given channel based on shouldLock.  Users cannot
     * join/leave locked channels except by way of the Router.
     * 
     * @param cid the channel ID
     *
     * @param shouldLock if true, will lock the channel, otherwise
     * unlock it.
     */
    public abstract void lock(ChannelID cid, boolean shouldLock);

    /**
     * Closes the local view of the channel mapped to ChannelID.  Any
     * remaining users will be notified as the channel is closing.
     * 
     * @param cid the ID of the channel to close.
     */
    public abstract void closeChannel(ChannelID cid);

    /**
     * Enables/disables eavesdropping for a given user/channel pair. 
     * <p>
     *
     * If there is a SimDataUserListener on the given user then
     * enable/disable that listener to recieve dataArrivedFromChannel
     * callbacks from this user to the given channel.  By default no
     * messages are eavedropped.  <p>
     *
     * @param uid the user to eavesdrop
     *
     * @param cid the channel on which to eavesdrop that user
     *
     * @param setting if <code>true</code> then enable eavesdropping
     * for the combination of user and channel, if <code>false</code>
     * then disable
     */
    public abstract void setEvesdroppingEnabled(UserID uid, ChannelID cid,
	    boolean setting);

    /**
     * Gets the SimTask for the currently executing event.
     * 
     * @return the current SimTask context
     * 
     * @throws ExecutionOutsideOfTaskException if called outside of
     * the context of an SGS task dispatch
     */
    public static SimTask getCurrent()
	    throws ExecutionOutsideOfTaskException
    {
	SimTask simTask = current.get();

	if (simTask == null) {
	    throw new ExecutionOutsideOfTaskException();
	}

	return simTask;
    }

    /**
     * <b>THIS IS AN INTERNAL FUNCTION AND SHOULD NEVER BE CALLED BY
     * SGS APPLICATION CODE!</b>
     * 
     * It is used by the system as GLOs are acquired to store data
     * about them in the SimTask for use by other methods.
     * 
     * @param objID The ID of the object
     *
     * @param glo A pointer to the GLO itself
     *
     * @param access The access type used to acquire the object
     */
    public abstract void registerGLOID(long objID, GLO glo, ACCESS_TYPE access);

    /**
     * Sets the {@link SimTask} returned by
     * <code>SimTask.getCurrent()</code>.  <p>
     *
     * <b>This is an internal function and should never be called by
     * SGS application code!</b>
     */
    protected void setAsCurrent() {
	current.set(this);
    }

    /**
     * Returns the Game Name assigned to this simulation when it was
     * installed into the SGS back end.
     *
     * @return The name of the application of which this task is part
     */
    public abstract String getAppName();

    /**
     * Returns the UserID of this task.
     * @return the UserID of this task
     */
    public abstract UserID getUserID();

    /**
     * <b>This is an internal function and should never be called by
     * SGS application code!</b>
     *
     * A utility call used by other parts of the system. It takes a Game Logic
     * Object (GLO) ID and wraps it in a GLOReference.
     * 
     * @param id the GLO id to which a reference will be created
     * 
     * @return a GLOReference that may be used by another GLO.
     */
    public abstract GLOReference<? extends GLO> makeReference(long id);
}
