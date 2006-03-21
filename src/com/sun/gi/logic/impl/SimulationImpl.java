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

package com.sun.gi.logic.impl;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Random;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

public class SimulationImpl implements Simulation {

    private static Logger log = Logger.getLogger("com.sun.gi.logic");

    long appID;
    private String appName;

    SimKernel kernel;
    Router router;
    ObjectStore ostore;
    ClassLoader loader;

    private List<Long> userListeners = new ArrayList<Long>();
    private Map<UserID, List<Long>> userDataListeners =
	    new HashMap<UserID, List<Long>>();
    // private List<SimTask> taskQueue = new LinkedList<SimTask>();
    private Map<UserID, Set<ChannelID>> userEvesdropChannels =
	    new HashMap<UserID, Set<ChannelID>>();


    // DJE:  A map of the state of each user.  Every user is always in
    // one of the two sets:  readyUsers or busyUsers.  A user is ready
    // if there is not already an in-progress task for that user (it's
    // not whether the USER is busy; it's whether the system is busy
    // doing something for the user).  A user may also be a lame duck,
    // which means that the "leave" task has been queued but not
    // processed.  Transitions from one set are always protected by
    // userStateMutex.

    protected Object userStateMutex = new Object();
    protected Set<UserID> readyUsers =
	    Collections.synchronizedSet(new HashSet<UserID>());
    protected Set<UserID> busyUsers =
	    Collections.synchronizedSet(new HashSet<UserID>());
    protected Set<UserID> lameDuckUsers =
	    Collections.synchronizedSet(new HashSet<UserID>());

    // DJE:  A list of all of the known active UserIDs.  At every
    // visible moment, should be the union of readyUsers and
    // busyUsers.  Used for bookkeeping; may be redundant eventually. 
    // Protected by userStateMutex as well.

    protected Set<UserID> knownUsers =
	    Collections.synchronizedSet(new HashSet<UserID>());

    // DJE: A map between UserIDs and Queues of pending tasks 

    protected Map<UserID, List<SimTask>> taskQueues =
	    Collections.synchronizedMap(
		    new HashMap<UserID, List<SimTask>>());
    protected List<SimTask> backdoorQueue =
	    Collections.synchronizedList(new LinkedList<SimTask>());

    // DJE: source of randomness for selecting task queues
    private Random random = new Random();

    /**
     * Constructor
     */
    public SimulationImpl(SimKernel kernel, ObjectStore ostore, Router router,
            DeploymentRec game) throws InstantiationException {

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
		log.fine("user " + uid + " joining");

		// DJE create local state for the user.
		synchronized (userStateMutex) {
		    if (!knownUsers.contains(uid)) {
			knownUsers.add(uid);

			// If we didn't even know about this user,
			// then we must not be running anything for
			// them (so they're ready) and we must not
			// have a taskQueue for them.

			readyUsers.add(uid);
			if (!taskQueues.containsKey(uid)) {
			    taskQueues.put(uid,
				    Collections.synchronizedList(
					    new LinkedList<SimTask>()));
			}
		    } else {
			log.warning("user " + uid + " was already joined.");
		    }
		}
                fireUserJoined(uid, subject);
            }

            public void userLeft(UserID uid) {
		log.fine("user " + uid + " leaving");

		fireUserLeft(uid);

		// DJE note the assymetry wrt userJoin:  we need to
		// queue up the "leave" message BEFORE we can declare
		// the user a lame duck.

		synchronized (userStateMutex) {

		    // DJE mark this user as a lame duck.  We can
		    // remove their queues and other bookkeeping if
		    // their queue drains while we still think they're
		    // dead wood.

		    if (knownUsers.contains(uid)) {
			log.fine("user left: " + uid);
			lameDuckUsers.add(uid);
		    } else {
			log.info("user " + uid +
				" leaving but was not joined.");
		    }
		}
	    }

            public void userJoinedChannel(UserID uid, ChannelID cid) {
		log.fine("user " + uid + " joined channel " + cid);
		synchronized (userStateMutex) {
		    if (lameDuckUsers.contains(uid)) {
			log.info("joinedChannel user " + uid +
				" is a lame duck");
		    }
		}
                fireUserJoinedChannel(uid, cid);
            }

            public void userLeftChannel(UserID uid, ChannelID cid) {
		log.fine("user " + uid + " left channel " + cid);
		synchronized (userStateMutex) {
		    if (lameDuckUsers.contains(uid)) {
			log.info("leftChannel user " + uid +
				" is a lame duck");
		    }
		}
                fireUserLeftChannel(uid, cid);
            }

            public void channelDataPacket(ChannelID cid, UserID from,
                    ByteBuffer buff) {
		log.fine("user " + from + " sent data to channel " + cid);
		synchronized (userStateMutex) {
		    if (lameDuckUsers.contains(from)) {
			log.info("channelDataPacket user " + from +
				" is a lame duck");
		    }
		}
                fireChannelDataPacket(cid, from, buff);
            }
        });

        final String bootClassName = game.getBootClass();
        if (bootClassName != null) { // has server side
            try {
                loader = new URLClassLoader(new URL[] { new URL(
                        game.getClasspathURL()) });

                Class bootclass = loader.loadClass(bootClassName);

                Method startMethod = bootclass.getMethod("boot",
			new Class[] { GLOReference.class, boolean.class });

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
                    if (bootObjectID == ObjectStore.INVALID_ID) {
                        // we lost a create race
                        bootObjectID = trans.lookup("BOOT");
                    } else {
                        firstTime = true;
                    }
                }
                trans.commit();

		log.fine("BootObj for app " + appID +
			" is objectID " + bootObjectID);

                queueTask(newTask(bootObjectID, startMethod, new Object[] {
                        new GLOReferenceImpl(bootObjectID), firstTime}, 
				null));

            } catch (MalformedURLException e) {
                e.printStackTrace();
                throw new InstantiationException(e.getMessage());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new InstantiationException(e.getMessage());
            } catch (NoSuchMethodException e) {
                throw new InstantiationException(
                        "Boot class in sim has no method: "
                                + "void boot(SimTask, boolean)");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new InstantiationException(e.getMessage());
            }
        }
        kernel.addSimulation(this);
    }

    /**
     */
    public void queueTask(SimTask task) {
	if (task.getUserID() == null) {
	    log.finer("backdoor task queued");
	    backdoorQueue.add(task);
	} else {
	    UserID uid = task.getUserID();

	    if (!knownUsers.contains(uid)) {
		log.warning("attempt to queue task for unknown uid " + uid);
		// GIVE UP
		return ;
	    }
	    synchronized (taskQueues) {
		// add the task to the front of the queue for this uid.
		if (!taskQueues.containsKey(uid)) {
		    log.severe("attempt to queue task for unknown uid " + uid);
		    // GIVE UP
		    return ;
		}
		List<SimTask> uidQueue = taskQueues.get(uid);
		uidQueue.add(task);

		// These will be interesting, at least for debugging.
		log.fine("queue for uid " + uid + " has length " +
			uidQueue.size());
	    }
	}

	kernel.simHasNewTask();
    }


    /**
     * DJE this is entirely new.
     *
     * The impl here should be folded back into queueTask, since the
     * primary difference is whether the task is put on the head of
     * the queue or the tail.
     */
    public void requeueTask(SimTask task) {
	if (task.getUserID() == null) {
	    log.finer("backdoor task re-queued");
	    backdoorQueue.add(0, task);
	} else {
	    UserID uid = task.getUserID();

	    if (!knownUsers.contains(uid)) {
		log.warning("attempt to queue task for unknown uid " + uid);
		// GIVE UP
		return ;
	    }
	    synchronized (taskQueues) {
		// add the task to the front of the queue for this uid.
		if (!taskQueues.containsKey(uid)) {
		    log.severe("attempt to queue task for unknown uid " + uid);
		    // GIVE UP
		    return ;
		}
		List<SimTask> uidQueue = taskQueues.get(uid);
		uidQueue.add(0, task);

		// These will be interesting, at least for debugging.
		log.fine("queue for uid " + uid + " has length " +
			uidQueue.size());
	    }
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
        addUserListener(((GLOReferenceImpl) ref).getObjID());
    }

    public void addUserListener(long objID) {
        userListeners.add(objID);
    }

    // internal
    private SimTask newTask(ACCESS_TYPE access, long startObject,
            Method startMethod, Object[] params, UserID uid)
    {
        return new SimTaskImpl(this, loader, access, startObject, startMethod,
                params, uid);
    }

    // external
    public SimTask newTask(ACCESS_TYPE access, GLOReference ref, Method method,
            Object[] params, UserID uid)
    {
        return newTask(access, ((GLOReferenceImpl) ref).getObjID(),
		method, params, uid);
    }

    /**
     * userAdded
     * 
     * @param id UserID
     */
    public void userLoggedIn(UserID id, Subject subject) {
        try {
            Method userJoinedMethod = loader.loadClass(
                    "com.sun.gi.logic.SimUserListener").getMethod("userJoined",
                    new Class[] { SimTask.class, UserID.class, Subject.class });
            Object[] params = { id, subject };
            for (Iterator i = userListeners.iterator(); i.hasNext();) {
                long objID = ((Long) i.next()).longValue();
                queueTask(newTask(objID, userJoinedMethod, params, id));
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
     * @param id UserID
     */
    public void userLoggedOut(UserID id) {
        try {
            Method userJoinedMethod = loader.loadClass(
                    "com.sun.gi.logic.SimUserListener").getMethod("userLeft",
                    new Class[] { SimTask.class, UserID.class });
            Object[] params = { id };
            for (Iterator i = userListeners.iterator(); i.hasNext();) {
                long objID = ((Long) i.next()).longValue();
                queueTask(newTask(objID, userJoinedMethod, params, id));
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

    public void sendUnicastData(ChannelID cid, UserID target, ByteBuffer buff,
            boolean reliable) {
        SGSChannel channel = router.getChannel(cid);
        channel.unicastData(UserID.SERVER_ID, target, buff, reliable);
    }

    public void sendMulticastData(ChannelID cid, UserID[] targets,
            ByteBuffer buff, boolean reliable) {
        SGSChannel channel = router.getChannel(cid);
        channel.multicastData(UserID.SERVER_ID, targets, buff, reliable, false);
    }

    public void sendBroadcastData(ChannelID cid, ByteBuffer buff,
            boolean reliable) {
        SGSChannel channel = router.getChannel(cid);
        channel.broadcastData(UserID.SERVER_ID, buff, reliable);
    }

    public void addUserDataListener(UserID id, GLOReference ref) {
        List<Long> dataListeners = userDataListeners.get(id);
        if (dataListeners == null) {
            dataListeners = new ArrayList<Long>();
            userDataListeners.put(id, dataListeners);
        }
        dataListeners.add(new Long(((GLOReferenceImpl) ref).getObjID()));
    }

    protected void fireUserLeftChannel(UserID uid, ChannelID cid) {
        List<Long> listenerIDs = userDataListeners.get(uid);
        if (listenerIDs != null) {
            try {
                Method userLeftChannelMethod = loader.loadClass(
                        "com.sun.gi.logic.SimUserDataListener").getMethod(
                        "userLeftChannel",
                        new Class[] { ChannelID.class, UserID.class });
                Object[] params = { cid, uid };
                for (Long gloID : listenerIDs) {
                    queueTask(newTask(gloID, userLeftChannelMethod, params, uid));
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
                        new Class[] { ChannelID.class, UserID.class });
                Object[] params = { cid, uid };
                for (Long gloID : listenerIDs) {
                    queueTask(newTask(gloID, userJoinedChannelMethod, params, uid));
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

    protected void fireChannelDataPacket(ChannelID cid, UserID from,
            ByteBuffer buff) {
        List<Long> listeners = userDataListeners.get(from);
        if (listeners != null) {
            Set<ChannelID> channels = userEvesdropChannels.get(from);
            if ((channels == null) || !channels.contains(cid)) {
                // we aren't evesdropping
                return;
            }
            ByteBuffer outBuff = buff.duplicate();
            outBuff.flip();
            Method m;
            try {
                m = SimUserDataListener.class.getMethod(
                        "dataArrivedFromChannel", ChannelID.class,
                        UserID.class, ByteBuffer.class);
                for (Long uid : listeners) {

		    // DJE:  this looks a little different from the
		    // others.  Review.

                    queueTask(newTask(uid.longValue(), m, new Object[] { cid,
                            from, outBuff.duplicate() }, from));
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    protected void fireUserLeft(UserID uid) {
        try {
            Method userLeftMethod = loader.loadClass(
                    "com.sun.gi.logic.SimUserListener").getMethod("userLeft",
                    new Class[] { UserID.class });
            Object[] params = { uid };
            for (Long gloID : userListeners) {
                queueTask(newTask(gloID, userLeftMethod, params, uid));
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
            Method userJoinedMethod = loader.loadClass(
                    "com.sun.gi.logic.SimUserListener").getMethod("userJoined",
                    new Class[] { UserID.class, Subject.class });
            Object[] params = { uid, subject };
            for (Long gloID : userListeners) {
                queueTask(newTask(gloID, userJoinedMethod, params, uid));
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
                    "com.sun.gi.logic.SimUserDataListener").getMethod(
                    "userDataReceived",
                    new Class[] { UserID.class, ByteBuffer.class });
            Object[] params = { from, data.duplicate() };
            List<Long> listeners = userDataListeners.get(from);
            if (listeners != null) {
                for (long objID : listeners) {
                    queueTask(newTask(objID, userJoinedMethod, params, from));
                }
            }
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        } catch (SecurityException ex) {
            ex.printStackTrace();
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace();
        }
    }

    public SimTask newTask(long objID, Method method, Object[] params,
	    UserID uid)
    {
        return newTask(ACCESS_TYPE.GET, objID, method, params, uid);
    }

    public boolean hasTasks() {
	synchronized (userStateMutex) {
	    synchronized (taskQueues) {
		if (!backdoorQueue.isEmpty()) {
		    log.fine("has Tasks: backdoor tasks");
		    return true;
		}
		for (UserID uid : readyUsers) {
		    // log.finer("has Tasks: considering user " + uid);
		    if (!taskQueues.get(uid).isEmpty()) {
			log.finer("has Tasks: user " + uid);
			return true;
		    }
		}
	    }
	}
	log.fine("has Tasks: no waiting tasks");
	return false;
    }

    public SimTask nextTask() {

	log.fine("next Task: choosing something");
	SimTask task = null;

	// DJE:  CHECK:  I'm assuming that the caller knows that if
	// this returns null, then there was a snafu, but to do
	// nothing.  Even if hasTasks indicates that there are tasks
	// ready to run, there is a race condition between that check
	// and actually choosing a candidate.  The only way to really
	// check whether there is something to run is find something
	// or fail.

	synchronized (userStateMutex) {
	    synchronized (taskQueues) {

		// DJE:  Rather than doing something clever, I'm doing
		// something simple, and trusting to randomness to even
		// things out.  This is unfair to users with lots of tasks
		// scheduled; they're no more likely to run than anyone
		// else.

		if (backdoorQueue.isEmpty() && readyUsers.isEmpty()) {
		    log.warning("nothing to do: problem!");
		    return null;
		}

		// DJE: If there's something on the backdoor queue, then
		// flip a coin over whether or not to take it.
		// Otherwise, walk through the readUserList looking
		// for something to take.    If that fails, then go
		// back to the backdoor queue and take what's there.
		// If that fails, there's nothing to do!

		if (!backdoorQueue.isEmpty() && random.nextBoolean()) {
		    task = backdoorQueue.remove(0);
		    log.finer("took newTask from backdoorQueue");
		} else if (!readyUsers.isEmpty()) {
		    List<UserID> readyUserList =
			    new ArrayList<UserID>(readyUsers);
		    Collections.shuffle(readyUserList);

		    for (UserID uid : readyUserList) {
			List<SimTask> queue =
				taskQueues.get(uid);
			if (queue != null && !queue.isEmpty()) {
			    task = queue.remove(0);
			    log.finer("took newTask from readyUser " + uid);
			    break;
			}
		    }
		} else if (!backdoorQueue.isEmpty()) {
		    log.finer("took newTask from backdoor on the rebound");
		    task = backdoorQueue.remove(0);
		} else {
		    task = null;
		}

		if (task == null) {
		    log.fine("newTask returning NULL");
		    return null;
		} else {
		    userIsReady(task.getUserID(), false);
		    return task;
		}
	    }
	}
    }

    private void userIsReady(UserID user, boolean ready) {

	log.fine("user " + user + " readiness " + ready);

	if (user == null) {
	    return;
	}

	synchronized (userStateMutex) {
	    if (ready) {
		readyUsers.add(user);
		busyUsers.remove(user);

		if (lameDuckUsers.contains(user)) {
		    log.finer("lame duck user " + user);
		    synchronized (taskQueues) {
			List<SimTask> tasks = taskQueues.get(user);
			if ((tasks == null) || tasks.isEmpty()) {
			    log.fine("removing lame duck user " + user);
			    taskQueues.remove(user);
			    readyUsers.remove(user);
			    busyUsers.remove(user);
			    lameDuckUsers.remove(user);
			} else {
			    log.finer("preserving lame duck user " + user +
				    " length " + tasks.size());
			}
		    }
		}
	    } else {
		readyUsers.remove(user);
		busyUsers.add(user);
	    }

	    log.finer("ready " + ready +
		    " #taskQueues " + taskQueues.size() +
		    " #readyUsers " + readyUsers.size() +
		    " #busyUsers " + busyUsers.size() + 
		    " #lameDuckUsers " + lameDuckUsers.size());
	}

	kernel.simHasNewTask();
    }

    /**
     * {@inheritDoc}
     */
    public void taskDone(SimTask task) {
	if (task == null) {
	    // unexpected!

	    log.severe("task is null");
	    return;
	}
	userIsReady(task.getUserID(), true);
    }

    public ChannelID openChannel(String name) {
        return router.openChannel(name).channelID();
    }

    public long registerTimerEvent(long tid, ACCESS_TYPE access, long objID,
            long delay, boolean repeat) {
        return kernel.registerTimerEvent(tid, access, this, objID, delay,
                repeat);
    }

    public SimTask newTask(GLOReference ref, Method methodToCall,
            Object[] params, UserID uid)
    {
        return newTask(ACCESS_TYPE.GET, ref, methodToCall, params, uid);
    }

    public ObjectStore getObjectStore() {
        return ostore;
    }

    // Hooks into the RawSocketManager, added 1/16/2006

    public long openSocket(long socketID, ACCESS_TYPE access, long objID,
            String host, int port, boolean reliable)
    {
        return kernel.openSocket(socketID, this, access, objID, host, port,
                reliable);
    }

    public long sendRawSocketData(long socketID, ByteBuffer data) {
        return kernel.sendRawSocketData(socketID, data);
    }

    public void closeSocket(long socketID) {
        kernel.closeSocket(socketID);
    }

    public long getNextTimerID() {
        return kernel.getNextTimerID();
    }

    public long getNextSocketID() {
        return kernel.getNextSocketID();
    }

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     * 
     * @param user the user
     * @param id the ChannelID
     */
    public void join(UserID user, ChannelID id) {
        router.join(user, id);
    }

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param user the user
     * @param id the ChannelID
     */
    public void leave(UserID user, ChannelID id) {
        router.leave(user, id);
    }

    /**
     * Locks the given channel based on shouldLock. Users cannot
     * join/leave locked channels except by way of the Router.
     * 
     * @param cid the channel ID
     * @param shouldLock if true, will lock the channel, otherwise
     * unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock) {
        router.lock(cid, shouldLock);
    }

    /**
     * Closes the local view of the channel mapped to ChannelID. Any
     * remaining users will be notified as the channel is closing.
     * 
     * @param id the ID of the channel to close.
     */
    public void closeChannel(ChannelID id) {
        router.closeChannel(id);
    }

    public void enableEvesdropping(UserID uid, ChannelID cid, boolean setting) {
        Set<ChannelID> channels;
        synchronized (userEvesdropChannels) {
            channels = userEvesdropChannels.get(uid);
            if (channels == null) {
                channels = new HashSet<ChannelID>();
                userEvesdropChannels.put(uid, channels);
            }
        }
        synchronized (channels) {
            if (setting) {
                channels.add(cid);
            } else {
                channels.remove(cid);
            }
        }
    }
}
