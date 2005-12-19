/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * UMSender
 * 
 * Module Description:
 * 
 * The class implements the Sender thread for the Unreliable
 * multicast transport protocol. It implements the data rate scheme
 * specified in the transport profile.
 */
package com.sun.multicast.reliable.transport.um;

import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.util.Vector;

/**
 * The UMSender class regulates the amount of data sent into the network.
 * The upper layers place DatagramPackets onto a queue for processing and
 * return. This class spawns a thread to send the data in the send queue
 * on the network at a specified rate. If the queue fills up, the upper
 * layers are stalled while the queue is being serviced.
 */
class UMSender implements Runnable {
    private static final int MAX_QUEUE = 16;
    private MulticastSocket ms = null;
    private Thread thread = null;
    private Vector transmitQueue = new Vector(MAX_QUEUE);
    private long transmitInterval = 10;
    private long bytesSent = 0;
    private long packetsSent = 0;
    private long dataRate = 64000;      /* Bytes/second */

    /**
     * This constructor creates the UMSender class. It links the multicast
     * socket to the UMSender thread and saves the current system time.
     * 
     * @param ms the multicast socket that data wil be sent on.
     * @param dataRate is the desired rate that packets are sent. This value
     * is specified as bytes/second.
     */
    public UMSender(MulticastSocket ms, long dataRate) {
        super();

        this.ms = ms;
        this.dataRate = dataRate;
    }

    /**
     * The insqueue method inserts DatagramPackets onto a Vector for
     * later processing. If the queue is full, the caller is stalled.
     * 
     * @param dp the DatagramPacket to send.
     */
    public synchronized void insqueue(DatagramPacket dp, byte ttl) {
        while (transmitQueue.size() == MAX_QUEUE) {
            try {
                System.out.println("Queue Full....");
                this.wait();
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }

        transmitQueue.addElement(new UMDataBlock(dp, ttl));
        start();
    }

    /**
     * The insqueue method inserts DatagramPackets onto a Vector for
     * later processing. If the queue is full, the caller is stalled.
     * 
     * @param dp the DatagramPacket to send.
     */
    public synchronized void insqueue(DatagramPacket dp) {
        while (transmitQueue.size() == MAX_QUEUE) {
            try {
                System.out.println("Queue Full....");
                this.wait();
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }

        transmitQueue.addElement(new UMDataBlock(dp));
        start();
    }

    /**
     * This method indicates whether there are any packets left on the queue
     * or not.
     * 
     * @return true if the send queue is empty.
     */
    public synchronized boolean isQueueEmpty() {
        return transmitQueue.isEmpty();
    }

    /**
     * The Start routine is called when a packet is placed on the send queue.
     * It starts the transmitter thread at MAX_PRIORITY. If the thread is
     * already running this method does nothing.
     */
    public void start() {
        if (thread == null) {
            thread = new Thread(this);

            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        }
    }

    /**
     * This is the heart of the transmitter. The thread.start() call above
     * calls this method to pick packets off the transmitQueue. The packet is
     * sent on the multicast socket. The interpacket delay is computed and
     * this thread stalls for that period of time. When the timer expires,
     * the next packet is removed from the queue. If the queue is empty, this
     * thread will die.
     */
    public synchronized void run() {
        long sendStart = 0;
        long sendDelta = 0;

        while (!transmitQueue.isEmpty()) {
            sendStart = System.currentTimeMillis();

            UMDataBlock db = (UMDataBlock) transmitQueue.firstElement();

            transmitQueue.removeElement(db);

            try {
                if (db.getTTL() == 0) {
                    ms.send(db.getDatagramPacket());
                } else {
                    ms.send(db.getDatagramPacket(), db.getTTL());
                }

                computeRate(db.getDatagramPacket());

                if ((transmitInterval - sendDelta) > 0) {
                    Thread.sleep(transmitInterval - sendDelta);
                }
            } catch (Exception e) {
                System.out.println(e);
            }

            /*
             * The sendDelta is an attempt to factor out the
             * the overhead of processing the packet from the
             * wait interval. The overhead is computed and subtracted
             * from the transmitInterval.
             */
            sendDelta = System.currentTimeMillis() - sendStart 
                        - transmitInterval;
        }

        this.notify();

        this.thread = null;
    }

    /**
     * The computeRate method calculates the next wait interval in an
     * attempt to achieve the desired data rate. The interval is computed
     * as follows:
     * 
     * timeout = (packetsize * 1000) / dataRate
     * 
     * The resulting timeout is in milliseconds. This accounts for packets
     * that are different sizes or the same size. If the desired rate changes,
     * the new data rate changes also.
     * 
     * @param dp the current datagram packet to send.
     */
    public void computeRate(DatagramPacket dp) {
        bytesSent += dp.getLength();
        packetsSent++;

        if (dataRate != 0) {
            transmitInterval = (dp.getLength() * 1000) / dataRate;
        } else {
            transmitInterval = 0;
        }
    }

    /**
     * @return the dataRate.
     */
    public long getDataRate() {
        return dataRate;
    }

    /**
     * Set the data rate - bytes/second
     * 
     * @param dataRate rate to send data in bytes / second
     */
    public void setDataRate(long dataRate) {
        this.dataRate = dataRate;
    }

}

