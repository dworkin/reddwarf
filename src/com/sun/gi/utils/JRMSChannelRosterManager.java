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

package com.sun.gi.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;

public class JRMSChannelRosterManager {
    SGSUUID myUUID;
    RMPacketSocket ps;
    static final int HEARTBEATMILLIS = 500;
    private static final byte OP_HEARTBEAT = 0;
    private static final byte OP_DATAPKT = 1;

    private Channel channel = null;
    private Map<SGSUUID, Long> roster = new HashMap<SGSUUID, Long>();

    private List<JRMSChannelRosterManagerListener> listeners =
        new ArrayList<JRMSChannelRosterManagerListener>();

    public JRMSChannelRosterManager(Channel channel) {
        this.channel = channel;
        myUUID = new StatisticalUUID();
        try {
            ps = channel.createRMPacketSocket(TransportProfile.SEND_RECEIVE);
            startListening();
            startHeartbeat();
            Thread.sleep(HEARTBEATMILLIS); // allow roster to tick once
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void startHeartbeat() {
        new Thread() {
            public void run() {
                byte[] buff = null;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeByte(OP_HEARTBEAT);
                    oos.writeObject(myUUID);
                    oos.flush();
                    buff = baos.toByteArray();
                    oos.close();
                    baos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                while (true) {
                    DatagramPacket pkt = new DatagramPacket(buff, buff.length);
                    try {
                        ps.send(pkt);
                        Thread.sleep(HEARTBEATMILLIS);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void startListening() {
        new Thread() {
            public void run() {
                while (true) {
                    try {
                        DatagramPacket pkt = ps.receive();
                        ByteArrayInputStream bais = new ByteArrayInputStream(
                                pkt.getData(), pkt.getOffset(), pkt.getLength());
                        ObjectInputStream ois = new ObjectInputStream(bais);
                        byte op = ois.readByte();
                        switch (op) {
                            case OP_HEARTBEAT:
                                SGSUUID uuid = (SGSUUID) ois.readObject();
                                doHeartbeat(uuid);
                                break;
                            case OP_DATAPKT:
                                uuid = (SGSUUID) ois.readObject();
                                byte[] buff = new byte[ois.available()];
                                ois.read(buff, 0, buff.length);
                                doData(uuid, buff);
                                break;
                        }
                        ois.close();
                        bais.close();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }.start();
    }

    void doHeartbeat(SGSUUID uuid) {
        roster.put(uuid, System.currentTimeMillis());
    }

    public Set<SGSUUID> getRoster() {
        Iterator<Map.Entry<SGSUUID, Long>> i;
        for (i = roster.entrySet().iterator(); i.hasNext();) {
            Map.Entry<SGSUUID, Long> entry = i.next();
            if ((entry.getValue() + (HEARTBEATMILLIS * 2)) < System.currentTimeMillis()) { // expired
                i.remove();
            }
        }
        return roster.keySet();
    }

    public void sendData(byte[] buff) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] outbuff = null;
        try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeByte(OP_DATAPKT);
            oos.writeObject(myUUID);
            oos.write(buff);
            oos.flush();
            outbuff = baos.toByteArray();
            DatagramPacket pkt = new DatagramPacket(outbuff, 0, outbuff.length);
            ps.send(pkt);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void addListener(JRMSChannelRosterManagerListener l) {
        listeners.add(l);
    }

    void doData(SGSUUID uuid, byte[] buff) {
        fireDataArrived(uuid, buff);
    }

    private void fireDataArrived(SGSUUID uuid, byte[] buff) {
        for (JRMSChannelRosterManagerListener listener : listeners) {
            listener.pktArrived(uuid, buff);
        }
    }

    // test routine

    private static int dataPort = 6824;
    private static String addr = "224.100.100.224";

    public static void main(String[] args) {
        final Object printMutex = new Object();
        try {
            LRMPTransportProfile tp;
            InetAddress address = InetAddress.getByName(addr);
            tp = new LRMPTransportProfile(address, dataPort);
            tp.setMaxDataRate(100000);
            PrimaryChannelManager pcm = ChannelManagerFinder.getPrimaryChannelManager(null);
            Channel channel = pcm.createChannel();
            channel.setChannelName("SGS Shared Data Channel");
            channel.setApplicationName("SGS");
            channel.setTransportProfile(tp);
            channel.setAbstract("Used for coordinating shared data.");
            channel.setAdvertisingRequested(true);
            JRMSChannelRosterManager crm = new JRMSChannelRosterManager(channel);
            String intro = "Joining channel message from " + crm.myUUID
                    + " : Hello!";
            crm.sendData(intro.getBytes());
            crm.addListener(new JRMSChannelRosterManagerListener() {
                public void pktArrived(SGSUUID uuid, byte[] buff) {
                    synchronized (printMutex) {
                        System.out.println("MSG FROM " + uuid + ": "
                                + new String(buff));
                    }
                }
            });
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Set roster = crm.getRoster();
                synchronized (printMutex) {
                    System.out.print("Roster :");
                    for (Iterator i = roster.iterator(); i.hasNext();) {
                        System.out.print(i.next() + " ");
                    }
                    System.out.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
