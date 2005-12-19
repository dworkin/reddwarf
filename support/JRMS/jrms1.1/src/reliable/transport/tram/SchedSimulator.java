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
 * SchedSimulator.java
 * 
 * Module Description: 
 * 
 * This module is a standalone simulator developed to study the
 * behaviour of different dispatching algorithms. The scheduler
 * implements 4 different algorithms - DT0,DT1,DT2 & AT0.
 */

import java.net.DatagramPacket;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.io.*;
import java.net.*;

/**
 * The SchedSimulator assumes that there is always a packet to send.
 * The typical routines - getPacket(), that fetch packets from the 
 * application's output queue and the 'sendPacket()' routine that 
 * enqueues packet to the network interface side, are simulated by
 * getPacket() and sendPacket() routines. The simulated routines
 * tries to account for various actions that are typically done(but
 * are nowhere close to the actual one. We need to work on this 
 * make it as close to reality as possible).
 */
public class SchedSimulator extends Thread {

    public static final int UNKNOWN = 0;
    public static final int DT0 = 1;
    public static final int DT1 = 2;
    public static final int DT2 = 3;
    public static final int AT0 = 4;
    public static final int DT3 = 5;
    public static final int AT1 = 6;


    private static final String name = "TRAM SchedSimulator";
    private boolean done = false;
    private int packetSize = 1400;
    private int dataRate = 10000;
    private int algorithm = UNKNOWN;
    private InetAddress sendAddress = null;
    private int numPackets = 0;
    private int burstRate = 100000;
    private int arrivalModel = 0;

    private int  sendPort = 4321;

    // Statistics gathering.
    private long sessionStartTime = 0;
    private long bytesSent = 0;
    private long pktsSent = 0;
    private long sessionEndTime = 0;
    private long totalSleep = 0;
    private long totalActualSleep = 0;
    

    /*
     * Constructor for the SchedSimulator class. Set the thread
     * name and the daemon flag. Initializes key simulation parameters
     * with the passed in values.
     */

    public SchedSimulator(String algo, int dRate, int pktSize, int numPkts,
    		int bRate, int aModel) {
        super(name);

	// System.out.println("In SchedSimulator");
        this.setDaemon(true);
	try {
	    sendAddress = InetAddress.getByName("224.10.10.20");
	} catch (Exception e) {
	    return;
	}
	algorithm = getAlgoFromString(algo);
	if ((algorithm <= UNKNOWN) || (algorithm > AT1) ||
	    (dRate <= 0) ||
	    (pktSize <= 0) ||
	    (numPkts <= 0) ||
	    (aModel < 0) ||
	    (bRate < dRate)) {
	    System.out.println("Invalid parameters.... Exiting");
	    return;
	}
	dataRate = dRate;
	packetSize = pktSize;
	numPackets = numPkts;
	burstRate = bRate;
	arrivalModel = aModel;
	this.start();
    }

    /*
     * the run loop. Based on the initialized parameters, the relavent
     * algorithm is run till the termination condition is reached. The
     * termination condition is currently the number of packets
     * sent.
     */

    public void run() {

        /*
         * Wait till the Output database is available if currently not
         * available.
         */
	// System.out.print("In Run Loop");
	switch (algorithm) {
	  case DT0:
	    runDT0Algorithm();
	    break;

	  case DT1:
	    runDT1Algorithm();
	    break;

	  case DT2:
	    runDT2Algorithm();
	    break;
	    
	  case AT0:
	    runAT0Algorithm();
	    break;
	  
	  case DT3:
	    runDT3Algorithm();
	    break;
	    
	  case AT1:
	    runAT1Algorithm();
	    break;
	    
	  default:
	    System.out.println("Unknown Algorithm.... exiting");
	    break;
	}
	
    }
    /*
     * Test program to run the simulator. The test program takes in the
     * required initialization parameters as command line input. The test
     * program spawns the simulator and 'hangs' around for the schedular
     * to complete. The test program exists after simulator exists. 
     */
    public static void main(String args[]) {
	if (args.length < 4) {
            System.out.println("Usage: java SchedSimulator algorithm" +
			 "[DT0/DT1/DT2/AT0] dataRate packetSize numPackets");
            System.exit(1);
        }
	int dataRate = Integer.parseInt(args[1]);
	int packetSize = Integer.parseInt(args[2]);
	int numPackets = Integer.parseInt(args[3]);
	int burstRate = Integer.parseInt(args[4]);
	int arrivalModel = Integer.parseInt(args[5]);
	SchedSimulator ss = new SchedSimulator(args[0], dataRate,
			packetSize, numPackets, burstRate, arrivalModel);
	/*
	 * Now wait for the simulator to complete. Instead of waiting
	 * the test program could be extended(in future) to accept some
	 * command line inputs to dynamically change the schedular
	 * behaviour. 
	 */
	while (ss.isAlive() == true) {
	    try {
		Thread.sleep(1000);
	    } catch (Exception e) {
	    }
	}
    }
    /*
     * runDT0Algorithm()
     * Brief description of this algorithm: To be filled in
     */
    private void runDT0Algorithm() {
	// System.out.println("Running DT0 Algorithm");
	done = false;
	long start = System.currentTimeMillis();
	sessionStartTime = start;
	long sendTime = 0;
	pktsSent = 0;
	while (pktsSent < numPackets) {
	    DatagramPacket packet = getPacket();
	    sendPacket(packet);
	    pktsSent++;
	    int size = packet.getLength();
	    bytesSent += size;
	    long transmitTime = (size * 1000) / dataRate;
	    long sleepTime = transmitTime - sendTime;
	    // System.out.println("sleep = " + sleepTime);
	    if (sleepTime > 0) {
		try {
		    totalSleep += sleepTime;
		    long t1 = System.currentTimeMillis();
		    sleep(sleepTime);
		    long actualSleep = System.currentTimeMillis() - t1;
		    // System.out.println("actually slept "+ actualSleep);
		    totalActualSleep += actualSleep;
		} catch (InterruptedException ie) {
		}
	    } else {
	        sleepTime = 0;
		// accounting.... never slept in this case.
	    }

	    long end = System.currentTimeMillis();
	    // sendTime = end - start - transmitTime;
	    sendTime = end - start - sleepTime;
	    // System.out.println("start, end, send times: "+start+
	    // " "+end+" "+sendTime);
	    start = end;
	    if (sendTime < 0)
	        sendTime = 0;
	}
	sessionEndTime = System.currentTimeMillis();
	printStats();

    }

    /*
     * Implementation of DT1 Algorithm
     */
    private void runDT1Algorithm() {
	done = false;
	long start = System.currentTimeMillis();
	sessionStartTime = start;
	long lostTime = 0;
	long sendTime = 0;
	pktsSent = 0;
	while (pktsSent < numPackets) {
	    DatagramPacket packet = getPacket();
	    sendPacket(packet);
	    pktsSent++;
	    int size = packet.getLength();
	    bytesSent += size;
	    long transmitTime = (size * 1000) / dataRate;
	    long end = System.currentTimeMillis();
	    sendTime = end - start;
	    long sleepTime = transmitTime - sendTime;
	    if ((lostTime > 0) && (sleepTime > 0)) {
	        if (lostTime > sleepTime) {
	            lostTime = lostTime - sleepTime;
	            sleepTime = 0;
	        } else {
	            sleepTime = sleepTime - lostTime;
	            lostTime = 0;
	        }
	    }
	    if (sleepTime > 0) {
	        long wakeUpAt =  System.currentTimeMillis() + sleepTime;
		try {
		    totalSleep += sleepTime;
		    long t1 = System.currentTimeMillis();
		    sleep(sleepTime);
		    long actualSleep = System.currentTimeMillis() - t1;
		    totalActualSleep += actualSleep;
		} catch (InterruptedException ie) {
		}
	        end = System.currentTimeMillis();
	        lostTime = end - wakeUpAt;
	        if (lostTime < 0)
	            lostTime = 0;
	    }
	    start = end;
	}
	sessionEndTime = System.currentTimeMillis();
	printStats();
    }
    
    /*
     * Implementation of DT2 Algorithm
     */
    private void runDT2Algorithm() {

	done = false;
	long start = System.currentTimeMillis();
	sessionStartTime = start;
	long lostTime = 0;
	pktsSent = 0;
	while (pktsSent < numPackets) {
	    DatagramPacket packet = getPacket();
	    sendPacket(packet);
	    pktsSent++;
	    int size = packet.getLength();
	    bytesSent += size;
	    long transmitTime = (size * 1000) / dataRate;
	    long sleepTime = transmitTime;
	    if ((lostTime > 0) && (sleepTime > 0)) {
	        if (lostTime > sleepTime) {
	            lostTime = lostTime - sleepTime;
	            sleepTime = 0;
	        } else {
	            sleepTime = sleepTime - lostTime;
	            lostTime = 0;
	        }
	    }
	    if (sleepTime > 0) {
		try {
		    totalSleep += sleepTime;
		    long t1 = System.currentTimeMillis();
		    sleep(sleepTime);
		    long actualSleep = System.currentTimeMillis() - t1;
		    totalActualSleep += actualSleep;
		}catch (InterruptedException ie) {
		}
	        long end = System.currentTimeMillis();
	        lostTime = end - start - sleepTime;
	        if (lostTime < 0)
	            lostTime = 0;
	        start = end;
	    }
	}
	sessionEndTime = System.currentTimeMillis();
	printStats();
    }

    /*
     * Implementation of DT3 Algorithm
     */
    private void runDT3Algorithm() {
	done = false;
	long start = System.currentTimeMillis();
	long burstStart = start;
	long burstSent = 0;
	sessionStartTime = start;
	long lostTime = 0;
	long sendTime = 0;
	pktsSent = 0;
	while (pktsSent < numPackets) {
	    DatagramPacket packet = getPacket();
	    sendPacket(packet);
	    pktsSent++;
	    int size = packet.getLength();
	    bytesSent += size;
	    burstSent += size;
	    long transmitTime = (size * 1000) / dataRate;
	    long burstTime = (burstSent * 1000) / burstRate;
	    long end = System.currentTimeMillis();
	    sendTime = end - start;
	    long sleepTime = transmitTime - sendTime;
	    long pause = burstTime - (end - burstStart);
	    if (pause > sleepTime) {
	        sleepTime = pause;
	    }
	    if ((lostTime > 0) && (sleepTime > 0)) {
	        if (lostTime > sleepTime) {
	            lostTime = lostTime - sleepTime;
	            sleepTime = 0;
	        } else {
	            sleepTime = sleepTime - lostTime;
	            lostTime = 0;
	        }
	    }
	    if (sleepTime > 0) {
	        long wakeUpAt =  System.currentTimeMillis() + sleepTime;
		try {
		    totalSleep += sleepTime;
		    long t1 = System.currentTimeMillis();
		    sleep(sleepTime);
		    long actualSleep = System.currentTimeMillis() - t1;
		    totalActualSleep += actualSleep;
		} catch (InterruptedException ie) {
		}
	        end = System.currentTimeMillis();
	        lostTime = end - wakeUpAt;
	        if (lostTime < 0)
	            lostTime = 0;
	    }
	    start = end;
	}
	sessionEndTime = System.currentTimeMillis();
	printStats();
    }

    /*
     * Implementation of AT0 Algorithm
     */

    private void  runAT0Algorithm() {
	done = false;
	bytesSent = 0;
	long start = System.currentTimeMillis();
	sessionStartTime = start;
	pktsSent = 0;
	while (pktsSent < numPackets) {
	    DatagramPacket packet = getPacket();
	    System.out.println("got packet at " + 
			       (System.currentTimeMillis()-start));
	    sendPacket(packet);
	    pktsSent ++;
	    int size = packet.getLength();
	    bytesSent += size;
	    long next = ((bytesSent * 1000)/dataRate) + start;
	    long sleepTime = next - System.currentTimeMillis();
	    if (sleepTime > 0)
		try {
		    totalSleep += sleepTime;
		    long t1 = System.currentTimeMillis();
		    sleep(sleepTime);
		    long actualSleep = System.currentTimeMillis() - t1;
		    totalActualSleep += actualSleep;
		}catch (InterruptedException ie) {}
	}
	sessionEndTime = System.currentTimeMillis();
	printStats();
    }

    /*
     * Implementation of AT1 Algorithm
     */
    private void  runAT1Algorithm() {
	done = false;
	bytesSent = 0;
	long start = System.currentTimeMillis();
	long fragmentBytes = 0;
	sessionStartTime = start;
	pktsSent = 0;
	while (pktsSent < numPackets) {
	    long gapStart = System.currentTimeMillis();
	    DatagramPacket packet = getPacket();
	    System.out.println("got packet at "+
	        (System.currentTimeMillis()-sessionStartTime));
	    long gapTime = System.currentTimeMillis() - gapStart;
	    if (gapTime > 1000) {
	        start = System.currentTimeMillis();
	        fragmentBytes = 0;
	    }
	    sendPacket(packet);
	    pktsSent ++;
	    int size = packet.getLength();
	    bytesSent += size;
	    fragmentBytes += size;
	    long next = ((fragmentBytes * 1000)/dataRate) + start;
	    long sleepTime = next - System.currentTimeMillis();
	    if (sleepTime > 0)
		try {
		    totalSleep += sleepTime;
		    long t1 = System.currentTimeMillis();
		    sleep(sleepTime);
		    long actualSleep = System.currentTimeMillis() - t1;
		    totalActualSleep += actualSleep;
		}catch (InterruptedException ie) {}
	}
	sessionEndTime = System.currentTimeMillis();
	printStats();
    }
        
    /*
     * Prints Statistics.
     */
    private void printStats() {

	System.out.println("\nSimulation Complete ........");
	System.out.println("Simulation Configuration details ");
	System.out.println("DataRate " + dataRate + ". Data Size " +
			   packetSize + ". Algorithm Chosen: " + 
			   getAlgoString(algorithm));
	long dur = sessionEndTime - sessionStartTime;
	System.out.println("Simulation Time: " + 
			   (dur/1000) + " secs.[ " + dur + "msecs.]");
	System.out.println("Total Pkts sent " + pktsSent);
	System.out.println("Total bytes of data sent " + bytesSent);
	System.out.println("sleep, oversleep: "+totalSleep+" "+
			(totalActualSleep-totalSleep));
	System.out.println("Throughput " + (bytesSent*1000)/dur + 
			   " Bytes/sec");
	
	// long t1 = System.currentTimeMillis();
	// int i = 0;
	// while (i<1000000)
	//    i++;
	// long t2 = System.currentTimeMillis();
	// i = 0;
	// while (i<10000000)
	//    i++;
	// long t3 = System.currentTimeMillis();
	// System.out.println("1M adds "+(t2-t1)+", 10M adds "+(t3-t2));
    }

    /*
     * gets the String equivalent of the selected algorithm.
     */
    private String getAlgoString(int algo) {
	
	switch (algo) {
	  case DT0:
	    return "DT0";
	    
	  case DT1:
	    return "DT1";
	    
	  case DT2:
	    return "DT2";

	  case AT0:
	    return "AT0";
	  
	  case AT1:
	    return "AT1";
	  
	  case DT3:
	    return "DT3";
	    
	  default:
	    break;
	}
	return "UNKNOWN";
    }

    /*
     * Converts the integer equivalent of a algorithm
     */
    private int getAlgoFromString(String algoString) {
	
	if (algoString.equalsIgnoreCase("DT0") == true)
	    return DT0;

	if (algoString.equalsIgnoreCase("DT1") == true)
	    return DT1;

	if (algoString.equalsIgnoreCase("DT2") == true)
	    return DT2;
     
	if (algoString.equalsIgnoreCase("AT0") == true)
	    return AT0;

	if (algoString.equalsIgnoreCase("DT3") == true)
	    return DT3;
     
	if (algoString.equalsIgnoreCase("AT1") == true)
	    return AT1;

	return UNKNOWN;
    }

    /*
     * Method to simulate getting the packet from the upper layer
     */

    private DatagramPacket getPacket() {

	byte[] buff = new byte[packetSize];

	// fillup the buffer with some data
	for (int i = 0; i < packetSize; i++) {
	    buff[i] = (byte) 0;
	}

	DatagramPacket dp =
	    new DatagramPacket(buff, packetSize, sendAddress, sendPort);
	
	if (arrivalModel == 0) {
	    // some minimal delay
	    int i = 0;
	    while (i < 30) {
	        i++;
	    }
	} else if (arrivalModel == 1) {
	    // 10Kbytes data arrive at beginning of each 10sec interval
	    long now = System.currentTimeMillis();
	    long dur = now - sessionStartTime;
	    long batch = dur / (long)10000;
	    long earlierTotal = batch * (long)10000;
	    if ((bytesSent - earlierTotal) >= 10000) {
	        long waitTime = 10000 - (dur - (batch*10000));
	        if (waitTime > 0)
	            try {
	                sleep(waitTime);
	            }catch (InterruptedException ie) {}
	    }
	}
	return dp;
    }

    /*
     * Method to simulate sending the packet to the upper layer
     */

    private void sendPacket(DatagramPacket dp) {
	int size = dp.getLength();
	// fixed cost = 2msec caliberated on my machine
	int i = 0;
	while (i < 102000)
	    i++;
	// variable cost = 0
	// for (i=0; i<size; i++)
	//    while(i<10)
	//        i++;
    }

}





