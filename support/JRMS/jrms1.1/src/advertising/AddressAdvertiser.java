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
 * AddressAdvertiser.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;

/**
 * A SAP Address Advertiser.
 * Manages all advertisements being advertised on a particular 
 * multicast address.
 * Created by the static Advertiser class.
 * @see                         Advertiser
 * @see                         Advertisement
 */
class AddressAdvertiser extends Object {

    /**
     * Creates a new AddressAdvertiser to send advertisements on 
     * the indicated multicast address.
     * The multicast socket is created, and the multicast group is joined
     * @param address the InetAddress to use for sending announcements
     */
    AddressAdvertiser(InetAddress address) throws java.io.IOException {
        advertisingAddress = address; // save user-specified advertising address
        mSocket = new MulticastSocket(SD_PORT); // get a socket and join group

        mSocket.joinGroup(advertisingAddress);

        advertisementList = new Vector(); // initialize vector of advertisements
    }

    /**
     * Starts advertising the address using the given time-to-live.
     * @param address the InetAddress to advertise
     * @param ttl the time-to-live to use in the advertisement
     */
    synchronized void startAdvertising(InetAddress address, int ttl, 
        Date startTime, Date endTime) throws java.net.UnknownHostException {

        // build a SAP advertisement for the address in use

        Advertisement advert = new Advertisement(address, ttl);

        // set up the start and end times

        if (startTime != null) {
            advert.setStartTime(startTime);
        } 
        if (endTime != null) {
            advert.setEndTime(endTime);
        } 

        // save the advertisement.  Advertising starts at the next tick

        advertisementList.addElement(advert);
    }

    /**
     * Starts advertising the advertisement.
     * @param advertisement the advertisement to advertise
     */
    synchronized void startAdvertising(Advertisement advertisement) {

        // save the advertisement.  Advertising starts at the next tick

        advertisementList.addElement(advertisement);
    }

    /**
     * Stops advertising the address.
     * @param address the InetAddress to stop advertising
     * @return <code>true</code> if the advertisement was found;
     * <code>false</code> otherwise
     */
    synchronized boolean stopAdvertising(InetAddress address) {

        // see if we are advertising the address

        int i;
        int listSize = advertisementList.size();

        for (i = 0; i < listSize; i++) {
            Advertisement advert = 
                (Advertisement) advertisementList.elementAt(i);

            if (address.equals(advert.getAdvertisedAddress())) {
                advert.stopAdvertising(advertisingAddress, mSocket);
                advertisementList.removeElementAt(i);

                return (true);
            }
        }

        return (false);
    }

    /**
     * Stops advertising the advertisement.
     * @param advertisement the advertisement to stop advertising
     * @return <code>true</code> if the advertisement was found;
     * <code>false</code> otherwise
     */
    synchronized boolean stopAdvertising(Advertisement advertisement) {

        // see if we are advertising it

        int i;
        int listSize = advertisementList.size();

        for (i = 0; i < listSize; i++) {
            Advertisement advert = 
                (Advertisement) advertisementList.elementAt(i);

            if (advert.matches(advertisement)) {
                advert.stopAdvertising(advertisingAddress, mSocket);
                advertisementList.removeElementAt(i);

                return (true);
            }
        }

        return (false);
    }

    /**
     * Returns the number of advertisements for this advertiser
     * @return current number of advertisements
     */
    int getAdvertisementCount() {
        return (advertisementList.size());
    }

    /**
     * Returns the InetAddress of the advertising address.
     * @return the InetAddress of the advertising address
     */
    InetAddress getAdvertisingAddress() {
        return (advertisingAddress);
    }

    /**
     * processes a timeout from the static Advertiser class
     * for each advertisement, determines whether or not it is time to send
     * out another copy.  If so, the Advertisement's advertise method is called
     */
    synchronized void timeout(int elapsedTime) throws java.io.IOException {

        // each time we time out, see if we should send any announcements

        int i;
        Advertisement advert;

        for (i = 0; i < advertisementList.size(); i++) {
            ((Advertisement) (advertisementList.elementAt(i))).advertise(
		advertisingAddress, mSocket, elapsedTime);
        }
    }

    static final int SD_PORT = 9875;       // official port number for SAP v2
    InetAddress advertisingAddress;        // address for sending advertisements
    Vector advertisementList;              // list of all advertisements 
					   // on advertisingAddress
    private MulticastSocket mSocket;       // socket for sending advertisements

}

