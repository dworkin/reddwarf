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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DistributedMutexMgrImpl
        implements DistributedMutexMgr, RMCListener
{
    transient static SecureRandom random = null;
    static final byte OP_ANNOUNCE = 0;
    static final byte OP_INTRODUCE = 1;
    static final byte OP_REQLOCK = 2;
    static final byte OP_ACKLOCK = 3;
    static final byte OP_NAKLOCK = 4;
    static final byte OP_RELEASE = 5;
    static final byte OP_DATA = 6;

    MutexLists lists = new MutexLists();
    ReliableMulticaster rmc;
    Set<SGSUUID> peerSet = new TreeSet<SGSUUID>();
    SGSUUID uuid = new StatisticalUUID();

    private List listeners = new ArrayList();
    private static final boolean DEBUG = false;
    volatile boolean done = false;
    private static final long TIMEOUT = 3000;

    public DistributedMutexMgrImpl(ReliableMulticaster reliable) {
        rmc = reliable;
        rmc.setListener(this);
        if (random == null) {
            try {
                random = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException ex) {
                ex.printStackTrace();
            }
        }
        rmc.setListener(this);
        startWatchdog();
        sendAnnounce();
    }

    public void startWatchdog() {
        (new Thread() {
            public void run() {
                while (!done) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    long currentTime = System.currentTimeMillis();
                    synchronized (lists) {
                        for (Iterator i = lists.pendingMutexes.iterator(); i.hasNext();) {
                            DistributedMutexImpl mutex = (DistributedMutexImpl) i.next();
                            if (mutex.hasExpired(currentTime)) {
                                // remove remainign peers from current
                                // peerset
                                Set<SGSUUID> leftovers =
                                    CollectionUtils.minus(peerSet, mutex.acksReceived);
                                peerSet.removeAll(leftovers);
                                i.remove();
                                mutex.ackTest(peerSet);
                            }
                        }
                        for (Iterator i = lists.blockedMutexes.iterator(); i.hasNext();) {
                            DistributedMutexImpl mutex = (DistributedMutexImpl) i.next();
                            if (mutex.hasExpired(currentTime)) {
                                peerSet.remove(mutex.getBlockedOn());
                                doRelease(mutex);
                            }
                        }
                    }
                }
            }
        }).start();
    }

    public DistributedMutex getMutex(String name) {
        return new DistributedMutexImpl(name, this);
    }

    public void releaseMutex(DistributedMutexImpl mutex) {
        sendRelease(mutex.getID());
    }

    public void lockMutex(DistributedMutex mutex) throws InterruptedException {
        if (peerSet.size() == 0) {
            if (DEBUG) {
                // System.out.println("No peers.");

            }
            return;
        }
        synchronized (lists) {
            lists.pendingMutexes.add(mutex);
        }
        ((DistributedMutexImpl) mutex).clearAcks();
        long tieBreaker = random.nextLong();
        ((DistributedMutexImpl) mutex).tieBreaker = tieBreaker;
        ((DistributedMutexImpl) mutex).resetTimeout(TIMEOUT);
        sendLockReq(mutex.getID(), tieBreaker);
        while (((DistributedMutexImpl) mutex).remoteAck == false) {
            synchronized (mutex) {
                try {
                    mutex.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                if (mutex.wasInterrupted()) {
                    throw new InterruptedException();
                }
            }
        }
    }

    private void sendAnnounce() {
        if (DEBUG) {
            System.out.println("Send announce.");
        }
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_ANNOUNCE);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void sendLockReq(Object mutexid, long tieBreaker) {
        if (DEBUG) {
            System.out.println("Send lock req");
        }
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_REQLOCK);
            oos.writeObject(mutexid);
            oos.writeLong(tieBreaker);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void sendLockNAK(Object mutexid, SGSUUID senderID) {
        if (DEBUG) {
            System.out.println("Send lock naq.");
        }
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_NAKLOCK);
            oos.writeObject(mutexid);
            oos.writeObject(senderID);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void sendLockACK(Object mutexid, SGSUUID senderID) {
        if (DEBUG) {
            System.out.println("Send lock ack.");
        }
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_ACKLOCK);
            oos.writeObject(mutexid);
            oos.writeObject(senderID);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void sendIntroduce() {
        if (DEBUG) {
            System.out.println("Send introduce.");
        }
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_INTRODUCE);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void sendRelease(Object mutexid) {
        if (DEBUG) {
            System.out.println("Send release.");
        }
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_RELEASE);
            oos.writeObject(mutexid);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

    }

    public void pktArrived(DatagramPacket pkt) {
        ObjectInputStream ois;
        ByteArrayInputStream bais = new ByteArrayInputStream(pkt.getData(),
                pkt.getOffset(), pkt.getLength());
        try {
            ois = new ObjectInputStream(bais);
            SGSUUID senderID = (SGSUUID) ois.readObject();
            byte opcode = ois.readByte();
            // System.out.println("Got packet, op =" + opcode);
            switch (opcode) {
                case OP_ANNOUNCE:
                    peerSet.add(senderID);
                    sendIntroduce();
                    break;
                case OP_INTRODUCE:
                    peerSet.add(senderID);
                    break;
                case OP_REQLOCK:
                    Object mutexid = ois.readObject();
                    MutexLookup lookuprec = new MutexLookup(mutexid);
                    long tieBreaker = ois.readLong();
                    synchronized (lists) {
                        if (lists.heldMutexes.contains(lookuprec)) {
                            sendLockNAK(mutexid, senderID);
                        } else if (lists.pendingMutexes.contains(lookuprec)) {
                            int idx = lists.pendingMutexes.indexOf(lookuprec);
                            if (idx >= 0) {
                                DistributedMutexImpl localMutex = (DistributedMutexImpl) lists.pendingMutexes.get(idx);
                                if (tieBreaker < localMutex.tieBreaker) {
                                    doLockBlocked(senderID, localMutex);
                                    sendLockACK(mutexid, senderID);
                                } else {
                                    sendLockNAK(mutexid, senderID);
                                }
                            }
                        } else {
                            sendLockACK(mutexid, senderID);
                        }
                    }
                    break;
                case OP_ACKLOCK:
                    mutexid = ois.readObject();
                    lookuprec = new MutexLookup(mutexid);
                    SGSUUID ownerid = (SGSUUID) ois.readObject();
                    if (uuid.equals(ownerid)) { // ack is for us
                        int idx = lists.pendingMutexes.indexOf(lookuprec);
                        if (idx >= 0) {
                            DistributedMutexImpl mutex = (DistributedMutexImpl) lists.pendingMutexes.get(idx);
                            mutex.lockAck(senderID, peerSet);
                        }
                    }
                    break;
                case OP_NAKLOCK:
                    mutexid = ois.readObject();
                    lookuprec = new MutexLookup(mutexid);
                    ownerid = (SGSUUID) ois.readObject();
                    if (uuid.equals(ownerid)) { // nak is for us
                        int idx = lists.pendingMutexes.indexOf(lookuprec);
                        if (idx >= 0) {
                            DistributedMutexImpl mutex = (DistributedMutexImpl) lists.pendingMutexes.get(idx);
                            mutex.lockNAK(senderID);
                            doLockBlocked(senderID, mutex);
                        }
                    }
                    break;
                case OP_RELEASE:
                    mutexid = ois.readObject();
                    lookuprec = new MutexLookup(mutexid);
                    int idx = lists.blockedMutexes.indexOf(lookuprec);
                    if (idx >= 0) {
                        DistributedMutexImpl mutex = (DistributedMutexImpl) lists.blockedMutexes.get(idx);
                        doRelease(mutex);
                    }
                    break;
                case OP_DATA:
                    int bufflen = ois.readInt();
                    byte[] buff = new byte[bufflen];
                    ois.read(buff);
                    doData(buff);
                    break;
            }
        }

        catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    void doRelease(DistributedMutexImpl mutex) {
        synchronized (lists) {
            mutex.clearAcks(); // reset for new req
           
            // tiebreaker, so it doesnt get stuck with a bad value
            mutex.tieBreaker = random.nextLong();

            lists.blockedMutexes.remove(mutex);
            lists.pendingMutexes.add(mutex);
        }
        sendLockReq(mutex.id, mutex.tieBreaker);
    }

    private void doLockBlocked(SGSUUID id, DistributedMutexImpl localMutex) {
        localMutex.setBlockedOn(id);
        synchronized (lists) {
            lists.pendingMutexes.remove(localMutex);
            lists.blockedMutexes.add(localMutex);
        }
    }

    public void addListener(DistributedMutexMgrListener l) {
        listeners.add(l);
    }

    public void doData(byte[] buff) {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            ((DistributedMutexMgrListener) i.next()).receiveData(buff);
        }
    }

    public void sendData(byte[] buff) {
        ObjectOutputStream oos;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(uuid);
            oos.writeByte(OP_DATA);
            oos.writeInt(buff.length);
            oos.write(buff);
            oos.flush();
            byte[] outbuff = baos.toByteArray();
            DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
            rmc.send(outpkt);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    public boolean hasPeers() {
        return (peerSet.size() > 0);
    }

    public void interruptMutex(DistributedMutexImpl mutex) {
        synchronized (mutex) {
            mutex.notifyAll(); // wake them up to check the interrupt
                                // flag
        }
    }
}

class MutexLists {
    List pendingMutexes = new LinkedList();
    List heldMutexes = new LinkedList();
    List blockedMutexes = new LinkedList();
}

class MutexLookup {
    Object id;

    public MutexLookup(Object obj) {
        id = obj;
    }

    public boolean equals(Object obj) {
        return id.equals(((DistributedMutex) obj).getID());
    }

    public int compareTo(Object obj) {
        return ((Comparable) id).compareTo(((DistributedMutex) obj).getID());
    }
}