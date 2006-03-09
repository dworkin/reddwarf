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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
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

    long appID;
    private String appName;

    SimKernel kernel;
    Router router;
    ObjectStore ostore;
    ClassLoader loader;

    private List<Long> userListeners = new ArrayList<Long>();
    private Map<UserID, List<Long>> userDataListeners = new HashMap<UserID, List<Long>>();
    private List<SimTask> taskQueue = new LinkedList<SimTask>();
    private Map<UserID, Set<ChannelID>> userEvesdropChannels = new HashMap<UserID, Set<ChannelID>>();

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
                loader = new URLClassLoader(new URL[] { new URL(
                        game.getClasspathURL()) });

                Class bootclass = loader.loadClass(bootClassName);

                Method startMethod = bootclass.getMethod("boot", new Class[] {
                        GLOReference.class, boolean.class });

                // Check for existing boot object in objectstore...

                Transaction trans = ostore.newTransaction(bootclass.getClassLoader());

                trans.start();
                boolean firstTime = false;
                long bootObjectID = trans.lookup("BOOT");
                if (bootObjectID == ObjectStore.INVALID_ID) {
                    // boot object doesn't exist; create it
                    bootObjectID = trans.create((GLO) bootclass.newInstance(),
                            "BOOT");
                    if (bootObjectID == ObjectStore.INVALID_ID) {
                        // we lost a create race
                        bootObjectID = trans.lookup("BOOT");
                    } else {
                        firstTime = true;
                    }
                }
                trans.commit();
                queueTask(newTask(bootObjectID, startMethod, new Object[] {
                        new GLOReferenceImpl(bootObjectID), firstTime }));

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
        addUserListener(((GLOReferenceImpl) ref).objID);
    }

    public void addUserListener(long objID) {
        userListeners.add(objID);
    }

    // internal
    private SimTask newTask(ACCESS_TYPE access, long startObject,
            Method startMethod, Object[] params)
    {
        return new SimTaskImpl(this, loader, access, startObject, startMethod,
                params);
    }

    // external
    public SimTask newTask(ACCESS_TYPE access, GLOReference ref, Method method,
            Object[] params)
    {
        return newTask(access, ((GLOReferenceImpl) ref).objID, method, params);
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
        dataListeners.add(new Long(((GLOReferenceImpl) ref).objID));
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
                Method userJoinedChannelMethod = loader.loadClass(
                        "com.sun.gi.logic.SimUserDataListener").getMethod(
                        "userJoinedChannel",
                        new Class[] { ChannelID.class, UserID.class });
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

    protected void fireUserLeft(UserID uid) {
        try {
            Method userLeftMethod = loader.loadClass(
                    "com.sun.gi.logic.SimUserListener").getMethod("userLeft",
                    new Class[] { UserID.class });
            Object[] params = { uid };
            for (Long gloID : userListeners) {
                queueTask(newTask(gloID, userLeftMethod, params));
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
                queueTask(newTask(gloID, userJoinedMethod, params));
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
                    queueTask(newTask(objID, userJoinedMethod, params));
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

    public SimTask newTask(long objID, Method method, Object[] params) {
        return newTask(ACCESS_TYPE.GET, objID, method, params);
    }

    public boolean hasTasks() {
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

    public long registerTimerEvent(long tid, ACCESS_TYPE access, long objID,
            long delay, boolean repeat) {
        return kernel.registerTimerEvent(tid, access, this, objID, delay,
                repeat);
    }

    public SimTask newTask(GLOReference ref, Method methodToCall,
            Object[] params)
    {
        return newTask(ACCESS_TYPE.GET, ref, methodToCall, params);
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
