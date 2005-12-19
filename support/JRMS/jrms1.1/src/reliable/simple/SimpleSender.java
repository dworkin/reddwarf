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
 * SimpleSender.java
 */
package com.sun.multicast.reliable.simple;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Date;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnimplementedOperationException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.allocation.AddressAllocationException;
import com.sun.multicast.allocation.IPv4Address;
import com.sun.multicast.allocation.IPv4AddressType;
import com.sun.multicast.allocation.Lease;
import com.sun.multicast.allocation.MulticastAddressManager;
import com.sun.multicast.allocation.Scope;
import com.sun.multicast.allocation.NoAddressAvailableException;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.MROLE;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMStats;
import com.sun.multicast.reliable.transport.tram.TMODE;

/**
 * A simple sender object. This class provides a single class that
 * lets you send data as simply as possible. Multiple senders
 * per channel, security, and out of order delivery are not supported.
 */
public class SimpleSender {

    /**
     * Creates a SimpleSender with the parameters given. This constructor 
     * handles selecting a multicast address, creating a transport profile, 
     * creating a channel, and advertising it. All parameters except endTime 
     * are required to be not null.
     * @param applicationName the name of the application
     * @param channelName the name of the channel
     * @param startTime the time that the sender expects to start sending data
     * @param endTime the time that the sender expects to stop sending data 
     * (null if unknown)
     * @param scope the administrative scope requested (null to choose one 
     * based on the ttl)
     * @param ttl the time-to-live value for the data sent (1 is a good default)
     * @exception java.io.IOException if an I/O error occurs
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    public SimpleSender(String applicationName, String channelName, 
	Date startTime, Date endTime, Scope scope, byte ttl) throws 
	RMException, IOException, RemoteException {

	System.out.println("Application name = " + applicationName);
        System.out.println("Channel name = " + channelName);
        System.out.println("startTime = " + startTime.toString());
        System.out.println("Time to live = " + Byte.toString(ttl));
        try {
	    System.out.println("Getting Primary Channel Manager");
            pcm = (PrimaryChannelManager) 
		ChannelManagerFinder.getPrimaryChannelManager(null);

	    System.out.println("Getting Multicast Address Manager.");
            MulticastAddressManager mam = 
                MulticastAddressManager.getMulticastAddressManager();
	    if (scope == null) {
		System.out.println("scope is null.");		
		System.out.println(
		    "Getting scope from Multicast Address Manager.");
	        scope = mam.getScopeList(
		    IPv4AddressType.getAddressType()).findScopeForTTL(ttl);
	        System.out.println(mam.getScopeList(
		    IPv4AddressType.getAddressType()));
	        if (scope == null)
		    throw new IOException("No scope for requested TTL");
	    }
	    System.out.println("Setting duration");
	    int duration;
	    if (endTime == null)
	        duration = -1;
	    else {
	        long durationLong = 
		    (endTime.getTime() - startTime.getTime()) / 1000;

	        if ((durationLong > Integer.MAX_VALUE) || (durationLong < 0))
		    duration = -1;
	        else
	  	    duration = (int) durationLong;
	    }
	    System.out.println("Duration is now " + Integer.toString(duration));
	    System.out.println("Getting lease from Multicast Address Manager");
	    Lease lease = mam.allocateAddresses(null, scope, (int) ttl,
                1, startTime, startTime, duration, duration, null);
	    System.out.println("Getting mcastAddress from lease.");
            InetAddress mcastAddress = ((IPv4Address) lease.getAddresses().
		getFirstAddress()).toInetAddress();

            // @@@ Need a better way to choose a port?
            // @@@ Should use try...finally and finalize to deallocate 
	    // multicast address, destroy channel, etc.
	
	    System.out.println("Creating the TRAMTransportProfile.");
            tp = new TRAMTransportProfile(mcastAddress, 4321);
 	    // tp.setLogMask(TRAMTransportProfile.LOG_VERBOSE);	
	    // tp.setLogMask(TRAMTransportProfile.LOG_CONGESTION);

            // @@@ Is this required?
            // tp.setMaxDataRate((long) speed);

	    System.out.println(
		"Setting the time to live to :" + Integer.toString(ttl));

            tp.setTTL(ttl);

	    System.out.println("Setting ordered to true");
            tp.setOrdered(true);

            // Create and configure channel

	    System.out.println("Creating a Channel");

            c = pcm.createChannel();

	    System.out.println("Setting Channel Name to: " + channelName);

            c.setChannelName(channelName);
            c.setApplicationName(applicationName);
            c.setDataStartTime(startTime);
            c.setDataEndTime(endTime);

            if (endTime != null) {
		System.out.println("endTime was null.");
		// data end + 20 seconds
                c.setSessionEndTime(new Date(endTime.getTime() + 20000));     
            } 
	    System.out.println(
		"Setting the channel to the TRAMTransportProfile.");

            c.setTransportProfile(tp);
	    System.out.println("Setting Advertising Requested to true");
            c.setAdvertisingRequested(true);

	    System.out.println("Setting channel enabled to true");
            c.setEnabled(true);

            // Create stream socket for sender
	    System.out.println("Creating stream socket for sender.");
            ms = tp.createRMStreamSocket(TransportProfile.SENDER);
        } catch (InvalidMulticastAddressException e) {
	} catch (InvalidTransportProfileException e) {
	} catch (UnsupportedException e) {
	} catch (NoAddressAvailableException e) {
	} catch (AddressAllocationException e) {
	}
    }

    /**
     * Creates a SimpleSender a serialized channel stored in a file.
     * @param channelFileName
     * @exception java.io.IOException if an I/O error occurs
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public SimpleSender(String channelFileName)
        throws RMException, IOException {

     	try {
            pcm = (PrimaryChannelManager) 
		ChannelManagerFinder.getPrimaryChannelManager(null);

	    c = (Channel) pcm.readChannel(channelFileName);
	    tp = (TRAMTransportProfile) c.getTransportProfile();
	    // tp.setLogMask(TRAMTransportProfile.LOG_INFO);
	    // tp.setLogMask(TRAMTransportProfile.LOG_CONGESTION);
	    // tp.setLogMask(TRAMTransportProfile.LOG_VERBOSE);
	    ms = tp.createRMStreamSocket(TransportProfile.SENDER);
	} catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
    public SimpleSender(String channelFileName, boolean verbose)
        throws RMException, IOException {

     	try {
            pcm = (PrimaryChannelManager) 
		ChannelManagerFinder.getPrimaryChannelManager(null);

	    c = (Channel) pcm.readChannel(channelFileName);
	    tp = (TRAMTransportProfile) c.getTransportProfile();
	    // tp.setLogMask(TRAMTransportProfile.LOG_INFO);
	    // tp.setLogMask(TRAMTransportProfile.LOG_CONGESTION);
	    if (verbose) {
	    	tp.setLogMask(TRAMTransportProfile.LOG_VERBOSE);
	    }
	    ms = tp.createRMStreamSocket(TransportProfile.SENDER);
	} catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
     
    /**
     * Waits until a specific time.
     * @param time the time to wait for
     */
    public void waitTill(Date time) {
        long timeMillis = time.getTime();

        while (System.currentTimeMillis() < timeMillis) {
            try {
                Thread.sleep(100); // timeMillis - System.currentTimeMillis());
            } catch (InterruptedException e) {}
        }
    }

    /**
     * Waits until a certain number of receivers are on the channel 
     * *** currently unsupported ***
     * @param numberReceivers the number of receivers to wait for
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public void waitTill(int numberReceivers) 
	throws UnsupportedException, RMException {

	TRAMStats stat = (TRAMStats)ms.getRMStatistics();
        while (stat.getReceiverCount() < numberReceivers) {
            try {
                Thread.sleep(1000);      // wait another second
		System.out.println(
		    "Found " + Integer.toString(stat.getReceiverCount()) + 
		    " receiver(s)");
            } catch (InterruptedException e) {}
        }
    }

    /**
     * Returns an OutputStream object that may be used to send data.
     * @return an OutputStream object
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception UnsupportedException if the operation is not supported
     */
    public OutputStream getOutputStream() 
            throws UnsupportedException, RMException {
        return (ms.getOutputStream());
    }

    /**
     * Leaves the multicast transport session gracefully.
     * Pending transmissions and outgoing repairs are handled properly.
     * This method may take some time to return.
     */
    public void close() {
        ms.close();
    }
    
    public TRAMTransportProfile getTRAMTransportProfile() {
	return tp;
    }
    private TRAMTransportProfile tp; 
    private PrimaryChannelManager pcm;
    private Channel c;
    private RMStreamSocket ms;

}
