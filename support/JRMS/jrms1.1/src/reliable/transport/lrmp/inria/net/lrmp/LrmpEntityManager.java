/*
 * COPYRIGHT 1995 BY: MASSACHUSETTS INSTITUTE OF TECHNOLOGY (MIT), INRIA
 * 
 * This W3C software is being provided by the copyright holders under the
 * following license. By obtaining, using and/or copying this software, you
 * agree that you have read, understood, and will comply with the following
 * terms and conditions:
 * 
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose and without fee or royalty is hereby granted,
 * provided that the full text of this NOTICE appears on ALL copies of the
 * software and documentation or portions thereof, including modifications,
 * that you make.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS MAKE NO
 * REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT
 * NOT LIMITATION, COPYRIGHT HOLDERS MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE
 * SOFTWARE OR DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS,
 * COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. COPYRIGHT HOLDERS WILL BEAR NO
 * LIABILITY FOR ANY USE OF THIS SOFTWARE OR DOCUMENTATION.
 * 
 * The name and trademarks of copyright holders may NOT be used in advertising
 * or publicity pertaining to the software without specific, written prior
 * permission. Title to copyright in this software and any associated
 * documentation will at all times remain with copyright holders.
 */

/*
 * LrmpEntityManager.java - Entity Manager for LRMP.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 April 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.net.*;
import java.util.*;
import inria.util.Logger;
import inria.util.EntityTable;
import inria.util.Utilities;
import inria.util.NTP;

/**
 * LRMP entity manager. It keeps track of all LRMP senders, and some receivers
 * if there are free space.
 */
final class LrmpEntityManager {

    /* maximum number of entities to maintain */

    private static final int maxSrc = 128;

    /* table of senders and receivers */

    private EntityTable entities;

    /* local entity */

    private LrmpSender whoami;

    /* silence time for drop */

    protected static final int rcvDropTime = 60000;
    protected static final int sndDropTime = 600000;
    protected LrmpProfile profile;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @see
     */
    LrmpEntityManager() {
        entities = new EntityTable();

        int i = allocateID();
        int initSeqno = 0;

        while (initSeqno <= 0) {
            initSeqno = Utilities.getRandomInteger() & 0xffff;
        }

        whoami = new LrmpSender(i, Utilities.getLocalHost(), initSeqno);

        if (Logger.debug) {
            Logger.debug(this, 
                         "local user=" + whoami.toString() + " seqno=" 
                         + whoami.expected());
        } 

        add(whoami);
    }

    /*
     * allocate an entity id.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    private int allocateID() {
        int i = Utilities.getRandomInteger();

        return i;
    }

    /**
     * gets the estimated number of attendees in the session. The local user is counted.
     */
    public int getNumberOfEntities() {
        return entities.size();
    }

    /**
     * gets the entity from srcId.
     * @param csrc the entity Id of attendee.
     */
    public LrmpEntity get(int srcId) {
        return (LrmpEntity) entities.getEntity(srcId);
    }

    /**
     * gets the list of entities.
     */
    public Vector getEntities() {
        return entities;
    }

    /**
     * If the entity is not registered, check if an entity from the same
     * address is registered and not active. If true, treat them as
     * the same entity.
     * Otherwise, check the address matches. If not match, and the
     * registered entity is not active, reuse this one.
     * @return value:
     * entity reference if OK,
     * null if conflict.
     */
    public LrmpEntity lookup(int srcId, InetAddress netaddr) {
        LrmpEntity s = (LrmpEntity) entities.getEntity(srcId);

        if (s != null) {

            /*
             * check for a srcId conflict.
             */
            if (!s.getAddress().equals(netaddr)) {

                /*
                 * if the registered is a sender, reject the new one,
                 * because a sender may keep silent during a period of time
                 * and becomes active again.
                 */
                if (s instanceof LrmpSender) {
                    return null;
                } 

                /*
                 * if the registered entity is still active, drop new entity.
                 * otherwise reuse it for new entity.
                 */
                long silence = System.currentTimeMillis() 
                               - s.getLastTimeHeard();

                if (silence < rcvDropTime) {
                    return null;
                } 

                s.setAddress(netaddr);
                s.reset();
            }

            return s;
        }

        /*
         * find duplicate, i.e., at th same net address, because an entity
         * may rejoined the session.
         */
        for (int i = entities.size() - 1; i >= 0; i--) {
            s = (LrmpEntity) entities.elementAt(i);

            if (s != whoami &&!(s instanceof LrmpSender)) {
                if (s.getAddress().equals(netaddr)) {
                    long silence = System.currentTimeMillis() 
                                   - s.getLastTimeHeard();

                    if (silence >= rcvDropTime) {
                        remove(s);
                        s.setID(srcId);
                        add(s);
                        s.reset();

                        return s;
                    }
                }
            }
        }

        s = new LrmpEntity(srcId, netaddr);

        if (Logger.debug) {
            Logger.debug(this, "new entity: " + s);
        } 

        add(s);

        return s;
    }

    /**
     * checks entity table to find one matching src id for data packets,
     * provided that prior to any data reception LRMP control packets should
     * be heard first.
     */
    public LrmpEntity demux(int srcId, InetAddress netaddr) {
        LrmpEntity s = (LrmpEntity) entities.getEntity(srcId);

        if (s == null) {
            return null;
        } 
        if (!s.getAddress().equals(netaddr)) {
            return null;
        } 

        return s;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void clear() {
        entities.removeAllElements();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void dump() {
        for (int i = entities.size() - 1; i >= 0; i--) {
            LrmpEntity s = (LrmpEntity) entities.elementAt(i);

            Logger.debug(this, s.toString());
        }
    }

    /**
     * drops old recorded entities.
     */
    public void prune() {
        prune(rcvDropTime);
    }

    /**
     * drops old recorded entities.
     */
    public void prune(int maxSilence) {
        long now = System.currentTimeMillis();

        for (int i = entities.size() - 1; i >= 0; i--) {
            LrmpEntity s = (LrmpEntity) entities.elementAt(i);

            if (s != whoami) {
                int silence = (int) (now - s.getLastTimeHeard());

                if (silence >= sndDropTime) {
                    remove(s);
                } else if (!(s instanceof LrmpSender) 
                           && silence >= maxSilence) {
                    remove(s);
                } 
            }
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public LrmpSender whoami() {
        return whoami;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param srcId
     * @param netaddr
     * @param seqno
     *
     * @return
     *
     * @see
     */
    public LrmpSender lookupSender(int srcId, InetAddress netaddr, 
                                   long seqno) {
        LrmpSender sender;
        LrmpEntity e = demux(srcId, netaddr);

        if (e == null) {
            sender = new LrmpSender(srcId, netaddr, seqno);

            sender.initCache(profile.rcvWindowSize);
            add(sender);
        } else if (!(e instanceof LrmpSender)) {
            sender = new LrmpSender(srcId, netaddr, seqno);

            sender.initCache(profile.rcvWindowSize);
            remove(e);
            add(sender);
        } else {
            sender = (LrmpSender) e;
        }

        return sender;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     *
     * @see
     */
    protected void add(LrmpEntity s) {
        if (entities.size() > maxSrc) {
            for (int maxSilence = rcvDropTime; entities.size() > maxSrc; ) {
                prune(maxSilence);

                if (maxSilence > 10000) {
                    maxSilence -= 10000;
                } else {
                    break;
                }
            }
        }

        entities.addEntity(s);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     *
     * @see
     */
    protected void remove(LrmpEntity s) {
        if (s != whoami) {
            entities.removeEntity(s);

            if (s instanceof LrmpSender) {
                if (profile.handler != null) {
                    profile.handler.processEvent(
			LrmpEventHandler.END_OF_SEQUENCE, s);
                } 
            }
        }
    }

}

