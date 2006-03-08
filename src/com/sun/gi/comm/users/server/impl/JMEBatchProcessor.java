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

package com.sun.gi.comm.users.server.impl;

import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * 
 * @author as93050
 */
public class JMEBatchProcessor implements TransportProtocolTransmitter {

    private TransportProtocol transportProtocol;
    private SGSUserImpl user;
    private ByteBuffer[] packetsToBeSent;

    /** Creates a new instance of JMEBatchProcessor */
    public JMEBatchProcessor() {
        transportProtocol = new BinaryPktProtocol();
    }

    public void setUser(SGSUserImpl user) {
        this.user = user;
        // this should always happen
        // once the user is created send the server id back to
        // the client
        if (packetsToBeSent != null) {
            sendBuffers(packetsToBeSent, true);
            packetsToBeSent = null;
        }
    }

    public void sendBuffers(ByteBuffer[] buffs, boolean reliable) {
        // the user creation process sends a message back to the user
        // this happens before the user is actually completed and
        // therefore
        // here when we try to send the message we can't get the queue
        // we don't
        // have the user, therefore we need to store the message until
        // the user
        // is created
        if (user == null) {
            packetsToBeSent = buffs;
        } else {
            Queue<byte[]> outgoingMessageQueue =
                ((JMESGSUserImpl) user).getOutgoingMessageQueue();
            byte[] tempPacket = new byte[8096];
            byte[] packetArray;
            byte[] packetToQueue;
            int packetsSize = 0;
            int position = 0;
            for (ByteBuffer packet : buffs) {
                packet.flip();
                int packetSize = packet.remaining();
                packetArray = new byte[packetSize];
                packet.get(packetArray);
                packetsSize += packetSize;
                System.arraycopy(packetArray, 0, tempPacket, position,
                        packetSize);
                position += packetSize;
            }
            packetToQueue = new byte[packetsSize + 2];
            packetToQueue[0] = short1((short) packetsSize);
            packetToQueue[1] = short0((short) packetsSize);
            System.arraycopy(tempPacket, 0, packetToQueue, 2, packetsSize);
            outgoingMessageQueue.add(packetToQueue);
        }
    }

    private byte short1(short x) {
        return (byte) (x >> 8);
    }

    private byte short0(short x) {
        return (byte) (x >> 0);
    }

    private int makeShort(byte b1, byte b0) {
        return (int) ((((b1 & 0xff) << 8) | ((b0 & 0xff) << 0)));
    }

    public void closeConnection() {}

    public void packetsReceived(byte[] data) {
        List<ByteBuffer> packetsReceived = extractPackets(data);
        for (ByteBuffer b : packetsReceived) {
            user.packetReceived(b);
        }
    }

    /**
     * Because we are doing batch processing, we will receive a bunch of
     * packets from a given client as a byte[]. We need to extract each
     * packet and wrap it into a ByteBuffer before it can be processed.
     * Note: the first byte of every request received by the client will
     * be the number of packets sent. Also note: the first 2 bytes of
     * every packet are it's size in bytes
     */
    private List<ByteBuffer> extractPackets(byte[] data) {
        int numberOfPackets = data[0];
        // logout message will be a packet whose size is 0
        if (numberOfPackets == 0) {
            System.out.println("disconnecting");
            user.disconnected();
        }
        int packetSize = 0;
        int position = 1;
        List<ByteBuffer> packetList = new ArrayList<ByteBuffer>();
        for (int i = 0; i < numberOfPackets; i++) {
            // extract the packet size
            byte packetSize1 = data[position++];
            byte packetSize0 = data[position++];
            packetSize = makeShort(packetSize1, packetSize0);
            byte[] packet = new byte[packetSize];
            System.arraycopy(data, position, packet, 0, packetSize);
            position += packetSize;
            packetList.add(ByteBuffer.wrap(packet));
        }
        return packetList;
    }
}
