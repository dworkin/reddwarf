package com.sun.gi.logic.impl;

import java.io.*;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.util.*;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.*;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.logic.*;
import com.sun.gi.objectstore.*;
import com.sun.multicast.util.*;

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

	ClassLoader loader;

	private List<Long> userListeners = new ArrayList<Long>();

	private String appName;

	private Map<UserID, List<Long>> userDataListeners = new HashMap<UserID, List<Long>>();

	private Map<ChannelID, List<Long>> channelListeners = new HashMap<ChannelID, List<Long>>();

	private List<SimTask> taskQueue = new LinkedList<SimTask>();

	public SimulationImpl(SimKernel kernel, Router router, DeploymentRec game)
			throws InstantiationException {
		this.kernel = kernel;
		this.appName = game.getName();
		this.router = router;
		try {
			loader = new URLClassLoader(new URL[] { new URL(game
					.getClasspathURL()) });
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new InstantiationException(e.getMessage());
		}
		Class bootclass = null;
		try {
			bootclass = loader.loadClass(game.getBootClass());
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
		this.appID = game.getID();
		// check for boot object. it it doesnt exist, then create it
		Transaction trans = kernel.getOstore().newTransaction(appID,
				bootclass.getClassLoader());
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
		loader = bootclass.getClassLoader();
		queueTask(newTask(bootObjectID, startMethod, new Object[] { firstTime }));
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
	private SimTask newTask(long startObject, Method startMethod,
			Object[] params) {
		return new SimTaskImpl(this, loader, startObject, startMethod, params);
	}

	// external
	public SimTask newTask(GLOReference ref, String methodName, Object[] params) {
		throw new UnimplementedOperationException("not implemented yet");

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
		List dataListeners = (List) userDataListeners.get(id);
		if (dataListeners == null) {
			dataListeners = new ArrayList();
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
			channelListenersList = new ArrayList();
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

}
