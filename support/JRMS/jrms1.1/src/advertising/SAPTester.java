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
 * SAPTester.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.util.*;

/**
 * A tester for the SAP code.  Feel free to expand upon it.
 */
class SAPTester extends Thread implements AdvertisementChangeListener, 
                                          AllAdvertisementsChangeListener {

    SAPTester() {

        // register for advertisement change events for JoeyAdvert

        JoeyAdvert.addAdvertisementChangeListener(this);

        // register for all advertisement change events

        SAPListener.addAllAdvertisementsChangeListener(this);
    }

    /**
     * Perform the SAP Test.
     * @param args command line arguments (ignored for now).
     */
    public static void main(String[] args) {
        System.out.println("Starting SAP Test.");

        try {
            SAPListener = Listener.getListener();
            SAPAdvertiser = Advertiser.getAdvertiser();
            AshleighAdvertAddress = InetAddress.getByName(ALAdvert_ADDRESS);
            AshleighAddress = InetAddress.getByName(AL_ADDRESS);
            JoeyAddress = InetAddress.getByName(J_ADDRESS);
            PlainAddress1 = InetAddress.getByName(P1_ADDRESS);
            PlainAddress2 = InetAddress.getByName(P2_ADDRESS);

            // start listening

            SAPListener.startListening();
            SAPListener.startListening(AshleighAdvertAddress);

            // listAdvertisements();

            buildAdvertisements();
            advertise();
            sleep(2000);            // wait a while til we receive them
            new SAPTester();        // register for change event

            while (true) {
                System.out.println(
		    SAPListener.getCurrentAdCount(AshleighAdvertAddress) + 
		    " ads on 224.10.10.30  " + 
		    SAPListener.getCurrentAdCount() + 
		    " ads on 224.2.127.254");
                sleep(10000);
                changeAdvertisements();

            // listAdvertisements();

            }
        } catch (Exception e) {
            System.out.println("Caught exception" + e);
        }
    }

    static void buildAdvertisements() throws UnknownHostException {
        JoeyAdvert = new Advertisement(JoeyAddress, 1);

        // fill in SAP data

        Date startJoey = new Date();
        Date endJoey = new Date();

        endJoey.setTime(endJoey.getTime() + 600000);
        JoeyAdvert.setOwner("Slinger");
        JoeyAdvert.setName("The Joey Channel");
        JoeyAdvert.setStartTime(startJoey);
        JoeyAdvert.setEndTime(endJoey);
        JoeyAdvert.setAdvertisedAddress(JoeyAddress);
        JoeyAdvert.setAdvertisedTTL(1);
        JoeyAdvert.setInfo("Live action of Joey the cat.");
        JoeyAdvert.addAttribute("type:broadcast");

        AshleighAdvert = new Advertisement(AshleighAddress, 1);

        Date startAshleigh = new Date();
        Date endAshleigh = new Date();

        endAshleigh.setTime(endAshleigh.getTime() + 60000);

        // fill in SAP data

        AshleighAdvert.setOwner("Slinger");
        AshleighAdvert.setName("The Ashleigh Channel");
        AshleighAdvert.setStartTime(startAshleigh);
        AshleighAdvert.setEndTime(endAshleigh);
        AshleighAdvert.setAdvertisedAddress(AshleighAddress);
        AshleighAdvert.setAdvertisedTTL(1);
        AshleighAdvert.setInfo("Live action of Ashleigh the cat.");
        AshleighAdvert.addAttribute("type:broadcast");
    }

    private static void advertise() 
            throws java.io.IOException, java.lang.InterruptedException {
        SAPAdvertiser.startAdvertising(JoeyAdvert);
        SAPAdvertiser.startAdvertising(AshleighAdvertAddress, AshleighAdvert);

        // advertise some plain SAP advertisements too

        SAPAdvertiser.startAdvertising(PlainAddress1, 1, null, null);
        sleep(1000);        // wait a while
        SAPAdvertiser.startAdvertising(AshleighAdvertAddress, PlainAddress2, 1, 
                                       null, null);
    }

    private void listAdvertisements() {
        Advertisement adverts[] = SAPListener.getAllAdvertisements();
        int i;

        for (i = 0; i < adverts.length; i++) {
            Advertisement advert = adverts[i];

            displayAdvertisement(advert);
        }
    }

    private static void changeAdvertisements() {
        JoeyAdvert.setInfo("New live action of Joey the cat.");
    }

    public void advertisementChange(AdvertisementChangeEvent event) {
        Advertisement advert = event.getChangedAdvertisement();

        System.out.print("ADVERTISEMENT CHANGED:_");
        displayAdvertisement(advert);
    }

    public void advertisementDelete(AdvertisementChangeEvent event) {
        Advertisement advert = event.getChangedAdvertisement();

        System.out.print("ADVERTISEMENT DELETED:__");
        displayAdvertisement(advert);
    }

    public void allAdvertisementsAdd(AllAdvertisementsChangeEvent event) {
        Advertisement advert = event.getChangedAdvertisement();

        System.out.print("NEW ADVERTISEMENT:_____");
        displayAdvertisement(advert);
    }

    public void allAdvertisementsChange(AllAdvertisementsChangeEvent event) {
        Advertisement advert = event.getChangedAdvertisement();

        System.out.print("CHANGED ADVERTISEMENT:_");
        displayAdvertisement(advert);
    }

    public void allAdvertisementsDelete(AllAdvertisementsChangeEvent event) {
        Advertisement advert = event.getChangedAdvertisement();

        System.out.print("DELETED ADVERTISEMENT:__");
        displayAdvertisement(advert);
    }

    void displayAdvertisement(Advertisement advert) {
        System.out.println(advert.getId() + "  " + advert.getVersion() + "  " 
                           + advert.getOriginAddress().getHostAddress() 
                           + "  " + advert.getOwner() + "  " 
                           + advert.getInfo());
    }

    static Listener SAPListener;
    static Advertiser SAPAdvertiser;
    static Advertisement JoeyAdvert;
    static Advertisement AshleighAdvert;
    static InetAddress AshleighAdvertAddress;      // advertising address for Ashleigh
    static final String ALAdvert_ADDRESS = "224.10.10.30";
    static InetAddress AshleighAddress;        // address for Ashleigh
    static final String AL_ADDRESS = "224.10.10.31";
    static InetAddress JoeyAddress;        // address for Joey
    static final String J_ADDRESS = "224.10.10.32";
    static InetAddress PlainAddress1;       // address for testing
    static final String P1_ADDRESS = "224.10.10.33";
    static InetAddress PlainAddress2;       // address for testing
    static final String P2_ADDRESS = "224.10.10.34";
}

