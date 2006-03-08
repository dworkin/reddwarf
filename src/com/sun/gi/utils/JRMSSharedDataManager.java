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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;

public class JRMSSharedDataManager
        implements JRMSChannelRosterManagerListener, SharedDataManager
{
    private static InetAddress address = null;
    private static int dataPort = 6824;
    private static String addr = "224.100.100.224";
    private PrimaryChannelManager pcm;
    private Channel channel;
    private SGSUUID guid;
    private JRMSChannelRosterManager rosterManager;

    // op codes
    static final int OP_LOCK_REQ = 0;
    static final int OP_LOCK_ACK = 1;
    static final int OP_LOCK_NAK = 2;
    static final int OP_LOCK_RELEASE = 3;
    static final int OP_DATA_REQUEST = 4;
    static final int OP_DATA_ASSERT = 5;

    private Map<String, JRMSSharedObjectBase> dataMap =
        new HashMap<String, JRMSSharedObjectBase>();

    public JRMSSharedDataManager() {
        guid = new StatisticalUUID();
        try {
            LRMPTransportProfile tp;
            address = InetAddress.getByName(addr);

            tp = new LRMPTransportProfile(address, dataPort);
            tp.setMaxDataRate(100000);
            pcm = ChannelManagerFinder.getPrimaryChannelManager(null);
            channel = pcm.createChannel();
            channel.setChannelName("SGS Shared Data Channel");
            channel.setApplicationName("SGS");
            channel.setTransportProfile(tp);
            channel.setAbstract("Used for coordinating shared data.");
            channel.setAdvertisingRequested(true);
            rosterManager = new JRMSChannelRosterManager(channel);
            rosterManager.addListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SharedMutex getSharedMutex(String name) {
        JRMSSharedMutex mutex = new JRMSSharedMutex(this, name);
        dataMap.put(name, mutex);
        return mutex;
    }

    public void pktArrived(SGSUUID uuid, byte[] buff) {
        int strlen = 0;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(buff);
            DataInputStream dis = new DataInputStream(bais);
            strlen = dis.readInt();
            byte[] strbuff = new byte[strlen];
            dis.read(strbuff);
            String name = new String(strbuff);
            JRMSSharedObjectBase obj = dataMap.get(name);
            int op = dis.readByte();
            // System.out.println("recieved op = "+op);
            switch (op) {
                case OP_LOCK_REQ:
                    if (obj == null) { // we dont have this lock so ACK
                                        // it
                        sendLockAck(name);
                    } else {
                        obj.lockReq(uuid);
                    }
                    break;
                case OP_LOCK_ACK:
                    if (obj != null) {
                        obj.lockAck(uuid);
                    }
                    break;
                case OP_LOCK_NAK:
                    if (obj != null) {
                        obj.lockNak(uuid);
                    }
                    break;
                case OP_LOCK_RELEASE:
                    if (obj != null) {
                        obj.lockRelease(uuid);
                    }
                    break;
                case OP_DATA_REQUEST:
                    if (obj != null) {
                        obj.dataRequest(uuid);
                    }
                    break;
                case OP_DATA_ASSERT:
                    if (obj != null) {
                        byte[] databuff = new byte[dis.readInt()];
                        dis.read(databuff);
                        obj.dataAssertion(uuid, databuff);
                    }
                    break;
            }
            dis.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public SGSUUID getUUID() {
        return guid;
    }

    public SharedData getSharedData(String name) {
        name = "__SHARED_" + name;
        JRMSSharedData sdata = new JRMSSharedData(this, name);
        dataMap.put(name, sdata);
        sdata.initialize();
        return sdata;
    }

    public void sendData(String name, byte[] buff) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(name.length());
            dos.writeBytes(name);
            dos.writeByte(JRMSSharedDataManager.OP_DATA_ASSERT);
            dos.writeInt(buff.length);
            dos.write(buff);
            dos.flush();
            byte[] outbuff = baos.toByteArray();
            dos.close();
            rosterManager.sendData(outbuff);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendLockReq(String name) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(name.length());
            dos.writeBytes(name);
            dos.writeByte(OP_LOCK_REQ);
            dos.flush();
            byte[] outbuff = baos.toByteArray();
            dos.close();
            rosterManager.sendData(outbuff);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendLockNak(String name) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(name.length());
            dos.writeBytes(name);
            dos.writeByte(OP_LOCK_NAK);
            dos.flush();
            byte[] outbuff = baos.toByteArray();
            dos.close();
            rosterManager.sendData(outbuff);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void sendLockAck(String name) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(name.length());
            dos.writeBytes(name);
            dos.writeByte(OP_LOCK_ACK);
            dos.flush();
            byte[] outbuff = baos.toByteArray();
            dos.close();
            rosterManager.sendData(outbuff);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendLockRelease(String name) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(name.length());
            dos.writeBytes(name);
            dos.writeByte(OP_LOCK_RELEASE);
            dos.flush();
            byte[] outbuff = baos.toByteArray();
            dos.close();
            rosterManager.sendData(outbuff);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Set<SGSUUID> getRoster() {
        return rosterManager.getRoster();
    }

    public long getRosterTimeout() {
        return JRMSChannelRosterManager.HEARTBEATMILLIS;
    }

    public void requestData(String name) {
        // System.out.println("Sending data request.");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(name.length());
            dos.writeBytes(name);
            dos.writeByte(OP_DATA_REQUEST);
            dos.flush();
            byte[] outbuff = baos.toByteArray();
            dos.close();
            rosterManager.sendData(outbuff);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
