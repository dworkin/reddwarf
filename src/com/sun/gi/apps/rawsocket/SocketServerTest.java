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

package com.sun.gi.apps.rawsocket;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This is a test harness server for the RawSocketManager, and not
 * intended to be part of a production release.
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class SocketServerTest {

    List<ServerSocketChannel> channels;
    Selector selector;

    public SocketServerTest() {

        channels = new ArrayList<ServerSocketChannel>();
        restart();
        while (true) {
            try {

                acceptConnections();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                restart();
            }
        }

    }

    void acceptUDP() {
        try {
            DatagramChannel channel = DatagramChannel.open();
            DatagramSocket socket = channel.socket();
            InetSocketAddress address = new InetSocketAddress(6000);
            socket.bind(address);
            ByteBuffer buffer = ByteBuffer.allocate(65507);
            while (true) {
                System.out.println("Waiting for Datagram");
                SocketAddress clientAddress = channel.receive(buffer);
                System.out.println("Received Datagram");
                buffer.flip();
                for (int i = buffer.position(); i < buffer.limit(); i++) {
                    System.out.print(buffer.get());
                }
                System.out.println();
                buffer.flip();
                channel.send(buffer, clientAddress);
                buffer.clear();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void restart() {
        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            for (ServerSocketChannel s : channels) {
                s.close();
            }
            setupChannels(10);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void acceptConnections() throws IOException {
        Thread t = new Thread() {
            public void run() {
                acceptUDP();
            }
        };
        t.start();
        while (true) {
            selector.select();

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey curKey = it.next();
                it.remove();

                if (curKey.isAcceptable()) {
                    ServerSocketChannel curChannel = (ServerSocketChannel) curKey.channel();
                    SocketChannel client = curChannel.accept();
                    System.out.println("Accepting connection from " + client);
                    client.configureBlocking(false);
                    client.register(selector, OP_READ | OP_WRITE);
                } else if (curKey.isReadable()) {
                    SocketChannel curChannel = (SocketChannel) curKey.channel();
                    ByteBuffer in = ByteBuffer.allocate(100);
                    int numBytes = curChannel.read(in);
                    if (numBytes < 0) {
                        curKey.cancel();
                    }
                    in.flip();
                    System.out.print("Received: " + numBytes + " data:");
                    for (int i = in.position(); i < in.limit(); i++) {
                        System.out.print(in.get());
                    }
                    System.out.println();
                } else if (curKey.isWritable()) {
                    System.out.println("writing response");
                    ByteBuffer response = ByteBuffer.wrap("Status OK".getBytes());
                    SocketChannel channel = (SocketChannel) curKey.channel();
                    channel.write(response);
                }
            }

            try {
                Thread.sleep(500); // poor man's simulation of network
                                    // latency.
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

        }
    }

    private void setupChannels(int numPorts) throws IOException {
        channels.clear();
        selector = Selector.open();
        for (int i = 5000; i < (5000 + numPorts); i++) {
            ServerSocketChannel curChannel = ServerSocketChannel.open();
            curChannel.configureBlocking(false);
            curChannel.socket().bind(new InetSocketAddress(i));
            curChannel.register(selector, OP_ACCEPT);

            channels.add(curChannel);
            System.out.println("Listening on port " + i);
        }
    }

    public static void main(String[] args) {
        new SocketServerTest();
    }
}
