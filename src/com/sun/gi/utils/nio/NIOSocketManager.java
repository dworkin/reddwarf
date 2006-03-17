/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.utils.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class NIOSocketManager implements Runnable {

    private static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    private Selector selector;
    private volatile boolean shutdown;

    private int tcpBufSize;
    private int udpBufSize;

    private final Set<NIOSocketManagerListener> listeners =
	new HashSet<NIOSocketManagerListener>();

    private final List<NIOConnection> initiatorQueue =
	new ArrayList<NIOConnection>();

    private final List<NIOConnection> receiverQueue =
	new ArrayList<NIOConnection>();

    private final List<ServerSocketChannel> acceptorQueue =
	new ArrayList<ServerSocketChannel>();

    private final List<SelectableChannel> writeQueue =
	new ArrayList<SelectableChannel>();

    public NIOSocketManager() throws IOException {
        this(128 * 1024,  // tcp buffer size in bytes
              64 * 1024); // udp buffer size in bytes
    }

    public NIOSocketManager(int tcpBufferSize, int udpBufferSize)
            throws IOException {

	shutdown = false;
        this.tcpBufSize = tcpBufferSize;
        this.udpBufSize = udpBufferSize;
	openSelector();
        new Thread(this).start();
    }
    
    protected void openSelector() throws IOException {
	selector = Selector.open();
    }

    public void acceptConnectionsOn(SocketAddress addr) throws IOException {

        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(addr);

        synchronized (acceptorQueue) {
            acceptorQueue.add(channel);
        }
        selector.wakeup();
    }

    public NIOConnection makeConnectionTo(SocketAddress addr) {

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
                log.severe("Can't allocate buffers for connection");
                log.throwing(getClass().getName(), "makeConnectionTo", e);
                e.printStackTrace();
                try {
                    sc.close();
                    dc.close();
                } catch (IOException ie) {
		    log.throwing(getClass().getName(), "makeConnectionTo", ie);
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
	    log.throwing(getClass().getName(), "makeConnectionTo", ex);
            return null;
        }
    }

    // This runs the actual polled input

    public void run() {

        while (! shutdown) {

	    if ((selector == null) || (! selector.isOpen())) {
		try {
		    openSelector();
		} catch (IOException e) {
		    log.severe("Could not open the selector!");
		    // TODO:  need to take more drastic action
		}
	    }

	    try {
		synchronized (initiatorQueue) {
		    for (NIOConnection conn : initiatorQueue) {
			try {
			    conn.registerConnect(selector);
			} catch (IOException ex) {
			    ex.printStackTrace();
			    log.throwing(getClass().getName(), "run", ex);
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
			    log.throwing(getClass().getName(), "run", ex);
			}
		    }
		    receiverQueue.clear();
		}

		synchronized (acceptorQueue) {
		    for (ServerSocketChannel chan : acceptorQueue) {
			try {
			    chan.register(selector, OP_ACCEPT);
			} catch (ClosedChannelException ex) {
			    ex.printStackTrace();
			    log.throwing(getClass().getName(), "run", ex);
			}
		    }
		    acceptorQueue.clear();
		}

		synchronized (writeQueue) {
		    for (SelectableChannel chan : writeQueue) {
			SelectionKey key = chan.keyFor(selector);
			if (key != null && key.isValid()) {
			    key.interestOps(key.interestOps() | OP_WRITE);
			}
		    }
		    writeQueue.clear();
		}

                log.finest("Calling select");

                int n = selector.select();

                log.finer("selector: " + n + " ready handles");

                if (n > 0) {
                    processSocketEvents();
                }
            } catch (ClosedSelectorException e) {
		log.throwing(getClass().getName(), "run", e);
            } catch (IOException e) {
		log.throwing(getClass().getName(), "run", e);
            }
        }
    }

    /**
     * processSocketEvents
     */
    private void processSocketEvents() {

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
    }

    private void handleRead(SelectionKey key) {
        ReadWriteSelectorHandler h =
	    (ReadWriteSelectorHandler) key.attachment();
        try {
            h.handleRead(key);
        } catch (IOException e) {
	    log.throwing(getClass().getName(), "handleRead", e);
            h.handleClose();
        }
    }

    private void handleWrite(SelectionKey key) {
        ReadWriteSelectorHandler h =
	    (ReadWriteSelectorHandler) key.attachment();
        try {
            h.handleWrite(key);
        } catch (IOException e) {
	    log.throwing(getClass().getName(), "handleWrite", e);
            h.handleClose();
        }
    }

    private void handleAccept(SelectionKey key) {
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
                log.severe("Can't allocate buffers for connection");
                e.printStackTrace();
		log.throwing(getClass().getName(), "handleAccept", e);
                try {
                    sc.close();
                    dc.close();
                } catch (IOException ie) {
		    log.throwing(getClass().getName(), "handleAccept", ie);
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
	    log.throwing(getClass().getName(), "handleAccept", ex);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void enableWrite(SelectableChannel chan) {
        synchronized (writeQueue) {
            writeQueue.add(chan);
        }
        selector.wakeup();
    }

    private void handleConnect(SelectionKey key) {
        NIOConnection conn = (NIOConnection) key.attachment();
        try {
            conn.processConnect(key);
            for (NIOSocketManagerListener l : listeners) {
                l.connected(conn);
            }
        } catch (IOException ex) {
	    log.throwing(getClass().getName(), "handleConnect", ex);
            log.warning("NIO connect failure: " + ex.getMessage());
            conn.disconnect();
            for (NIOSocketManagerListener l : listeners) {
                l.connectionFailed(conn);
            }
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
	    shutdown = true;
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
	    log.throwing(getClass().getName(), "shutdown", e);
        }
    }
}
