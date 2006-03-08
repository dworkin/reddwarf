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

package com.sun.gi.framework.interconnect.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.utils.LRMPSocketListener;
import com.sun.gi.utils.LRMPSocketManager;
import com.sun.gi.utils.ReversableMap;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;

public class LRMPTransportManager
        implements LRMPSocketListener, TransportManager
{
    private static int dataPort = 6824;
    private static String addr = "224.100.100.224";
    private LRMPSocketManager cmgr;
    private static InetAddress address = null;
    //private static byte[] outbytes = new byte[65];
    //private DatagramPacket outpkt = new DatagramPacket(outbytes, 65);
    private ReversableMap idMap = new ReversableMap();
    private static final byte OP_CHANNEL_ANNOUNCE = 1;
    private static final byte OP_CHANNEL_REMOVE = 2;
    private static final byte OP_DATA = 3;
    private Map chanMap = new HashMap();
    private Map oldChanMap = new HashMap();
    //private boolean echo = false;

    public LRMPTransportManager() {
        String prop = System.getProperty("sgs.lrmp.mcastaddress");
        if (prop != null) {
            addr = prop;
        }
        prop = System.getProperty("sgs.lrmp.mcastport");
        if (prop != null) {
            dataPort = Integer.parseInt(prop);
        }
        byte ttl = 5;
        prop = System.getProperty("sgs.lrmp.ttl");
        if (prop != null) {
            ttl = (byte) Integer.parseInt(prop);
        }
        try {
            address = InetAddress.getByName(addr);
            LRMPTransportProfile tp =
                new LRMPTransportProfile(address, dataPort);
            tp.setMaxDataRate(100000);
            tp.setOrdered(true);
            tp.setTTL(ttl);
            cmgr = new LRMPSocketManager(tp);
            cmgr.setEcho(false);
            cmgr.addListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * socketClosed
     * 
     * @param lRMPSocketManager LRMPSocketManager
     */
    public void socketClosed(LRMPSocketManager lRMPSocketManager) {
        throw new RuntimeException("LRMP Socket Closed!  Should never happen!");
    }

    /**
     * packetArrived
     * 
     * @param lRMPSocketManager LRMPSocketManager
     * @param inpkt DatagramPacket
     */
    public void packetArrived(LRMPSocketManager lRMPSocketManager,
            DatagramPacket inpkt) {
        ByteBuffer buff = ByteBuffer.wrap(inpkt.getData(), inpkt.getOffset(),
                inpkt.getLength());
        byte op = buff.get();
        // System.err.println("Processing transport pkt opcode: "+op);
        switch (op) {
            case OP_CHANNEL_ANNOUNCE:
                SGSUUID uuID = new StatisticalUUID();
                uuID.read(buff);
                int strsize = buff.limit() - buff.position();
                byte[] strbytes = new byte[strsize];
                buff.get(strbytes);
                String chanName = new String(strbytes);
                synchronized (idMap) {
                    SGSUUID oldID = (SGSUUID) idMap.reverseGet(chanName);
                    //boolean accept = true;
                    //boolean propose = false;
                    if ((oldID != null) && (uuID.compareTo(oldID) > 0)) {
                        proposeID(oldID, chanName);
                    } else {
                        if (oldID != null) {
                            idMap.remove(oldID);
                            idMap.put(uuID, chanName);
                            LRMPTransportChannel currentChan =
                                (LRMPTransportChannel) chanMap.remove(oldID);
                            if (currentChan != null) {
                                currentChan.uuID = uuID;
                                chanMap.put(uuID, currentChan);
                                oldChanMap.put(oldID, currentChan);
                            }
                        }
                    }
                }
                break;

            case OP_CHANNEL_REMOVE:
                uuID = new StatisticalUUID();
                uuID.read(buff);
                LRMPTransportChannel chan = closeChannel(uuID);
                if (chan != null) {
                    chan.doCloseChannel();
                }
                break;

            case OP_DATA:
                uuID = new StatisticalUUID();
                uuID.read(buff);
                chan = (LRMPTransportChannel) chanMap.get(uuID);
                if (chan == null) {
                    chan = (LRMPTransportChannel) oldChanMap.get(uuID);
                }
                if (chan != null) {
                    ByteBuffer data = buff.slice();
                    chan.doRecieveData(data);
                }
                break;
        }

    }

    /**
     * Closes off the channel with the given ID by removing references
     * to it.
     * 
     * @param uuID the channels uuID
     * 
     * @return the channel.
     */
    LRMPTransportChannel closeChannel(SGSUUID uuID) {
        synchronized (idMap) {
            idMap.remove(uuID);
        }
        LRMPTransportChannel channel = null;
        synchronized (chanMap) {
            channel = (LRMPTransportChannel) chanMap.remove(uuID);
        }
        return channel;
    }

    // Transport Manager Methods

    /**
     * openChannel
     * 
     * @param channelName String
     * @return TransportChannel
     */
    public TransportChannel openChannel(String channelName) throws IOException {
        LRMPTransportChannel chan;
        synchronized (idMap) {
            SGSUUID chanID = (SGSUUID) idMap.reverseGet(channelName);
            if (chanID == null) {
                chanID = new StatisticalUUID();
                idMap.put(chanID, channelName);
                chan = new LRMPTransportChannel(channelName, chanID, this);
                chanMap.put(chanID, chan);
                proposeID(chanID, channelName);
            } else {
                chan = (LRMPTransportChannel) chanMap.get(chanID);
            }
        }
        return chan;
    }

    /**
     * proposeID
     * 
     * @param chanID UUID
     * @param channelName String
     */
    private void proposeID(SGSUUID chanID, String channelName) {
        ByteBuffer outbuff = ByteBuffer.allocate(channelName.length()
                + chanID.ioByteSize() + 1);
        outbuff.put(OP_CHANNEL_ANNOUNCE);
        chanID.write(outbuff);
        outbuff.put(channelName.getBytes());
        cmgr.send(new DatagramPacket(outbuff.array(), outbuff.array().length));
    }

    /**
     * ensurePacketSize
     * 
     * @param pkt DatagramPacket
     * @param i int
     */
    private DatagramPacket ensurePacketSize(DatagramPacket pkt, int i) {
        if (pkt.getData().length < i) {
            byte[] newbytes = new byte[i];
            pkt = new DatagramPacket(newbytes, i);
        }
        return pkt;
    }

    // for use by LRMPTransportChannel
    void sendData(SGSUUID uuid, ByteBuffer data) throws IOException {
        data.flip();
        int sz = data.remaining() + uuid.ioByteSize() + 1;
        ByteBuffer outbuff = ByteBuffer.allocate(sz);
        outbuff.put(OP_DATA);
        uuid.write(outbuff);
        outbuff.put(data);
        cmgr.send(new DatagramPacket(outbuff.array(), sz));
    }

    /**
     * sendData
     * 
     * @param uuID UUID
     * @param byteBuffers ByteBuffer[]
     */
    public void sendData(SGSUUID uuID, ByteBuffer[] byteBuffers) {
        int sz = uuID.ioByteSize() + 1;
        for (int i = 0; i < byteBuffers.length; i++) {
            byteBuffers[i].flip();
            sz += byteBuffers[i].remaining();
        }
        ByteBuffer outbuff = ByteBuffer.allocate(sz);
        outbuff.put(OP_DATA);
        uuID.write(outbuff);
        for (int i = 0; i < byteBuffers.length; i++) {
            outbuff.put(byteBuffers[i]);
        }
        cmgr.send(new DatagramPacket(outbuff.array(), sz));
    }
}
