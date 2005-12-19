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
 * AddressListener.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;

/**
 * A SAP Address Listener.
 * @see                         Listener
 * @see                         Advertisement
 */
class AddressListener implements Runnable {

    /**
     * Creates an Address Listener.
     * @param address The multicast address to listen on for advertisements
     */
    AddressListener(InetAddress address) throws java.io.IOException {
        listeningAddress = 
            address;        // save the user-specified Listening address

        // get us a socket to receive on

        mSocket = new MulticastSocket(SD_PORT);

        mSocket.joinGroup(listeningAddress);        // join the multicast group

        advertisementList = new Vector();       // initilize advertisement list
        invalidAdvertisements = 0;

        try {
            SAPListener = Listener.getListener();
        } catch (java.net.UnknownHostException e) {}
    }

    /**
     * Returns the count of invalid advertisements received
     * @return invalid advertisement count
     */
    int getInvalidAdvertisementCount() {
        return (invalidAdvertisements);
    }

    /**
     * Returns the listening address InetAddress object
     * @return the listening address InetAddress object
     */
    InetAddress getListeningAddress() {
        return (listeningAddress);
    }

    /**
     * Returns the count of advertisements heard.
     * @return the count of advertisements heard
     */
    int getCurrentAdCount() {
        return (advertisementList.size());
    }

    /**
     * Returns an array of advertisements heard on the listening address.
     * The array consists of a list of Advertisement objects.
     * @return an array of the advertisements heard on the listening address
     */
    synchronized Advertisement[] getAdvertisements() {
        Advertisement advertArray[] = 
            new Advertisement[advertisementList.size()];
        int i, j;
        Advertisement advertisement, clonedAdvertisement;

        for (i = 0, j = 0; i < advertisementList.size(); i++) {
            advertisement = (Advertisement) advertisementList.elementAt(i);

            if ((clonedAdvertisement = (Advertisement) advertisement.clone()) 
                    != null) {
                advertArray[j++] = clonedAdvertisement;
            } 
        }

        return (advertArray);
    }

    /**
     * Determines whether of not the indicated address is being advertised.
     * @param testAddress the address to test
     * @return <code>true</code> if the address is being advertised;
     * <code>false</code> otherwise
     */
    synchronized boolean isAddressInUse(InetAddress testAddress) {
        int i;

        for (i = 0; i < advertisementList.size(); i++) {
            Advertisement advert = 
                (Advertisement) advertisementList.elementAt(i);

            if (testAddress.equals(advert.getAdvertisedAddress())) {
                return (true);      // we have a match
            } 
        }

        return (false);     // no match
    }

    /**
     * Returns a reference to the original advertisement matching the argument
     * @return the original advertisement
     */
    synchronized Advertisement findOriginalAdvertisement(Advertisement advert) {
        int i;
        Advertisement original;

        for (i = 0; i < advertisementList.size(); i++) {
            original = (Advertisement) advertisementList.elementAt(i);

            if (original.matches(advert)) {
                return original;
            } 
        }

        return null;
    }

    /**
     * Runs the AddressListener thread.
     * Overrides Thread's run method.  Receives packets off of the multicast
     * socket, then updates the advertisement list.
     */
    public void run() {
        while (true) {
            try {

                // make a packet to receive the advertisement

                byte dpBuffer[] = new byte[SAPBUFFERSIZE];
                DatagramPacket dPacket = new DatagramPacket(dpBuffer, 
                                                            SAPBUFFERSIZE);

                mSocket.receive(dPacket);

                // make a new advertisement object out of the info in the packet

                Advertisement advert = new Advertisement(dPacket);

                if (advert.isDeletion()) {

                    // process deletion request

                    deleteAdvertisement(advert);
                } else {

                    // while we're here, remove any duplicate advertisement

                    checkForDuplicate(advert);
                }
            }       // for now, print these out
            catch (java.io.IOException e) {}
            catch (InvalidAdvertisementException e) {
                invalidAdvertisements++;
            }
        }
    }

    /**
     * Removes a duplicate advertisement from the list
     * @param newAdvert new Advertisment object
     */
    private synchronized void checkForDuplicate(Advertisement newAdvert) {
        int i;

        for (i = 0; i < advertisementList.size(); i++) {
            Advertisement advert = 
                (Advertisement) advertisementList.elementAt(i);

            if (advert.matches(newAdvert)) {
                if (advert.getVersion() < newAdvert.getVersion()) {

                    // existing advertisement is older; get rid of it

                    advertisementList.removeElement(advert);

                    // replace the old one with the new one and inform listeners

                    advert.replace(newAdvert);

                    // add the new advertisement to the list

                    advertisementList.addElement(newAdvert);

                    return;
                } else {

                    // keep existing advertisement

                    advert.setLastTime(new Date());

                    return;
                }
            }
        }

        // add the new advertisement to the list

        advertisementList.addElement(newAdvert);

        // tell the Listener that some advertisements changed

        SAPListener.informListenersAdd(newAdvert);
    }

    /**
     * Removes a deleted advertisement from the list
     * @param newAdvert advertisement deletion
     */
    private synchronized void deleteAdvertisement(Advertisement newAdvert) {
        int i;

        for (i = 0; i < advertisementList.size(); i++) {
            Advertisement advert = 
                (Advertisement) advertisementList.elementAt(i);

            if (advert.matches(newAdvert)) {
                advertisementList.removeElement(advert);

                // delete the advertisement and inform listeners

                advert.delete();

                return;
            }
        }
    }

    /**
     * flushes expired advertisements
     * @param time the current time
     */
    synchronized void timeout(Date currentTime) {

        // see if any of the advertisements have expired
        // if no advertisements, all done

        if (getCurrentAdCount() == 0) {
            return;
        } 

        // figure out if we haven't heard about it for the required time,
        // ten times the send interval, or a half hour, whichever is greater

        int maxTime = 1800;       // seconds in a half hour

        // pick up the current advertising interval

        int currentInterval = ((Advertisement) 
	    advertisementList.elementAt(0)).recalculateInterval(
	    listeningAddress);     // in seconds

        // multiply by ten to really give them every opportunity;

        if ((currentInterval * 10) > maxTime) {
            maxTime = currentInterval * 10;
        } 

        int i;

        for (i = 0; i < advertisementList.size(); i++) {
            Advertisement advert = 
                (Advertisement) advertisementList.elementAt(i);

	    /*
	     * session has expired or is is longer being advertised
	     */
            if (((advert.getEndTime() != null) && 
		(currentTime.after(advert.getEndTime()))) || 
                ((currentTime.getTime() - advert.getLastTime().getTime()) > 
		(maxTime * 1000))) {

                // session no longer active

                advert.delete();
                advertisementList.removeElement(advert);
            }
        }
    }

    static final int SD_PORT = 9875;        // official port number for SAP v1
    static final int SAPBUFFERSIZE = 2048;
    InetAddress listeningAddress;           // address to use for listening
    Vector advertisementList;               // list of received advertisements
    MulticastSocket mSocket;                // socket to listen on
    int invalidAdvertisements;      // count of invalid advertisements received
    static Listener SAPListener;
}

