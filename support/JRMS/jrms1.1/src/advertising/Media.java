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
 * Media.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;

/**
 * Media object for SAP
 */
class Media implements Cloneable {

    /**
     * Creates a Media object for an advertisement
     */
    Media(String name) {
        setName(name);
    }

    /**
     * Clones the specified Media object
     */
    public Object clone() {
        Media newMedia = new Media(getName());

        newMedia.setProtocol(getProtocol());
        newMedia.setFormat(getFormat());
        newMedia.setBandwidth(getBandwidth());

        try {
            newMedia.setAdvertisedAddress(getAdvertisedAddress());
        } catch (java.net.UnknownHostException e) {
            return null;
        }

        newMedia.setPort(getPort());
        newMedia.setAdvertisedTTL(getAdvertisedTTL());
        newMedia.setAttributes(getAttributes());

        return (Object) newMedia;
    }

    /**
     * gets the media name.
     * @return the name of the media
     */
    String getName() {
        return name;
    }

    /**
     * gets the protocol associated with the media
     * @return the protocol used
     */
    String getProtocol() {
        return protocol;
    }

    /**
     * gets the media format.
     * @return the media format
     */
    String getFormat() {
        return format;
    }

    /**
     * gets the bandwidth that should be used for the media.
     * @return the bandwidth for the media
     */
    int getBandwidth() {
        return bandwidth;
    }

    /**
     * gets the advertised address.
     * @return the advertised address
     */
    InetAddress getAdvertisedAddress() {
        if (advertisedAddress == null) {
            return null;
        } 

        InetAddress advertised;

        advertised = advertisedAddress;

        try {
            advertised.getByName(advertisedAddress.getHostAddress());
        } catch (java.net.UnknownHostException e) {
            advertised = null;
        }

        return (advertised);
    }

    /**
     * gets the port number.
     */
    int getPort() {
        return port;
    }

    /**
     * gets the time-to-live value.
     */
    int getAdvertisedTTL() {
        return ttl;
    }

    /**
     * gets media-specific attributes.
     */
    String getAttributes() {
        return attr;
    }

    /**
     * sets the media name.
     * @param s name string.
     */
    void setName(String s) {
        name = s;
    }

    /**
     * sets the protocol for the media.
     * @param s protocol string.
     */
    void setProtocol(String s) {
        protocol = s;
    }

    /**
     * sets the media format.
     * @param s format string.
     */
    void setFormat(String s) {
        format = s;
    }

    /**
     * sets the bandwidth for the media.
     * @param b bandwidth to use.
     */
    void setBandwidth(int b) {
        bandwidth = b;
    }

    /**
     * sets the advertised address.
     * @param String advertised address
     */
    void setAdvertisedAddress(String addrString) 
            throws java.net.UnknownHostException {
        advertisedAddress.getByName(addrString);
    }

    /**
     * sets the advertised address.
     * @param object advertised address
     */
    void setAdvertisedAddress(InetAddress address) 
            throws java.net.UnknownHostException {
        advertisedAddress = address;
    }

    /**
     * sets the port.
     * @param port port number of the session
     */
    void setPort(int i) {
        port = i;
    }

    /**
     * sets the ttl for the media.
     * @param i ttl to use.
     */
    void setAdvertisedTTL(int i) {
        ttl = i;
    }

    /**
     * sets media-specific attributes.
     * @param s attribute string.
     */
    void setAttributes(String s) {
        attr = s;
    }

    private String name = null;                         // name of the media
    private String protocol = null;                     // protocol to use
    private String format = null;                       // format
    private int bandwidth = 0;                          // bandwidth
    private InetAddress advertisedAddress = null;       // advertised address
    private int port = 0;                               // port number
    private int ttl = 1;                                // time-to-live
    private String attr = null;                         // attributes
}

