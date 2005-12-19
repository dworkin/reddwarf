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
 * Advertiser.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;

/**
 * Controls advertising of Advertisement objects.
 * 
 * @see                         Advertisement
 */
public class Advertiser implements Runnable {

    /**
     * creates the Advertiser object.
     */
    Advertiser() throws java.net.UnknownHostException {
        advertiserList = 
            new Vector();       // initialize the list of AddressAdvertisers
        SDAddress = InetAddress.getByName(SD_ADDRESS);
    }

    /**
     * Returns a reference to the Advertiser.
     * @return reference to the Advertiser object
     */
    public static synchronized Advertiser getAdvertiser() 
            throws java.net.UnknownHostException {
        if (theAdvertiser == null) {

            // make a new one

            theAdvertiser = new Advertiser();

            // start the thread

            advertiserThread = new Thread(theAdvertiser);

            // make this a daemon thread

            advertiserThread.setDaemon(true);

            // start the thread

            advertiserThread.start();
        }

        return (theAdvertiser);
    }

    /**
     * Starts advertising an address on the specified address.
     * @param advertisingAddress the address on which to send the advertisement
     * @param address the address to be advertised
     * @param ttl the time-to-live to use in the advertisement
     * @param startTime the time to start advertising
     * @param endTime the time to stop advertising
     */
    public void startAdvertising(InetAddress advertisingAddress, 
                                 InetAddress address, int ttl, 
                                 Date startTime, 
                                 Date endTime) throws IOException {
        if (advertisingAddress == null) {
            startAdvertising(address, ttl, startTime, endTime);

            return;
        }

        // make sure we have an address advertiser

        AddressAdvertiser advertiser = addAdvertiser(advertisingAddress);

        advertiser.startAdvertising(address, ttl, startTime, endTime);
    }

    /**
     * Starts advertising an Advertisement on the specified advertising address.
     * @param advertisingAddress the address on which to send the Advertisement
     * @param advertisement the Advertisement to be advertised
     */
    public void startAdvertising(InetAddress advertisingAddress,
        Advertisement advertisement) throws IOException {

        if (advertisingAddress == null) {
            startAdvertising(advertisement);

            return;
        }

        // make sure we have an address advertiser

        AddressAdvertiser advertiser = addAdvertiser(advertisingAddress);

        advertiser.startAdvertising(advertisement);
    }

    /**
     * Starts advertising an address on the default advertising address.
     * @param address the address to be advertised
     * @param ttl the time-to-live to use in the advertisement
     * @param startTime the time to start advertising
     * @param endTime the time to stop advertising
     */
    public void startAdvertising(InetAddress address, int ttl, 
                                 Date startTime, 
                                 Date endTime) throws IOException {
        startAdvertising(SDAddress, address, ttl, startTime, endTime);
    }

    /**
     * Starts advertising an Advertisement on the default advertising address.
     * @param advertisement the Advertisement to be advertised
     */
    public void startAdvertising(Advertisement advertisement) 
            throws IOException {
        startAdvertising(SDAddress, advertisement);
    }

    /**
     * Stops advertising an address.
     * @param address the address to stop advertising
     * @return <code>true</code> if the Advertisement was found;
     * <code>false</code> otherwise
     */
    public synchronized boolean stopAdvertising(InetAddress address) {
        int i;
        boolean found;

        for (i = 0, found = false; i < advertiserList.size(); i++) {

            // look at each address advertiser

            AddressAdvertiser advertiser = 
                (AddressAdvertiser) advertiserList.elementAt(i);

            if (advertiser.stopAdvertising(address)) {

                // if no more advertisements for this advertiser, remove it

                if (advertiser.getAdvertisementCount() == 0) {
                    advertiserList.removeElementAt(i);
                } 

                return (true);
            }
        }

        return (false);
    }

    /**
     * Stops advertising an Advertisement.
     * advertisement the Advertisement to stop advertising
     * @return <code>true</code> if the Advertisement was found;
     * <code>false</code> otherwise
     */
    public synchronized boolean stopAdvertising(Advertisement advertisement) {
        int i;
        boolean found;

        for (i = 0, found = false; i < advertiserList.size(); i++) {

            // look at each address advertiser

            AddressAdvertiser advertiser = 
                (AddressAdvertiser) advertiserList.elementAt(i);

            if (advertiser.stopAdvertising(advertisement)) {

                // if no more advertisements for this advertiser, remove it

                if (advertiser.getAdvertisementCount() == 0) {
                    advertiserList.removeElementAt(i);
                } 

                return (true);
            }
        }

        return (false);
    }

    /**
     * adds an advertiser for the specified address
     * @param address the address on which to send the advertisements
     * @return the address advertiser to use
     */
    private synchronized AddressAdvertiser addAdvertiser(InetAddress address) 
            throws IOException {

        // see if we already have one for this address

        AddressAdvertiser advertiser;

        if ((advertiser = findAdvertiser(address)) == null) {

            // make a new one for this address

            advertiser = new AddressAdvertiser(address);

            advertiserList.addElement(advertiser);
        }

        return (advertiser);
    }

    /**
     * finds an advertiser for the specified address
     * @param address the address on which to send the advertisements
     * @return the address advertiser to use, if any
     */
    private synchronized AddressAdvertiser findAdvertiser(InetAddress address) {
        int i;
        AddressAdvertiser advertiser;

        for (i = 0; i < advertiserList.size(); i++) {
            advertiser = (AddressAdvertiser) advertiserList.elementAt(i);

            if (address.equals(advertiser.getAdvertisingAddress())) {
                return (advertiser);
            } 
        }

        return (null);
    }

    /**
     * Overrides Thread's run method.
     */
    public void run() {     // overrides Thread's run method

        // each time we run, tell each advertiser to run
        // just in case the system doesn't give us precise 10-second
        // timeouts, calculate exactly how much time has passed since
        // the last one

        lastExpiration = new Date();   // start with right now, minus 10 seconds

        lastExpiration.setTime(lastExpiration.getTime() - 10000);

        while (true) {
            Date currentTime = new Date();

            try {
                timeoutAdvertisers(currentTime);

                // save the last expiration time

                lastExpiration = currentTime;

                advertiserThread.sleep(SLEEPTIME);      // nanni, nanni
            } catch (InterruptedException e) {}
            catch (IOException e) {}
        }
    }

    /**
     * Times out each advertiser every 10 seconds
     */
    synchronized void timeoutAdvertisers(Date currentTime) 
            throws java.io.IOException {
        int i;

        for (i = 0; i < advertiserList.size(); i++) {
            AddressAdvertiser advertiser = 
                (AddressAdvertiser) advertiserList.elementAt(i);

            advertiser.timeout((int)((currentTime.getTime() - 
		lastExpiration.getTime()) / 1000));
        }
    }

    static Thread advertiserThread;
    static final String SD_ADDRESS = 
        "224.2.127.254";                // official address for SAP v1
    static InetAddress SDAddress;
    static final long SLEEPTIME = 10000; // how often thread runs in millisecs
    static Vector advertiserList;       // list of address advertisers
    private static Advertiser theAdvertiser = null;
    private Date lastExpiration;        // last time ten-second timer expired

}
