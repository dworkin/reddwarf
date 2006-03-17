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

package com.sun.gi.framework.rawsocket.impl;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.gi.framework.rawsocket.RawSocketManager;
import com.sun.gi.framework.rawsocket.SimRawSocketListener;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.impl.GLOReferenceImpl;

/**
 * <p>
 * Title: RawSocketManagerImpl
 * </p>
 * 
 * <p>
 * Description: A concrete implementation of
 * <code>RawSocketManager</code>. It listens for incoming data and
 * processes socket connections and closures on a separate thread. All
 * of the public methods are thread safe.
 * </p>
 * 
 * <p>
 * For reliable transport (TCP), the buffer size is set to 1KB. For
 * unreliable transport (UDP), the buffer size is set at the maximum
 * size for a datagram packet: 64KB minus header info.
 * </p>
 * 
 * <p>
 * NOTE: Data is sent on the caller's thread for ease of implementation
 * and to keep the thread count low. This will work fine for small
 * messages, but if typical use will have larger payloads, then the data
 * should be sent on a different thread.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 * 
 */
public class RawSocketManagerImpl implements RawSocketManager {

    private final static int RELIABLE_BUFFER_SIZE = 1024; // 1k
    
    // max udp size = 64k - header
    private final static int UNRELIABLE_BUFFER_SIZE = 65507;

    private AtomicLong currentSocketID;
    private ConcurrentHashMap<Long, SocketInfo> socketMap;

    // list of socket IDs to be opened.
    private ArrayList<Long> pendingConnections;

    // list of sockets IDs to be closed.
    private ArrayList<Long> pendingClosures;
    
    private Selector selector;

    boolean shouldUpdate = true;

    /**
     * Constructs a new <code>RawSocketManager</code> and starts it
     * running.
     */
    public RawSocketManagerImpl() {
        currentSocketID = new AtomicLong(0L);
        socketMap = new ConcurrentHashMap<Long, SocketInfo>();
        pendingConnections = new ArrayList<Long>();
        pendingClosures = new ArrayList<Long>();
        start();
    }

    // implemented methods from RawSocketManager

    /**
     * Queues a socket to be opened at the given host on the given port.
     */
    public long openSocket(long id, Simulation sim, ACCESS_TYPE access,
            long startObjectID, String host, int port, boolean reliable) {

        try {
            AbstractSelectableChannel channel = reliable ? SocketChannel.open()
                    : DatagramChannel.open();
            channel.configureBlocking(false);
            storeChannel(id, sim, access, startObjectID, (ByteChannel) channel);

            InetSocketAddress address = new InetSocketAddress(host, port);

            // Even though these classes share the same method, they are
            // separately defined.
            if (channel instanceof SocketChannel) {
                ((SocketChannel) channel).connect(address);
            } else {
                ((DatagramChannel) channel).connect(address);
            }

            synchronized (pendingConnections) {
                pendingConnections.add(id);
            }
        } catch (IOException ioe) {
            generateExceptionEvent(id, ioe);
            return 0;
        }
        selector.wakeup();
        return id;
    }

    /**
     * This method sends the given data down the socket mapped to the
     * given ID.
     * 
     * NOTE: It does not return until all the data is drained from the
     * buffer. Future versions may want to return immediately and send
     * data on a different thread to keep the caller lively.
     * 
     */
    public long sendData(long socketID, ByteBuffer data) {
        // System.out.println("Request to send: " + data.capacity() + " bytes");

        data.flip();
        ByteChannel channel = getChannel(socketID);
        if (channel == null) {
            return 0;
        }
        int totalWritten = 0;
        try {
            if (!channel.isOpen()
                    || (isReliableChannel(channel) && !((SocketChannel) channel).isConnected())) {
                generateExceptionEvent(socketID, new ClosedChannelException());
                return 0;
            }

            while (data.hasRemaining()) {
                totalWritten += channel.write(data);
            }
        } catch (IOException ioe) {
            generateExceptionEvent(socketID, ioe);
        }

        return totalWritten;
    }

    // see interface JDoc
    public void closeSocket(long socketID) {
        synchronized (pendingClosures) {
            if (!pendingClosures.contains(socketID)) {
                pendingClosures.add(socketID);
            }
        }
        selector.wakeup();
    }

    /**
     * Returns the ByteChannel associated with the given socketID.
     * 
     * @param socketID the socket ID
     * 
     * @return the Channel mapped to socketID
     */
    private ByteChannel getChannel(long socketID) {
        SocketInfo info = socketMap.get(socketID);
        if (info == null) {
            // attempt to reference a channel that is no longer valid.
            System.err.println("No channel in map, ID: " + socketID);
            new Throwable().printStackTrace();
            return null;
        }
        return info.channel;
    }

    /**
     * Generates and maps a SocketInfo object to an autogenerated socket
     * ID. This ID can be used to reference the Socket for all future
     * communication.
     * 
     * @param sim
     * @param access
     * @param gloID
     * @param channel
     */
    private void storeChannel(long key, Simulation sim, ACCESS_TYPE access,
            long gloID, ByteChannel channel) {
        SocketInfo info = new SocketInfo(sim, access, gloID, channel);
        socketMap.put(key, info);
    }

    /**
     * Starts the thread.
     * 
     */
    private void start() {
        socketMap.clear();
        synchronized (pendingConnections) {
            pendingConnections.clear();
        }
        synchronized (pendingClosures) {
            pendingClosures.clear();
        }
        shouldUpdate = true;
        try {
            selector = Selector.open();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        Thread t = new Thread() {
            public void run() {
                while (shouldUpdate) {
                    update();
                }
            }
        };
        t.start();
    }

    /**
     * <p>
     * This method is called repeatedly in a separate thread. The idea
     * here is that if all socket processing is done by one thread,
     * it'll minimize concurrency issues, although future versions could
     * use a thread pool.
     * 
     * It does the following:
     * </p>
     * 
     * <ul>
     * <li>Attends to any I/O that's ready to be processed (reads or
     * connects).</li>
     * <li>Checks for, and attempts to complete, any pending connection
     * requests.</li>
     * <li>Checks for, and closes, any pending socket closure requests.</li>
     * </ul>
     * 
     */
    private void update() {
        long socketID = -1; // If there's an exception thrown
        // this will be the offending socket.
        // The socket will be closed.
        try {
            selector.select(); // block until some selectable I/O
                                // happens or wakeup() is called.

            Iterator keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = (SelectionKey) keys.next();
                keys.remove();
                socketID = (Long) key.attachment();
                boolean reliable = isReliableChannel(key.channel());
                ByteChannel curChannel = (ByteChannel) key.channel();
                if (key.isConnectable()) {
                    if (reliable
                            && ((SocketChannel) curChannel).finishConnect()) {
                        key.interestOps(SelectionKey.OP_READ);
                        generateEvent(socketID, "socketOpened",
                                new Class[] { long.class },
                                new Object[] { socketID });

                    }
                } else if (key.isReadable()) {
                    if (curChannel.isOpen()) {
                        if (!reliable
                                || (reliable && ((SocketChannel) curChannel).isConnected())) {
                            ByteBuffer in = ByteBuffer.allocate(reliable
                                    ? RELIABLE_BUFFER_SIZE
                                    : UNRELIABLE_BUFFER_SIZE);
                            int numBytes = curChannel.read(in);
                            in.flip();

                            generateEvent(
                                    socketID,
                                    "dataReceived",
                                    new Class[] { long.class, ByteBuffer.class },
                                    new Object[] { socketID, in });
                        }
                    } else {
                        // System.out.println("channel " + socketID + "
                        // not connected");
                        generateExceptionEvent(socketID,
                                new ClosedChannelException());
                        doSocketClose(socketID);
                    }
                }
            }

            checkPendingConnections();
            checkPendingClosures();
        } catch (IOException ioe) {
            if (socketID >= 0) {
                generateExceptionEvent(socketID, ioe);
                doSocketClose(socketID);
            } else {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Iterates through the list of pending connection requests and
     * registers the channels for either connection (reliable) or
     * directly for read (unreliable).
     * 
     */
    private void checkPendingConnections() {
        synchronized (pendingConnections) {
            for (int i = pendingConnections.size() - 1; i >= 0; i--) {
                long curSocketID = pendingConnections.get(i);
                pendingConnections.remove(curSocketID);
                ByteChannel bc = getChannel(curSocketID);
                if (bc == null) {
                    continue;
                }
                AbstractSelectableChannel curChannel = (AbstractSelectableChannel) bc;
                boolean isReliable = isReliableChannel(curChannel);
                SelectionKey key = null;
                try {
                    int interestedOp = isReliable ? SelectionKey.OP_CONNECT
                            : SelectionKey.OP_READ;
                    key = curChannel.register(selector, interestedOp);
                    key.attach(curSocketID);

                } catch (IOException ioe) { // connection failed for
                                            // some reason
                    if (key != null) {
                        key.cancel();
                    }
                    generateExceptionEvent(curSocketID, ioe);
                }
                // If this is an unreliable connection, call the
                // socketOpened callback
                // immediately.
                if (!isReliable) {
                    generateEvent(curSocketID, "socketOpened",
                            new Class[] { long.class },
                            new Object[] { curSocketID });
                }
            }
        }
    }

    /**
     * Iterates through the list of pending closure requests and closes
     * the connections accordingly.
     */
    private void checkPendingClosures() {
        synchronized (pendingClosures) {
            for (int i = pendingClosures.size() - 1; i >= 0; i--) {
                long curSocketID = pendingClosures.get(i);

                doSocketClose(curSocketID);
                pendingClosures.remove(curSocketID);
            }
        }
    }

    /**
     * Attempts to close the given socket. This will implicitly
     * de-register its key with the selector.
     * 
     * @param socketID
     */
    private void doSocketClose(long socketID) {
        SocketInfo info = socketMap.get(socketID);
        if (info == null) {
            return;
        }
        ByteChannel curChannel = info.channel;
        if (curChannel == null) { // already closed.
            return;
        }
        try {
            if (curChannel.isOpen()) {
                curChannel.close();

                generateEvent(socketID, "socketClosed",
                        new Class[] { long.class }, new Object[] { socketID });
                socketMap.remove(socketID);
            }
        } catch (IOException ioe) { // closure failed for some reason
            generateExceptionEvent(socketID, ioe);
        }
    }

    private void generateExceptionEvent(long socketID, IOException ioe) {
        generateEvent(socketID, "socketException", new Class[] { long.class,
                IOException.class }, new Object[] { socketID, ioe });
    }

    private void generateEvent(long socketID, String callBack, Class[] params,
            Object[] args) {
        // System.out.println("begin generate event " + callBack + " " +
        // socketID);
        SocketInfo info = socketMap.get(socketID);

        Method cbMethod = null;
        try {
            cbMethod = SimRawSocketListener.class.getMethod(callBack, params);
        } catch (SecurityException e1) {
            e1.printStackTrace();
        } catch (NoSuchMethodException e1) {
            e1.printStackTrace();
        }

        SimTask task = info.simulation.newTask(info.access,
                new GLOReferenceImpl<GLO>(info.gloID), cbMethod, args, null);

        info.simulation.queueTask(task);

    }

    /**
     * Stops the manager from updating.
     * 
     */
    private void stop() {
        shouldUpdate = false;
        if (selector == null) {
            return;
        }
        try {
            selector.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private boolean isReliableChannel(Channel channel) {
        return channel instanceof SocketChannel;
    }

    private class SocketInfo {

        Simulation simulation;
        ACCESS_TYPE access;
        long gloID;
        ByteChannel channel;

        SocketInfo(Simulation sim, ACCESS_TYPE access, long gloID,
                ByteChannel channel) {
            this.simulation = sim;
            this.access = access;
            this.gloID = gloID;
            this.channel = channel;
        }
    }

    public long getNextSocketID() {
        return currentSocketID.getAndIncrement();
    }
}
