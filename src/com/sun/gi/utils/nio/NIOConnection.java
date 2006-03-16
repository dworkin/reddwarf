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

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

// TODO: Set SO_RCVBUF and SO_SNDBUF on the channels
public class NIOConnection implements SelectorHandler {

    static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    protected final NIOSocketManager socketManager;
    protected final PacketHandler tcpHandler;
    protected final PacketHandler udpHandler;

    // made available for filling by the manager
    protected ByteBuffer inputBuffer;

    protected ByteBuffer sizeHeader;
    protected int currentPacketSize = -1;
    protected ByteBuffer outputHeader = ByteBuffer.allocate(4);

    Set<NIOConnectionListener> listeners = new TreeSet<NIOConnectionListener>();

    public NIOConnection(NIOSocketManager mgr, SocketChannel sockChannel,
            DatagramChannel dgramChannel, int tcpBufSize, int udpBufSize) {

        socketManager = mgr;

        tcpHandler = new PacketHandler(this, sockChannel, tcpBufSize);
        udpHandler = new PacketHandler(this, dgramChannel, udpBufSize);
    }

    public void open(Selector sel) throws IOException {
        tcpHandler.open(sel);
        udpHandler.open(sel);
    }

    public void registerConnect(Selector sel) throws IOException {
        tcpHandler.channel.register(sel, OP_CONNECT, this);
    }

    public void processConnect(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        if (sc.finishConnect()) {
            tcpHandler.open(key.selector());
            Socket sock = sc.socket();

            // Point the UDP channel to the right endpoint
            DatagramChannel dc = (DatagramChannel) udpHandler.channel;
            DatagramSocket ds = dc.socket();
            ds.bind(sock.getLocalSocketAddress());
            dc.connect(sock.getRemoteSocketAddress());
            udpHandler.open(key.selector());
        }
    }

    void packetReceived(ByteBuffer pkt) {
        for (NIOConnectionListener l : listeners) {
            l.packetReceived(this, pkt);
        }
    }

    class PacketHandler implements ReadWriteSelectorHandler {

        final NIOConnection parent;
        final SelectableChannel channel;
        final ByteBuffer sendBuffer;
        final ByteBuffer recvBuffer;

        protected int nextRecvPacketLen = 0;
        protected SelectionKey key = null;

        public PacketHandler(NIOConnection conn, SelectableChannel chan,
                int bufSize) {
            parent = conn;
            channel = chan;
            sendBuffer = ByteBuffer.allocateDirect(bufSize);
            recvBuffer = ByteBuffer.allocateDirect(bufSize);
        }

        public void open(Selector sel) {
            try {
                key = channel.register(sel, OP_READ, this);
            } catch (IOException e) {
                log.throwing(getClass().getName(), "open", e);
                e.printStackTrace();
            }
        }

        protected boolean processRecvBuffer() throws IOException {
            recvBuffer.flip();
            boolean buffer_empty = false;

            for (;;) {
                if (nextRecvPacketLen == 0) {
                    // We're waiting for a frame header (an int)

                    if (recvBuffer.remaining() == 0) {
                        // No partial packets remain in the buffer
                        // log.finest("Dispatched entire buffer");
                        buffer_empty = true;
                        break;
                    }

                    if (recvBuffer.remaining() < 4) {
                        // log.fine("Waiting for a packet header -- "
                        // + "only have " + recvBuffer.remaining());
                        break;
                    }

                    // Got frame header
                    int packet_len = recvBuffer.getInt();
                    if (packet_len <= 0) {
                        // log.warning("Bad packet length: " +
                        // packet_len);
                        break;
                    }

                    // Now we know the new packet's length
                    nextRecvPacketLen = packet_len;
                }

                if (recvBuffer.remaining() < nextRecvPacketLen) {
                    // We don't have all of the packet
                    // log.finer("Partial packet; have " +
                    // recvBuffer.remaining() +
                    // " bytes, need " + nextRecvPacketLen);
                    break;
                }

                // Got a whole packet; dispatch it
                // Copy the packet in this implementation
                ByteBuffer packetView = recvBuffer.slice().asReadOnlyBuffer();
                packetView.limit(nextRecvPacketLen);
                recvBuffer.position(recvBuffer.position() + nextRecvPacketLen);
                ByteBuffer packetCopy = ByteBuffer.allocate(nextRecvPacketLen);
                packetCopy.put(packetView);
                packetCopy.rewind();
                nextRecvPacketLen = 0;
                parent.packetReceived(packetCopy);

                // Loop around and see if we can dispatch some more
            }

            recvBuffer.compact();
            return buffer_empty;
        }

        public void handleRead(SelectionKey k) throws IOException {
            if (!channel.isOpen()) {
                throw new IOException("not open");
            }

            log.finest("channel is a " + channel.getClass());

            int rc = ((ReadableByteChannel) channel).read(recvBuffer);

            if (rc <= 0) {
                throw new IOException("Error reading");
            }

            processRecvBuffer();
        }

        public void handleWrite(SelectionKey k) throws IOException {
            //int wc = 0;
            boolean bufferEmpty;

            synchronized (sendBuffer) {
                sendBuffer.flip();
                /* wc = */ ((WritableByteChannel) channel).write(sendBuffer);
                bufferEmpty = (!sendBuffer.hasRemaining());
                sendBuffer.compact();
            }

            if (bufferEmpty) {
                k.interestOps(k.interestOps() & (~SelectionKey.OP_WRITE));
            }
        }

        public void handleClose() {
            parent.handleClose();
        }

        public void close() {
            try {
                log.fine("Closing " + channel);
                channel.close();
            } catch (IOException e) {
                log.throwing(getClass().getName(), "close", e);
                e.printStackTrace();
            }
        }

        public void send(ByteBuffer[] packetParts) {
            int sz = 0;
            for (ByteBuffer buf : packetParts) {
                buf.flip();
                sz += buf.remaining();
            }
            synchronized (sendBuffer) {
                sendBuffer.putInt(sz);
                for (ByteBuffer buf : packetParts) {
                    sendBuffer.put(buf);
                }
            }
            parent.socketManager.enableWrite(channel);
        }

    }

    public void handleClose() {
        tcpHandler.close();
        udpHandler.close();

        for (NIOConnectionListener l : listeners) {
            l.disconnected(this);
        }
    }

    public void disconnect() {
        handleClose();
    }

    /**
     * addListener
     * 
     * @param l NIOConnectionListener
     */
    public void addListener(NIOConnectionListener l) {
        listeners.add(l);
    }

    /**
     * removeListener
     * 
     * @param l NIOConnectionListener
     */
    public void removeListener(NIOConnectionListener l) {
        listeners.remove(l);
    }

    /**
     * send
     * 
     * @param packet ByteBuffer
     */
    public synchronized void send(ByteBuffer packet) throws IOException {
        send(packet, true);
    }

    /**
     * send
     * 
     * @param packet ByteBuffer
     * @param reliable boolean
     */
    public synchronized void send(ByteBuffer packet, boolean reliable)
            throws IOException {
        send(new ByteBuffer[] { packet }, reliable);
    }

    /**
     * send
     * 
     * @param packetParts ByteBuffer[]
     */
    public synchronized void send(ByteBuffer[] packetParts) throws IOException {
        send(packetParts, true);
    }

    /**
     * send
     * 
     * @param packetParts ByteBuffer[]
     * @param reliable boolean
     */
    public synchronized void send(ByteBuffer[] packetParts, boolean reliable)
            throws IOException {
        // log.entering("NIOChannel", "send[]");

        PacketHandler h = reliable ? tcpHandler : udpHandler;
        h.send(packetParts);
    }
}
