package com.sun.gi.logic.impl;

import java.io.Serializable;
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
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimChannelListener;
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

	private List<SimTask> taskQueue = new LinkedList<SimTask>();

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
		String bootClassName = game.getBootClass();
		if (bootClassName != null){ // has server side
			try {
				loader = new URLClassLoader(new URL[] { new URL(game
					.getClasspathURL()) });
			} catch (MalformedURLException e) {
				e.printStackTrace();
				throw new InstantiationException(e.getMessage());
			}
			Class bootclass = null;
			try {
				bootclass = loader.loadClass(bootClassName);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				throw new InstantiationException(e.getMessage());
			}
			Method startMethod = null;
			try {
				startMethod = bootclass.getMethod("boot", new Class[] {
					SimTask.class, boolean.class });
			} catch (NoSuchMethodException ex) {
				throw new InstantiationException(
					"Boot class in sim has no method: void boot(SimTask)");
			}
			
			// check for boot object. it it doesnt exist, then create it
			Transaction trans = ostore.newTransaction(bootclass.getClassLoader());
			trans.start();
			boolean firstTime = false;
			long bootObjectID = trans.lookup("BOOT");
			if (bootObjectID == ObjectStore.INVALID_ID) { // doesnt exist
				try {
					bootObjectID = trans.create((Serializable) bootclass
						.newInstance(), "BOOT");
					firstTime = true;
				} catch (IllegalAccessException ex1) {
					ex1.printStackTrace();
					throw new InstantiationException(ex1.getMessage());
				}
			}
			trans.commit();
			queueTask(newTask(bootObjectID, startMethod, new Object[] { firstTime }));
		}				
		kernel.addSimulation(this);
	}

	/**
	 * @param cid
	 * @param from
	 * @param buff
	 */
	protected void fireChannelDataPacket(ChannelID cid, UserID from,
			ByteBuffer buff) {
		List<Long> listeners = channelListeners.get(cid);
		if (listeners != null) {
			Method m;
			try {
				m = SimChannelListener.class.getMethod("dataArrived",
						SimTask.class, ChannelID.class, UserID.class,
						ByteBuffer.class);
				for (Long uid : listeners) {
					queueTask(newTask(uid.longValue(), m, new Object[] { cid,
							from, buff }));
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

	/**
	 * sendUnicastData
	 * 
	 * @param cid
	 * @param target
	 * @param buff
	 * @param reliable
	 */
	public void sendUnicastData(ChannelID cid, UserID target, ByteBuffer buff,
			boolean reliable) {
		SGSChannel channel = router.getChannel(cid);
		channel.unicastData(UserID.SERVER_ID, target, buff, reliable);
	}

	/**
	 * sendBroadcastData
	 * 
	 * @param cid
	 * @param target
	 * @param buff
	 * @param reliable
	 */

	public void sendBroadcastData(ChannelID cid, UserID target,
			ByteBuffer buff, boolean reliable) {
		SGSChannel channel = router.getChannel(cid);
		channel.unicastData(UserID.SERVER_ID, target, buff, reliable);
	}

	/**
	 * addUserDataListener
	 * 
	 * @param id
	 *            UserID
	 * @param ref
	 *            SOReference
	 */
	public void addUserDataListener(UserID id, GLOReference ref) {
		List<Long> dataListeners = userDataListeners.get(id);
		if (dataListeners == null) {
			dataListeners = new ArrayList<Long>();
			userDataListeners.put(id, dataListeners);
		}
		dataListeners.add(new Long(((GLOReferenceImpl) ref).objID));
	}

	/**
	 * addUserDataListener
	 * 
	 * @param id
	 *            UserID
	 * @param ref
	 *            SOReference
	 */
	public void addChannelListener(ChannelID id, GLOReference ref) {
		List<Long> channelListenersList = channelListeners.get(id);
		if (channelListenersList == null) {
			channelListenersList = new ArrayList<Long>();
			channelListeners.put(id, channelListenersList);
		}
		channelListenersList.add(new Long(((GLOReferenceImpl) ref).objID));
	}

	/**
	 * @param uid
	 * @param cid
	 */
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

	}

	/**
	 * @param uid
	 * @param cid
	 */
	protected void fireUserJoinedChannel(UserID uid, ChannelID cid) {
		List<Long> listenerIDs = userDataListeners.get(uid);
		if (listenerIDs != null) {
			try {
				Method userLeftChannelMethod = loader.loadClass(
						"com.sun.gi.logic.SimUserDataListener").getMethod(
						"userJoinedChannel",
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

	}

	/**
	 * @param uid
	 */
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

	/**
	 * @param uid
	 * @param subject
	 */
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

	/**
	 * @param objID
	 * @param userJoinedMethod
	 * @param params
	 * @return
	 */
	public SimTask newTask(long objID, Method method, Object[] params) {
		return newTask(ACCESS_TYPE.GET,objID,method,params);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.Simulation#hasTasks()
	 */
	public boolean hasTasks() {
		// TODO Auto-generated method stub
		synchronized (taskQueue) {
			return !taskQueue.isEmpty();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.Simulation#nextTask()
	 */
	public SimTask nextTask() {
		synchronized (taskQueue) {
			return taskQueue.remove(0);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.Simulation#openChannel(java.lang.String)
	 */
	public ChannelID openChannel(String name) {
		return router.openChannel(name).channelID();
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.Simulation#registerTimerEvent(com.sun.gi.logic.GLOReference, long, boolean)
	 */
	public long registerTimerEvent(ACCESS_TYPE access, GLOReference ref, long delay, boolean repeat) {
		return kernel.registerTimerEvent(access, this,ref,delay,repeat);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.Simulation#newTask(com.sun.gi.logic.Simulation.AccessType, com.sun.gi.logic.GLOReference, java.lang.reflect.Method, java.lang.Object[])
	 */
	public SimTask newTask(GLOReference ref, Method methodToCall, Object[] params) {		
		return newTask(ACCESS_TYPE.GET,ref,methodToCall,params);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.Simulation#getObjectStore()
	 */
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
	 * @param access			the access type (GET, PEEK, or ATTEMPT)
	 * @param ref				a reference to the GLO initiating the connection.
	 * @param host				a String representation of the remote host.
	 * @param port				the remote port.
	 * @param reliable			if true, the connection will use a reliable protocol.
	 * 
	 * @return an identifier that can be used for future communication with the socket.
	 */
	public long openSocket(ACCESS_TYPE access, GLOReference ref, String host, 
			int port, boolean reliable) {
		
		return kernel.openSocket(this, access, ref, host, port, reliable);
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

}
