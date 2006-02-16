package com.sun.gi.logic.impl;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimChannelDataListener;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company:
 * </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class SimulationImpl implements Simulation {
    SimKernel kernel;

    Router router;

    long appID;
    ObjectStore ostore;

    ClassLoader loader;

    private List<Long> userListeners = new ArrayList<Long>();

    private String appName;

    private Map<UserID, List<Long>> userDataListeners = new HashMap<UserID, List<Long>>();

    private Map<ChannelID, List<Long>> channelListeners = new HashMap<ChannelID, List<Long>>();

    private Map<ChannelID, List<Long>> channelDataListeners = new HashMap<ChannelID, List<Long>>();

    private List<SimTask> taskQueue = new LinkedList<SimTask>();

    /**
     * Constructor
     */
    public SimulationImpl(SimKernel kernel, ObjectStore ostore, Router router, DeploymentRec game)
	    throws InstantiationException {

	this.kernel = kernel;
	this.appName = game.getName();
	this.ostore = ostore;
	this.router = router;
	this.appID = game.getID();

	router.addRouterListener(new RouterListener() {
	    public void serverMessage(UserID from, ByteBuffer data,
		    boolean reliable) {
		fireServerMessage(from, data);
	    }

	    public void userJoined(UserID uid, Subject subject) {
		fireUserJoined(uid, subject);
	    }

	    public void userLeft(UserID uid) {
		fireUserLeft(uid);
	    }

	    public void userJoinedChannel(UserID uid, ChannelID cid) {
		fireUserJoinedChannel(uid, cid);
	    }

	    public void userLeftChannel(UserID uid, ChannelID cid) {
		fireUserLeftChannel(uid, cid);
	    }

	    public void channelDataPacket(ChannelID cid, UserID from,
		    ByteBuffer buff) {
		fireChannelDataPacket(cid, from, buff);
	    }
	});

	final String bootClassName = game.getBootClass();
	if (bootClassName != null) { // has server side
	    try {
		loader = new URLClassLoader(new URL[]
		    { new URL(game.getClasspathURL()) });

		Class bootclass = loader.loadClass(bootClassName);

		Method startMethod = bootclass.getMethod("boot",
		    new Class[] { SimTask.class, boolean.class });

		// Check for existing boot object in objectstore...

		Transaction trans =
		    ostore.newTransaction(bootclass.getClassLoader());

		trans.start();
		boolean firstTime = false;
		long bootObjectID = trans.lookup("BOOT");
		if (bootObjectID == ObjectStore.INVALID_ID) {
		    // boot object doesn't exist; create it
		    bootObjectID =
			trans.create((GLO) bootclass.newInstance(), "BOOT");
		    firstTime = true;
		}

		trans.commit();
		queueTask(newTask(bootObjectID,
		    startMethod, new Object[] { firstTime }));

	    } catch (MalformedURLException e) {
		e.printStackTrace();
		throw new InstantiationException(e.getMessage());
	    } catch (ClassNotFoundException e) {
		e.printStackTrace();
		throw new InstantiationException(e.getMessage());
	    } catch (NoSuchMethodException e) {
		throw new InstantiationException(
		    "Boot class in sim has no method: " +
		    "void boot(SimTask, boolean)");
	    } catch (IllegalAccessException e) {
		e.printStackTrace();
		throw new InstantiationException(e.getMessage());
	    }
	}
	kernel.addSimulation(this);
    }

    protected void fireChannelDataPacket(ChannelID cid, UserID from,
	    ByteBuffer buff) {
	List<Long> listeners = channelDataListeners.get(cid);
	if (listeners != null) {
	    ByteBuffer outBuff = buff.duplicate();
	    outBuff.flip();
	    Method m;
	    try {
		m = SimChannelDataListener.class.getMethod("dataArrived",
			SimTask.class, ChannelID.class, UserID.class,
			ByteBuffer.class);
		for (Long uid : listeners) {
		    queueTask(newTask(uid.longValue(), m, new Object[] { cid,
				from, outBuff.duplicate() }));
		}
	    } catch (SecurityException e) {
		e.printStackTrace();
	    } catch (NoSuchMethodException e) {
		e.printStackTrace();
	    }
	}
    }

    /**
     * @param task
     */
    public void queueTask(SimTask task) {
	synchronized (taskQueue) {
	    taskQueue.add(task);
	}
	kernel.simHasNewTask();

    }

    /**
     * addUserListener
     *
     * @param ref
     *
     */
    public void addUserListener(GLOReference ref) {
	userListeners.add(new Long(((GLOReferenceImpl) ref).objID));
    }

    // internal
    private SimTask newTask(ACCESS_TYPE access,long startObject, Method startMethod,
	    Object[] params) {
	return new SimTaskImpl(this, loader, access, startObject, startMethod, params);
    }

    // external
    public SimTask newTask(ACCESS_TYPE access, GLOReference ref, Method method, Object[] params) {
	return newTask(access,((GLOReferenceImpl) ref).objID,method,params);

    }



    /**
     * userAdded
     *
     * @param id
     *            UserID
     */
    public void userLoggedIn(UserID id, Subject subject) {
	try {
	    Method userJoinedMethod = loader.loadClass(
		    "com.sun.gi.logic.SimUserListener").getMethod("userJoined",
			new Class[] { SimTask.class, UserID.class, Subject.class });
	    Object[] params = { id, subject };
	    for (Iterator i = userListeners.iterator(); i.hasNext();) {
		long objID = ((Long) i.next()).longValue();
		queueTask(newTask(objID, userJoinedMethod, params));
	    }
	} catch (ClassNotFoundException ex) {
	    ex.printStackTrace();
	} catch (SecurityException ex) {
	    ex.printStackTrace();
	} catch (NoSuchMethodException ex) {
	    ex.printStackTrace();
	}
    }

    /**
     * userRemoved
     *
     * @param id
     *            UserID
     */
    public void userLoggedOut(UserID id) {
	try {
	    Method userJoinedMethod = loader.loadClass(
		    "com.sun.gi.logic.SimUserListener").getMethod("userLeft",
			new Class[] { SimTask.class, UserID.class });
	    Object[] params = { id };
	    for (Iterator i = userListeners.iterator(); i.hasNext();) {
		long objID = ((Long) i.next()).longValue();
		queueTask(newTask(objID, userJoinedMethod, params));
	    }
	} catch (ClassNotFoundException ex) {
	    ex.printStackTrace();
	} catch (SecurityException ex) {
	    ex.printStackTrace();
	} catch (NoSuchMethodException ex) {
	    ex.printStackTrace();
	}
    }

    /**
     * getAppID
     *
     * @return long
     */
    public long getAppID() {
	return appID;
    }

    /**
     * getAppName
     *
     * @return String
     */
    public String getAppName() {
	return appName;
    }

    /**
     * sendMulticastData
     *
     * @param cid
     * @param targets
     * @param buff
     * @param reliable
     */
    public void sendMulticastData(ChannelID cid, UserID[] targets,
	    ByteBuffer buff, boolean reliable) {
	SGSChannel channel = router.getChannel(cid);
	channel.multicastData(UserID.SERVER_ID, targets, buff, reliable, false);
    }

    public void sendUnicastData(ChannelID cid, UserID target, ByteBuffer buff,
	    boolean reliable) {
	SGSChannel channel = router.getChannel(cid);
	channel.unicastData(UserID.SERVER_ID, target, buff, reliable);
    }

    public void sendBroadcastData(ChannelID cid, UserID target,
	    ByteBuffer buff, boolean reliable) {
	SGSChannel channel = router.getChannel(cid);
	channel.unicastData(UserID.SERVER_ID, target, buff, reliable);
    }

    public void addUserDataListener(UserID id, GLOReference ref) {
	List<Long> dataListeners = userDataListeners.get(id);
	if (dataListeners == null) {
	    dataListeners = new ArrayList<Long>();
	    userDataListeners.put(id, dataListeners);
	}
	dataListeners.add(new Long(((GLOReferenceImpl) ref).objID));
    }

    public void addChannelListener(ChannelID id, GLOReference ref) {
	List<Long> listeners = channelListeners.get(id);
	if (listeners == null) {
	    listeners = new ArrayList<Long>();
	    channelListeners.put(id, listeners);
	}
	listeners.add(new Long(((GLOReferenceImpl) ref).objID));
    }

    public void addChannelDataListener(ChannelID id, GLOReference ref) {
	List<Long> channelDataListenersList = channelDataListeners.get(id);
	if (channelDataListenersList == null) {
	    channelDataListenersList = new ArrayList<Long>();
	    channelDataListeners.put(id, channelDataListenersList);
	}
	channelDataListenersList.add(new Long(((GLOReferenceImpl) ref).objID));
    }

    protected void fireUserLeftChannel(UserID uid, ChannelID cid) {
	List<Long> listenerIDs = userDataListeners.get(uid);
	if (listenerIDs != null) {
	    try {
		Method userLeftChannelMethod = loader.loadClass(
			"com.sun.gi.logic.SimUserDataListener").getMethod(
			    "userLeftChannel",
			    new Class[] { SimTask.class, ChannelID.class,
			    UserID.class });
		Object[] params = { cid, uid };
		for (Long gloID : listenerIDs) {
		    queueTask(newTask(gloID, userLeftChannelMethod, params));
		}
	    } catch (SecurityException e) {

		e.printStackTrace();
	    } catch (NoSuchMethodException e) {

		e.printStackTrace();
	    } catch (ClassNotFoundException e) {

		e.printStackTrace();
	    }
	}

	List<Long> chanListenerIDs = channelListeners.get(cid);
	if (chanListenerIDs != null) {
	    try {
		Method leftChannelMethod = loader.loadClass(
			"com.sun.gi.logic.SimChannelListener").getMethod(
			    "leftChannel",
			    new Class[] { SimTask.class, ChannelID.class,
			    UserID.class });
		Object[] params = { cid, uid };
		for (Long gloID : chanListenerIDs) {
		    queueTask(newTask(gloID, leftChannelMethod, params));
		}
	    } catch (SecurityException e) {

		e.printStackTrace();
	    } catch (NoSuchMethodException e) {

		e.printStackTrace();
	    } catch (ClassNotFoundException e) {

		e.printStackTrace();
	    }
	}

    }

    /**
     * @param uid
     * @param cid
     */
    protected void fireUserJoinedChannel(UserID uid, ChannelID cid) {
	List<Long> listenerIDs = userDataListeners.get(uid);
	if (listenerIDs != null) {
	    try {
		Method userJoinedChannelMethod = loader.loadClass(
			"com.sun.gi.logic.SimUserDataListener").getMethod(
			    "userJoinedChannel",
			    new Class[] { SimTask.class, ChannelID.class,
			    UserID.class });
		Object[] params = { cid, uid };
		for (Long gloID : listenerIDs) {
		    queueTask(newTask(gloID, userJoinedChannelMethod, params));
		}
	    } catch (SecurityException e) {

		e.printStackTrace();
	    } catch (NoSuchMethodException e) {

		e.printStackTrace();
	    } catch (ClassNotFoundException e) {

		e.printStackTrace();
	    }
	}

	List<Long> chanListenerIDs = channelListeners.get(cid);
	if (chanListenerIDs != null) {
	    try {
		Method joinedChannelMethod = loader.loadClass(
			"com.sun.gi.logic.SimChannelListener").getMethod(
			    "joinedChannel",
			    new Class[] { SimTask.class, ChannelID.class,
			    UserID.class });
		Object[] params = { cid, uid };
		for (Long gloID : chanListenerIDs) {
		    queueTask(newTask(gloID, joinedChannelMethod, params));
		}
	    } catch (SecurityException e) {

		e.printStackTrace();
	    } catch (NoSuchMethodException e) {

		e.printStackTrace();
	    } catch (ClassNotFoundException e) {

		e.printStackTrace();
	    }
	}

    }

    protected void fireUserLeft(UserID uid) {

	try {
	    Method userLeftChannelMethod = loader.loadClass(
		    "com.sun.gi.logic.SimUserListener").getMethod("userLeft",
			new Class[] { SimTask.class, UserID.class });
	    Object[] params = { uid };
	    for (Long gloID : userListeners) {
		queueTask(newTask(gloID, userLeftChannelMethod, params));
	    }
	} catch (SecurityException e) {

	    e.printStackTrace();
	} catch (NoSuchMethodException e) {

	    e.printStackTrace();
	} catch (ClassNotFoundException e) {

	    e.printStackTrace();
	}

    }

    protected void fireUserJoined(UserID uid, Subject subject) {

	try {
	    Method userLeftChannelMethod = loader.loadClass(
		    "com.sun.gi.logic.SimUserListener").getMethod("userJoined",
			new Class[] { SimTask.class, UserID.class, Subject.class });
	    Object[] params = { uid, subject };
	    for (Long gloID : userListeners) {
		queueTask(newTask(gloID, userLeftChannelMethod, params));
	    }
	} catch (SecurityException e) {

	    e.printStackTrace();
	} catch (NoSuchMethodException e) {

	    e.printStackTrace();
	} catch (ClassNotFoundException e) {

	    e.printStackTrace();
	}

    }

    protected void fireServerMessage(UserID from, ByteBuffer data) {
	try {
	    Method userJoinedMethod = loader.loadClass(
		    "com.sun.gi.logic.SimUserDataListener")
		.getMethod(
			"userDataReceived",
			new Class[] { SimTask.class, UserID.class,
			ByteBuffer.class });
	    Object[] params = { from, data.duplicate() };
	    List listeners = (List) userDataListeners.get(from);
	    for (Iterator i = listeners.iterator(); i.hasNext();) {
		long objID = ((Long) i.next()).longValue();
		queueTask(newTask(objID, userJoinedMethod, params));
	    }
	} catch (ClassNotFoundException ex) {
	    ex.printStackTrace();
	} catch (SecurityException ex) {
	    ex.printStackTrace();
	} catch (NoSuchMethodException ex) {
	    ex.printStackTrace();
	}
    }

    public SimTask newTask(long objID, Method method, Object[] params) {
	return newTask(ACCESS_TYPE.GET,objID,method,params);
    }

    public boolean hasTasks() {
	// TODO Auto-generated method stub
	synchronized (taskQueue) {
	    return !taskQueue.isEmpty();
	}
    }

    public SimTask nextTask() {
	synchronized (taskQueue) {
	    return taskQueue.remove(0);
	}
    }

    public ChannelID openChannel(String name) {
	return router.openChannel(name).channelID();
    }

    public long registerTimerEvent(long tid, ACCESS_TYPE access, long objID, long delay, boolean repeat) {
	return kernel.registerTimerEvent(tid, access, this,objID,delay,repeat);
    }

    public SimTask newTask(GLOReference ref, Method methodToCall, Object[] params) {
	return newTask(ACCESS_TYPE.GET,ref,methodToCall,params);
    }

    public ObjectStore getObjectStore() {
	return ostore;
    }

    // Hooks into the RawSocketManager, added 1/16/2006

    /**
     * (Copied from Simulation)
     *
     * Requests that a socket be opened at the given host on the given port.
     * The returned ID can be used for future communication with the socket that will
     * be opened.  The socket ID will not be valid, and therefore should not be used
     * until the connection is complete.  Connection is complete once the
     * SimRawSocketListener.socketOpened() call back is called.
     *
     * @param socketID			the socket identifier.
     * @param access			the access type (GET, PEEK, or ATTEMPT)
     * @param objID				a reference to the GLO initiating the connection.
     * @param host				a String representation of the remote host.
     * @param port				the remote port.
     * @param reliable			if true, the connection will use a reliable protocol.
     *
     * @return an identifier that can be used for future communication with the socket.
     */
    public long openSocket(long socketID, ACCESS_TYPE access, long objID, String host,
	    int port, boolean reliable) {

	return kernel.openSocket(socketID,this, access, objID, host, port, reliable);
    }

    /**
     *  (Copied from Simulation)
     *
     * Sends data on the socket mapped to the given socketID.  This method
     * will not return until the entire buffer has been drained.
     *
     * @param socketID			the socket identifier.
     * @param data				the data to send.  The buffer should be in a ready
     * 							state, i.e. flipped if necessary.
     *
     * @return the number of bytes sent.
     */
    public long sendRawSocketData(long socketID, ByteBuffer data) {
	return kernel.sendRawSocketData(socketID, data);
    }

    /**
     *  (Copied from Simulation)
     *
     * Requests that the socket matching the given socketID be closed.
     * The socket should not be assumed to be closed, however, until the
     * call back SimRawSocketListener.socketClosed() is called.
     *
     * @param socketID		the identifier of the socket.
     */
    public void closeSocket(long socketID) {
	kernel.closeSocket(socketID);
    }

    /* (non-Javadoc)
     * @see com.sun.gi.logic.Simulation#getNextTimerID()
     */
    public long getNextTimerID() {
	return kernel.getNextTimerID();
    }

    /* (non-Javadoc)
     * @see com.sun.gi.logic.Simulation#getNextSocketID()
     */
    public long getNextSocketID() {
	return kernel.getNextSocketID();
    }

    /**
     * Joins the specified user to the Channel referenced by the
     * given ChannelID.
     *
     * @param user				the user
     * @param id				the ChannelID
     */
    public void join(UserID user, ChannelID id) {
	router.join(user, id);
    }

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     *
     * @param user				the user
     * @param id				the ChannelID
     */
    public void leave(UserID user, ChannelID id) {
	router.leave(user, id);
    }

    /**
     * Locks the given channel based on shouldLock.  Users cannot join/leave locked channels
     * except by way of the Router.
     *
     * @param cid				the channel ID
     * @param shouldLock		if true, will lock the channel, otherwise unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock) {
	router.lock(cid, shouldLock);
    }

    /**
     * Closes the local view of the channel mapped to ChannelID.
     * Any remaining users will be notified as the channel is closing.
     *
     * @param id		the ID of the channel to close.
     */
    public void closeChannel(ChannelID id) {
	router.closeChannel(id);
    }

}
