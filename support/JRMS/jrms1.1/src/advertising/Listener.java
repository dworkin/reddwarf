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
 * Listener.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;

/**
 * The Listener object manages all address listener objects
 * 
 * @see                         Advertisement
 */
public class Listener extends Thread {

    /**
     * Creates the Listener object
     */
    Listener() throws java.net.UnknownHostException {
        listenerList = new Vector();
        changeListenerList = new Vector();
        someAdvertisementChanged = false;
        SDAddress = InetAddress.getByName(SD_ADDRESS);
    }

    /**
     * Retrieves the Listener object
     */
    public static synchronized Listener getListener() 
            throws java.net.UnknownHostException {
        if (theListener == null) {
            theListener = new Listener();

            // make a thread

            listenerThread = new Thread(theListener);

            // make this a daemon thread

            listenerThread.setDaemon(true);
            listenerThread.start();
        }

        return (theListener);
    }

    /**
     * Starts listening on the default address
     */
    public void startListening() throws java.io.IOException {

        // make sure we have an address listener

        AddressListener listener = addListener(SDAddress);
    }

    /**
     * Starts listening on the specified address
     * @param address address to listen on
     */
    public void startListening(InetAddress listeningAddress) 
            throws java.io.IOException {

        // make sure we have an address listener

        AddressListener listener = addListener(listeningAddress);
    }

    /**
     * Adds a listener on the specified address
     * @param address address to listen on
     * @return AddressListener object for this address
     */
    private synchronized AddressListener addListener(InetAddress address) 
            throws java.io.IOException {

        // see if we already have one for this address

        int i;
        boolean found;
        AddressListener listener = null;

        for (i = 0, found = false; i < listenerList.size(); i++) {
            listener = (AddressListener) listenerList.elementAt(i);

            if (address.equals(listener.getListeningAddress())) {
                found = true;

                break;      // found it; all done
            }
        }

        if (!found) {

            // make a new one for this address

            listener = new AddressListener(address);

            Thread listenerThread = new Thread(listener);

            listenerThread.setDaemon(true);
            listenerThread.start();
            listenerList.addElement(listener);
        }

        return (listener);
    }

    /**
     * Returns the count of advertisements heard on a given address
     * @param address listening address
     * @return count of advertisements heard
     */
    public synchronized int getCurrentAdCount(InetAddress advertisingAddress) {
        int i;

        for (i = 0; i < listenerList.size(); i++) {

            // find the listener for this address

            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            if (advertisingAddress.equals(listener.getListeningAddress())) {
                return (listener.getCurrentAdCount());
            }
        }

        return (0);
    }

    /**
     * Returns the count of advertisements heard on the standard address
     * @return count of advertisements heard
     */
    public int getCurrentAdCount() {
        return (getCurrentAdCount(SDAddress));
    }

    /**
     * Returns the count of invalid advertisements received on the standard 
     * address
     * @return invalid advertisement count
     */
    public int getInvalidAdvertisementCount() {
        return getInvalidAdvertisementCount(SDAddress);
    }

    /**
     * Returns the count of invalid advertisements received on the address
     * @return invalid advertisement count
     */
    public int getInvalidAdvertisementCount(InetAddress advertisingAddress) {
        int i;

        for (i = 0; i < listenerList.size(); i++) {

            // find the listener for this address

            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            if (advertisingAddress.equals(listener.getListeningAddress())) {
                return (listener.getInvalidAdvertisementCount());
            }
        }

        return (0);
    }

    /**
     * Determines whether or not the specified address is being advertised
     * @param address address to look for
     * @return <code>true</code> if the address is being advertised;
     * <code>false</code> otherwise
     */
    public synchronized boolean isAddressInUse(InetAddress testAddress) {
        int i;

        for (i = 0; i < listenerList.size(); i++) {
            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            if (listener.isAddressInUse(testAddress)) {
                return (true);
            } 
        }

        return (false);
    }

    /**
     * Returns an array of all advertisements heard
     * @return array of all current advertisements
     */
    public synchronized Advertisement[] getAllAdvertisements() {

        // combine all of the advertisement vectors
        // first, figure out how big the array needs to be

        int i, j, k, arraySize;

        arraySize = 0;

        Advertisement adverts[];

        for (i = 0; i < listenerList.size(); i++) {
            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            arraySize += listener.getCurrentAdCount();
        }

        Advertisement[] allAdverts = new Advertisement[arraySize];

        for (i = 0, k = 0; i < listenerList.size(); i++) {
            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            if ((adverts = listener.getAdvertisements()) != null) {

                // add advertisements to the array

                for (j = 0; j < adverts.length; j++, k++) {
                    allAdverts[k] = adverts[j];
                }
            }
        }

        return (allAdverts);
    }

    /**
     * Returns an array of all advertisements heard on the specified address
     * @param address listening address
     * @return array of advertisements
     */
    public synchronized Advertisement[] getAdvertisements(InetAddress address) {
        int i;
        AddressListener listener = null;

        // first find the listener

        for (i = 0; i < listenerList.size(); i++) {
            listener = (AddressListener) listenerList.elementAt(i);

            if (address.equals(listener.getListeningAddress())) {
                return (listener.getAdvertisements());
            } 
        }

        return (null);
    }

    /**
     * Returns an array of all advertisements heard on the default address
     * @return array of advertisements
     */
    public Advertisement[] getAdvertisements() {
        return (getAdvertisements(SDAddress));
    }

    /**
     * Returns a reference to the original advertisement matching the argument
     * @return the original Advertisement object
     */
    Advertisement findOriginalAdvertisement(Advertisement advert) {
        int i;
        Advertisement original;

        for (i = 0; i < listenerList.size(); i++) {
            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            if ((original = listener.findOriginalAdvertisement(advert)) 
                    != null) {
                return original;
            } 
        }

        return null;
    }

    /**
     * Adds a listener for any change in the advertisements
     * @param changelistener an instance of AllAdvertisementsChangeListener
     */
    public synchronized void addAllAdvertisementsChangeListener(
	AllAdvertisementsChangeListener changeListener) {

        changeListenerList.addElement(changeListener);
    }

    /**
     * Removes a listener for any change in the advertisements
     * @param changelistener an instance of AllAdvertisementsChangeListener
     */
    public synchronized void removeAllAdvertisementsChangeListener(
	AllAdvertisementsChangeListener changeListener) {

        changeListenerList.removeElement(changeListener);
    }

    /**
     * Overrides Thread's run method.
     * times out each advertiser every 10 seconds
     */
    public void run() {     // overrides Thread's run method

        // each time we wake up, look for expired advertisements

        while (true) {
            Date currentTime = new Date();

            try {
                timeoutListeners(currentTime);
                listenerThread.sleep(SLEEPTIME);        // nanni, nanni
            } catch (InterruptedException e) {}
            catch (IOException e) {}
        }
    }

    /**
     * Times out each listener every 10 seconds
     */
    private synchronized void timeoutListeners(Date currentTime) 
            throws java.io.IOException {
        int i;

        for (i = 0; i < listenerList.size(); i++) {
            AddressListener listener = 
                (AddressListener) listenerList.elementAt(i);

            listener.timeout(currentTime);
        }
    }

    /**
     * Informs listeners that an advertisemnt has been added
     */
    synchronized void informListenersAdd(Advertisement advert) {

        // tell any listeners that there was a change

        AllAdvertisementsChangeListener changeListener;
        int j;

        for (j = 0; j < changeListenerList.size(); j++) {

            // make an event object for each listener

            AllAdvertisementsChangeEvent aace = 
                new AllAdvertisementsChangeEvent(this, advert);

            changeListener = (AllAdvertisementsChangeListener) 
		changeListenerList.elementAt(j);

            changeListener.allAdvertisementsAdd(aace);
        }
    }

    /**
     * Informs listeners that an advertisemnt has been changed
     */
    synchronized void informListenersChange(Advertisement advert) {

        // tell any listeners that there was a change

        AllAdvertisementsChangeListener changeListener;
        int j;

        for (j = 0; j < changeListenerList.size(); j++) {

            // make an event object for each listener

            AllAdvertisementsChangeEvent aace = 
                new AllAdvertisementsChangeEvent(this, advert);

            changeListener = (AllAdvertisementsChangeListener) 
		changeListenerList.elementAt(j);

            changeListener.allAdvertisementsChange(aace);
        }
    }

    /**
     * Informs listeners that an advertisemnt has been deleted
     */
    synchronized void informListenersDelete(Advertisement advert) {

        // tell any listeners that there was a deletion

        AllAdvertisementsChangeListener changeListener;
        int j;

        for (j = 0; j < changeListenerList.size(); j++) {

            // make an event object for each listener

            AllAdvertisementsChangeEvent aace = 
                new AllAdvertisementsChangeEvent(this, advert);

            changeListener = (AllAdvertisementsChangeListener) 
		changeListenerList.elementAt(j);

            changeListener.allAdvertisementsDelete(aace);
        }
    }

    static Thread listenerThread;
    static final long SLEEPTIME = 60000;   // how often thread runs in millisecs

    // static final long SLEEPTIME = 10000;  // for testing

    static InetAddress SDAddress;           // official address for SAP v1
    static final String SD_ADDRESS = 
        "224.2.127.254";                    // official address for SAP v1
    Vector listenerList;                    // list of address listeners
    private static Listener theListener = null;
    private Vector changeListenerList;      // list of change listeners
    private boolean someAdvertisementChanged; // indicates whether 
					      // some advertisement changed

}
