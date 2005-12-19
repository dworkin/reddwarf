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
 * Lrmp.java - Light-weight Reliable Multicast Protocol (version 1).
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: April 23, 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.net.*;
import java.util.*;

/**
 * an implementation of the Light-weight Reliable Multicast Protocol which provides
 * point-to-multipoint reliable and ordered data delivery service. This object
 * works in multicast mode if the provided network address is a multicast group
 * address, or in unicast mode if it is a classical host IP address. The configuration
 * is set using LrmpProfile by which a number of transmission and reception parameters
 * can be adjusted. Not ordered but reliable packet delivery is currently
 * not implemented.
 * <p>
 * For performance reason, an application must not modify the packets received from
 * and sent to LRMP. These packets are kept in the cache for possible retransmissions.
 * If necessary, an application should make a local copy before the modification.
 * <p>
 * An application should implement the LrmpEventHandler interface for processing
 * data packets and events received from LRMP. This event handler interface is
 * also configured through LrmpProfile.
 * <p>
 * An application should set the data rate to a tolerable range. Lrmp keeps the data rate
 * between the minimum rate and the maximum rate while adapting to the available network
 * resources. Correct rate setting is very important for both reliability and congestion
 * control. It is advised not to use an aggressive data rate in a large scale.
 * <p>
 * Received data flows from different senders are distinguished by the sender's ID
 * and the network address. Under normal network conditions, Lrmp guarantees that
 * the data packets delivered to the application are in good order. If serious
 * network troubles make this guarantee impossible, Lrmp will notify the application
 * if there is a break in the data sequence through processEvent() of the event handler.
 * Upon this notification, the application may need to drop the current incomplete
 * object.
 * <p>
 * A LRMP session has a TTL value which is used to limit the scope of the session and
 * should be between 0 and 255. More meaningfully,
 * <ul>
 * <li>TTL 0, limit to the same machine.
 * <li>TTL 1, limit to the same subnet.
 * <li>TTL 15, limit to the same site.
 * <li>TTL 63, limit to the same region.
 * <li>TTL 127, world-wide.
 * </ul>
 * <p>
 * For better performance, it is advised to use a TTL value corresponding to the
 * scope of the session. A TTL value which is unnecessarily large may degrade
 * the performance.
 * <p>
 * The following is a simple example of how to use this object:
 * <pre>
 * import inria.net.lrmp.*;
 * public class Test implements LrmpEventHandler {
 * private Lrmp lrmp;
 * public Test(String group, int port, int ttl) {
 * LrmpProfile profile = new LrmpProfile();
 * profile.setEventHandler(this);
 * profile.minRate = 8;
 * profile.maxRate = 16;
 * try {
 * lrmp = new Lrmp(group, port, ttl, profile);
 * } catch (LrmpException e) {
 * System.exit(1);
 * }
 * lrmp.start();
 * }
 * public void quit() {
 * lrmp.stopSession();
 * }
 * public void sendTestData() {
 * LrmpPacket pack = new LrmpPacket();
 * int maxLen = pack.getMaxDataLength();
 * pack.setDataLength(maxLen);
 * try {
 * lrmp.send(pack);
 * } catch (LrmpException e) {
 * }
 * }
 * public void processData(LrmpPacket pack) {
 * System.out.println("got packet from " + pack.getSourceID() + "@" +
 * pack.getAddress());
 * System.out.println("buffer " + pack.getDataBuffer() +
 * " offset " + pack.getOffset() +
 * " length " + pack.getDataLength());
 * }
 * public void processEvent(int event, Object obj) {
 * switch (event) {
 * case LrmpEventHandler.UNRECOVERABLE_SEQUENCE_ERROR:
 * System.out.println("reception failure!");
 * break;
 * default:
 * break;
 * }
 * }
 * }
 * </pre>
 */
public class Lrmp {

    /**
     * the version of this LRMP implementation.
     */
    public final String Version = "LRMP-1.4.2";
    private LrmpImpl impl;

    /**
     * creates and joins an LRMP multicast session. The session will be carried on
     * the specified group address and the transport port.
     * @param addr the destination address.
     * @param port the port to use.
     * @param ttl the time-to-live value.
     * @param prof the profile to use.
     * @exception LrmpException is raised if there is an error in joining or
     * bad profile.
     */
    public Lrmp(InetAddress addr, int port, int ttl, 
                LrmpProfile prof) throws LrmpException {
        impl = new LrmpImpl(addr, port, ttl, prof);
    }

    /**
     * creates and joins an LRMP unicast session.
     * @param addr the destination address.
     * @param port the port to use.
     * @param prof profile to use.
     * @exception LrmpException is raised if there is an error in creating socket or
     * bad profile.
     */
    public Lrmp(InetAddress addr, int port, 
                LrmpProfile prof) throws LrmpException {
        this(addr, port, 0, prof);
    }

    /**
     * creates and joins an LRMP multicast session.
     * @param group the destination address.
     * @param port the port to use.
     * @param ttl the time-to-live value.
     * @param prof profile to use.
     * @exception LrmpException is raised if there is an error in joining or
     * bad profile.
     */
    public Lrmp(String group, int port, int ttl, 
                LrmpProfile prof) throws LrmpException {
        InetAddress iaddr;

        try {
            iaddr = InetAddress.getByName(group);
        } catch (UnknownHostException e) {
            throw new LrmpException(e.toString());
        }

        impl = new LrmpImpl(iaddr, port, ttl, prof);
    }

    /**
     * creates and joins an LRMP unicast session.
     * @param addr the destination address.
     * @param port the port to use.
     * @param prof profile to use.
     * @exception LrmpException is raised if there is an error in creating socket or
     * bad profile.
     */
    public Lrmp(String addr, int port, 
                LrmpProfile prof) throws LrmpException {
        this(addr, port, 0, prof);
    }

    /**
     * Starts the session. After started, the application can send and receive data
     * through this object.
     */
    public void start() {
        impl.startSession();
    }

    /**
     * Stops the session. After stopped, the application should not send data
     * to this object.
     */
    public void stop() {
        impl.stopSession();
    }

    /**
     * Starts the session.
     * @deprecated it is replaced by <code>start()</code>.
     */
    public void startSession() {
        impl.startSession();
    }

    /**
     * Stops the session.
     * @deprecated it is replaced by <code>stop()</code>.
     */
    public void stopSession() {
        impl.stopSession();
    }

    /**
     * Sets the profile. The configuration parameters will be reset using the
     * new profile.
     * @param prof the profile to use.
     * @exception LrmpException is raised if this is a bad profile.
     */
    public void setProfile(LrmpProfile prof) throws LrmpException {
        impl.setProfile(prof);
    }

    /**
     * Sets the scope value.
     */
    public void setTTL(int i) {
        impl.setTTL(i);
    }

    /**
     * Returns the destination address.
     */
    public InetAddress getAddress() {
        return impl.getAddress();
    }

    /**
     * Returns the port number.
     */
    public int getPort() {
        return impl.getPort();
    }

    /**
     * Returns the scope value.
     */
    public int getTTL() {
        return impl.getTTL();
    }

    /**
     * Returns the overall statistics.
     */
    public LrmpStats getLrmpStats() {
        return (LrmpStats) impl.getLrmpStats().clone();
    }

    /**
     * Returns the recovery domain statistics.
     * @param scope the domain scope.
     */
    public LrmpDomainStats getDomainStats(int scope) {
        LrmpDomainStats s = impl.getDomainStats(scope);

        if (s == null) {
            return null;
        } 

        return (LrmpDomainStats) s.clone();
    }

    /**
     * Returns the local user info.
     */
    public LrmpEntity whoami() {

        /* may need clone??? */

        return impl.whoami();
    }

    /**
     * Sends a data packet to the session. This method will block if the output queue
     * is full in Lrmp.
     * @param pack the packet to send.
     * @exception LrmpException is raised if the packet is too big.
     */
    public void send(LrmpPacket pack) throws LrmpException {
        if (pack.getDataLength() > pack.getMaxDataLength()) {
            throw new LrmpException("bad packet length");
        } 

        impl.send(pack);
    }

    /**
     * Flushes the output queue. This method returns only after all enqueued
     * packets are sent to the underlying network.
     */
    public void flush() {
        impl.flush();
    }

}

