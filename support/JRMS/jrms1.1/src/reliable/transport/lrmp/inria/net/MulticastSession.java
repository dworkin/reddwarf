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
 * MulticastSession.java - basic functionality of a Multicast Session.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 April 1997.
 * Updated: no.
 */
package inria.net;

import java.util.*;
import java.io.*;
import java.net.*;
import inria.util.Logger;
import inria.util.Utilities;

/**
 * an abstract class for managing a multicast/unicast session over DatagramSocket.
 */
abstract public class MulticastSession implements Runnable {
    protected int MaxPacketSize = 1024;
    protected int port;
    protected int ttl = 1;
    protected InetAddress inetAddr;

    /* can't reuse a socket for both sending and reception */

    protected DatagramSocket sock_in = null;
    protected DatagramSocket sock_out = null;
    protected int packets = 0;
    protected int bytes = 0;
    protected Thread thread = null;

    /**
     * creates a MulticastSession object.
     */
    protected MulticastSession() {}

    /**
     * starts the session.
     */
    public void start() {
        if (thread == null) {
            thread = new Thread(this);

            thread.setName(getClass().getName());
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        }
    }

    /**
     * stops the session.
     */
    public void stop() {

        /*
         * thread.stop does not return due to the following fact:
         * MulticastSession.run()->MulticastSession.parse()
         * ->high level callback->MulticastSession.stop().
         * don't use thread.stop() as in jdk1.2.
         */
        thread = null;

        if (inetAddr != null) {
            if (inetAddr.isMulticastAddress()) {
                try {
                    if (sock_in != null) {
                        ((MulticastSocket) sock_in).leaveGroup(inetAddr);
                    }
                    if (sock_out != null) {
                        ((MulticastSocket) sock_out).leaveGroup(inetAddr);
                    }
                } catch (IOException e) {
                    Logger.error(this, "failed to leave group", e);
                }
            }
            if (sock_in != null) {
                sock_in.close();

                sock_in = null;
            }
            if (sock_out != null) {
                sock_out.close();

                sock_out = null;
            }
        }
    }

    /**
     * creates MulticastSocket or DatagramSocket according to the class of the given
     * IP address.
     * @param addr the destination address.
     * @param port the port to use.
     * @exception IOException is raised if there is an error in creating socket.
     */
    protected void initialize(InetAddress addr, int port) throws IOException {
        inetAddr = addr;

        if (isMulticast(inetAddr)) {
            sock_in = new MulticastSocket(port);

            ((MulticastSocket) sock_in).joinGroup(inetAddr);

            sock_out = new MulticastSocket();

            ((MulticastSocket) sock_out).joinGroup(inetAddr);
        } else {
            sock_in = new DatagramSocket(port);
            sock_out = new DatagramSocket();
        }

        this.port = port;
    }

    /**
     * creates MulticastSocket or DatagramSocket according to the class of the given
     * IP address.
     * @param addr the destination address.
     * @param port the port to use.
     * @exception UnknownHostException is raised if bad address.
     * @exception IOException is raised if there is an error in creating socket.
     */
    protected void initialize(String addr, int port) 
            throws IOException, UnknownHostException {
        initialize(InetAddress.getByName(addr), port);
    }

    /**
     * sets the TTL for a multicast session.
     * @param t TTL.
     */
    public void setTTL(int t) {
        ttl = t;
    }

    /**
     * gets the TTL value.
     */
    public int getTTL() {
        return ttl;
    }

    /**
     * sends data to the session using the session TTL.
     * @param buff the data buffer.
     * @param len the data length in the buffer.
     */
    public synchronized void send(byte buff[], int len) {
        send(buff, len, ttl);
    }

    /**
     * sends data to the session using the provided TTL.
     * @param buff the data buffer.
     * @param len the data length in the buffer.
     * @param ttl the ttl value to use.
     */
    public synchronized void send(byte buf[], int len, int ttl) {
        if (false && drop()) {
            Logger.trace(this, "drop packet");

            return;
        }
        if (sock_out == null) {
            Logger.debug(this, "send: null socket");

            return;
        }

        DatagramPacket pack = new DatagramPacket(buf, len, inetAddr, port);

        try {
            if (inetAddr.isMulticastAddress()) {
                ((MulticastSocket) sock_out).send(pack, (byte) ttl);
            } else {
                sock_out.send(pack);

                /* in unicast, sent packets will not be received */

                packets++;
                bytes += pack.getLength();
            }
        } catch (Exception e) {
            Logger.error(this, "send error", e);
        }
    }

    /**
     * parses a received data packet. To be implemented by subclasses.
     * @param buff the data buffer.
     * @param datalen the data length in the buffer.
     * @param addr the source network address.
     * @retrun true if the buffer can be reused; false otherwise.
     */
    protected abstract boolean parse(byte buff[], int datalen, 
                                     InetAddress addr);

    /**
     * starts to receive packets from the session.
     */
    public void run() {
        if (Logger.debug) {
            Logger.debug(this, 
                         "started on " + inetAddr.getHostAddress() + "/" 
                         + port + "/" + ttl);
        } 

        byte buff[] = null;
        Thread thisThread = Thread.currentThread();

        while (thread == thisThread) {
            if (sock_in == null) {
                Logger.debug(this, "receive: null socket");

                break;
            }
            if (buff == null) {
                buff = new byte[MaxPacketSize];
            } 

            DatagramPacket packet = new DatagramPacket(buff, buff.length);

            try {
                sock_in.receive(packet);
            } catch (IOException e) {

                /*
                 * sometimes get Interrupted system call (on linux), ignore.
                 */
                if (Logger.debug) {
                    Logger.debug(this, "receive: " + e.getMessage());
                } 

                continue;
            }

            if (false && drop()) {
                Logger.trace(this, "drop packet");

                continue;
            }

            packets++;
            bytes += packet.getLength();

            /*
             * only reuse the buffer, DatagramPacket cann't be reused due to some
             * problems in DatagramSocket.
             */
            if (!parse(packet.getData(), packet.getLength(), 
                       packet.getAddress())) {
                buff = null;
            } 
        }

        if (Logger.debug) {
            Logger.debug(this, "thread stopped");
        } 

        thread = null;
    }

    /**
     * returns true if the address is in class D.
     * @param addr address to check.
     */
    public static boolean isMulticast(InetAddress addr) {
        byte baddr[] = addr.getAddress();

        return ((baddr[0] & 0xf0) == 0xe0);
    }

    /*
     * just for simulation.
     */
    static Random rand = new Random(0xafcd3a015e90L);

    private boolean drop() {
        if (rand.nextDouble() < 0.2) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * returns the total number of packets received.
     */
    public int packets() {
        return packets;
    }

    /**
     * returns the total number of bytes received.
     */
    public int bytes() {
        return bytes;
    }
}
