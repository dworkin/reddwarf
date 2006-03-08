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

package com.sun.gi.utils.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

public class NIOSocketManager implements Runnable {

    private static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    private Selector selector;

    private int tcpBufSize;
    private int udpBufSize;

    private Set<NIOSocketManagerListener> listeners =
	new TreeSet<NIOSocketManagerListener>();

    private List<NIOConnection> initiatorQueue =
	new ArrayList<NIOConnection>();

    private List<NIOConnection> receiverQueue =
	new ArrayList<NIOConnection>();

    private List<ServerSocketChannel> acceptorQueue =
	new ArrayList<ServerSocketChannel>();

    private List<SelectableChannel> writeQueue =
	new ArrayList<SelectableChannel>();

    public NIOSocketManager() throws IOException {
        this(128 * 1024,  // tcp buffer size
              64 * 1024); // udp buffer size
    }

    public NIOSocketManager(int tcpBufferSize, int udpBufferSize)
            throws IOException {

        selector = Selector.open();
        this.tcpBufSize = tcpBufferSize;
        this.udpBufSize = udpBufferSize;
        new Thread(this).start();
    }

    public void acceptConnectionsOn(SocketAddress addr) throws IOException {
        // log.entering("NIOSocketManager", "acceptConnectionsOn");

        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(addr);

        synchronized (acceptorQueue) {
            acceptorQueue.add(channel);
        }
        selector.wakeup();

        // log.exiting("NIOSocketManager", "acceptConnectionsOn");
    }

    public NIOConnection makeConnectionTo(SocketAddress addr) {
        // log.entering("NIOSocketManager", "makeConnectionTo");

        try {
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            sc.connect(addr);

            DatagramChannel dc = DatagramChannel.open();
            dc.configureBlocking(false);

            NIOConnection conn = null;
            try {
                conn = new NIOConnection(this, sc, dc, tcpBufSize, udpBufSize);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                log.severe("Can't allocate buffers for connection");
                try {
                    sc.close();
                    dc.close();
                } catch (IOException ie) {
                    // ignore
                }
                return null;
            }

            synchronized (initiatorQueue) {
                initiatorQueue.add(conn);
            }
            selector.wakeup();

            return conn;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
            // } finally {
            // log.exiting("NIOSocketManager", "makeConnectionTo");
        }
    }

    // This runs the actual polled input

    public void run() {
        // log.entering("NIOSocketManager", "run");

        while (true) { // until shutdown() is called

            synchronized (initiatorQueue) {
                for (NIOConnection conn : initiatorQueue) {
                    try {
                        conn.registerConnect(selector);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                initiatorQueue.clear();
            }

            synchronized (receiverQueue) {
                for (NIOConnection conn : receiverQueue) {
                    try {
                        conn.open(selector);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                receiverQueue.clear();
            }

            synchronized (acceptorQueue) {
                for (ServerSocketChannel chan : acceptorQueue) {
                    try {
                        chan.register(selector, OP_ACCEPT);
                    } catch (ClosedChannelException ex2) {
                        ex2.printStackTrace();
                    }
                }
                acceptorQueue.clear();
            }

            synchronized (writeQueue) {
                for (SelectableChannel chan : writeQueue) {
                    SelectionKey key = chan.keyFor(selector);
                    if (key == null) { // XXX: DJE
                        log.warning("key is null");
                        continue;
                    }
                    if (key.isValid()) {
                        key.interestOps(key.interestOps() | OP_WRITE);
                    }
                }
                writeQueue.clear();
            }

            if (!selector.isOpen())
                break;

            try {
                log.finest("Calling select");

                int n = selector.select();

                log.finer("selector: " + n + " ready handles");

                if (n > 0) {
                    processSocketEvents();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // log.exiting("NIOSocketManager", "run");
    }

    /**
     * processSocketEvents
     */
    private void processSocketEvents() {
        // log.entering("NIOSocketManager", "processSocketEvents");

        Iterator<SelectionKey> i = selector.selectedKeys().iterator();

        // Walk through set
        while (i.hasNext()) {

            // Get key from set
            SelectionKey key = i.next();

            // Remove current entry
            i.remove();

            if (key.isValid() && key.isAcceptable()) {
                handleAccept(key);
            }

            if (key.isValid() && key.isConnectable()) {
                handleConnect(key);
            }

            if (key.isValid() && key.isReadable()) {
                handleRead(key);
            }

            if (key.isValid() && key.isWritable()) {
                handleWrite(key);
            }
        }

        // log.exiting("NIOSocketManager", "processSocketEvents");
    }

    private void handleRead(SelectionKey key) {
        // log.entering("NIOSocketManager", "handleRead");
        ReadWriteSelectorHandler h = (ReadWriteSelectorHandler) key.attachment();
        try {
            h.handleRead(key);
        } catch (IOException ex) {
            h.handleClose();
            // } finally {
            // log.exiting("NIOSocketManager", "handleRead");
        }
    }

    private void handleWrite(SelectionKey key) {
        // log.entering("NIOSocketManager", "handleWrite");
        ReadWriteSelectorHandler h = (ReadWriteSelectorHandler) key.attachment();
        try {
            h.handleWrite(key);
        } catch (IOException ex) {
            h.handleClose();
            // } finally {
            // log.exiting("NIOSocketManager", "handleWrite");
        }
    }

    private void handleAccept(SelectionKey key) {
        // log.entering("NIOSocketManager", "handleAccept");
        // Get channel
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

        NIOConnection conn = null;

        // Accept request
        try {
            SocketChannel sc = serverChannel.accept();
            if (sc == null) {
                log.warning("accept returned null");
                return;
            }
            sc.configureBlocking(false);

            // Now create a UDP channel for this endpoint

            DatagramChannel dc = DatagramChannel.open();
            try {
                conn = new NIOConnection(this, sc, dc, tcpBufSize, udpBufSize);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                log.severe("Can't allocate buffers for connection");
                try {
                    sc.close();
                    dc.close();
                } catch (IOException ie) {
                    // ignore
                }
                return;
            }

            dc.socket().setReuseAddress(true);
            dc.configureBlocking(false);
            dc.socket().bind(sc.socket().getLocalSocketAddress());

            // @@: Workaround for Windows JDK 1.5; it's unhappy with
            // this
            // call because it's trying to use the (null) hostname
            // instead
            // of the host address. So we explicitly pull out the host
            // address and create a new InetSocketAddress with it.
            //dc.connect(sc.socket().getRemoteSocketAddress());
            dc.connect(new InetSocketAddress(
                    sc.socket().getInetAddress().getHostAddress(),
                    sc.socket().getPort()));

            log.finest("udp local " + dc.socket().getLocalSocketAddress()
                    + " remote " + dc.socket().getRemoteSocketAddress());

            synchronized (receiverQueue) {
                receiverQueue.add(conn);
            }

            for (NIOSocketManagerListener l : listeners) {
                l.newConnection(conn);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            if (conn != null) {
                conn.disconnect();
            }
        } finally {
            //log.exiting("NIOSocketManager", "handleAccept");
        }
    }

    public void enableWrite(SelectableChannel chan) {
        synchronized (writeQueue) {
            writeQueue.add(chan);
        }
        selector.wakeup();
    }

    private void handleConnect(SelectionKey key) {
        //log.entering("NIOSocketManager", "handleConnect");

        NIOConnection conn = (NIOConnection) key.attachment();
        try {
            conn.processConnect(key);
            for (NIOSocketManagerListener l : listeners) {
                l.connected(conn);
            }
        } catch (IOException ex) {
            log.warning("NIO connect failure: " + ex.getMessage());
            conn.disconnect();
            for (NIOSocketManagerListener l : listeners) {
                l.connectionFailed(conn);
            }
            //} finally {
            //log.exiting("NIOSocketManager", "handleConnect");
        }
    }

    /**
     * addListener
     *
     * @param l NIOSocketManagerListener
     */
    public void addListener(NIOSocketManagerListener l) {
        listeners.add(l);
    }

    public void shutdown() {
        try {
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
