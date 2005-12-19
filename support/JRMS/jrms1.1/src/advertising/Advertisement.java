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
 * Advertisement.java
 */
package com.sun.multicast.advertising;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import com.sun.multicast.util.Util;

/**
 * An Advertisement object.  This class is used for received as well as
 * transmitted advertisements.  
 */
public class Advertisement extends Object implements Cloneable {

    /**
     * Creates an advertisement for an address and TTL.
     * @param address the multicast address to advertise
     * @param ttl the time-to-live to use in the Advertisement
     */
    public Advertisement(InetAddress address, 
                         int ttl) throws java.net.UnknownHostException {

        // fill in SAP data

        setOwner("-");              // default for owner
        setName("JRMS");
        setAdvertisedAddress(address);
        setAdvertisedTTL(ttl);
        setId(System.currentTimeMillis() / 1000);       // use timestamps for id
        setOriginAddress(InetAddress.getLocalHost());
        AdvertisementInit();        // perform common intialization
    }

    /**
     * Creates an Advertisement object based on a received SAP packet.
     * @param dPacket DatagramPacket containing the SAP message
     * @exception InvalidAdvertisementException if the received advertisement
     * cannot be parsed.
     */
    Advertisement(DatagramPacket dPacket) 
            throws InvalidAdvertisementException, 
                   java.net.UnknownHostException {
        AdvertisementInit();        // fill in some common info

        advertisementChanged = false;

        ResourceBundle resources = ResourceBundle.getBundle(
	    "com.sun.multicast.advertising.resources.SAPResources");

        // check for our favorite SD settings

        byte SAPdata[] = dPacket.getData();
        int length = dPacket.getLength();

        if (length <= 8) {
            throw new InvalidAdvertisementException(
		resources.getString("noData"));      // no data
        } 

        byte header = SAPdata[0];

        // check for a valid version number

        if (((header & 0xff) >> 5) > 1) {
            throw new InvalidAdvertisementException(
		resources.getString("invalidVersionNumber"));
        } 

        // listen for announcements and deletions

        if ((header & 0x1c) == 0) {
            deletion = false;
        } else if (((header & 0x1c) >> 2) == SAPDELETE) {
            deletion = true;
        } else {
            throw new InvalidAdvertisementException(
		resources.getString("notAnAnnouncement"));
        }

        // encryption not supported

        if ((header & 0x02) != 0) {
            throw new InvalidAdvertisementException(
		resources.getString("encryptionSpecified"));
        } 

        // no compression either

        if ((header & 0x01) != 0) {
            throw new InvalidAdvertisementException(
		resources.getString("compressionSpecified"));
        } 

        // no authentication

        if ((SAPdata[1] & 0xff) != 0) {
            throw new InvalidAdvertisementException(
		resources.getString("authenticationSpecified"));
        } 

        // no message hash either

        if ((((SAPdata[2] & 0xff) << 8) + (SAPdata[3] & 0xff)) != 0) {
            throw new InvalidAdvertisementException(
		resources.getString("messageHash"));
        } 

        // pick up the source address
        // this will be replaced if the 'o' option is found

        setOriginAddress(Util.intToInetAddress(Util.readInt(SAPdata, 4)));

        // start here

        int offset = 8;

        while (offset < length) {

            // pull out each element of the message
            // find the '0a' at the end of this element

            int end;
            boolean endFound;

            for (end = offset, endFound = false; end < length; end++) {
                if (SAPdata[end] == 0x0a) {
                    endFound = true;

                    break;
                }
            }

            if (!endFound) {

                // packet is too short

                throw new InvalidAdvertisementException(
		    resources.getString("tooShort"));
            }

            // isolate this element

            String element = new String(SAPdata, offset, end - offset);

            if (element.length() == 0) {
                continue;                           // skip extra 0a's
            } 

            unpackElement(element, resources);      // unpack it

            offset = end + 1;                       // skip to the next one
        }

        // if the start time has already passed, discard

        if ((getEndTime() != null) && (getEndTime().getTime() > 0) && 
	    getEndTime().before(new Date())) {

            throw new InvalidAdvertisementException(
		resources.getString("timeExpired") + getName() + " " + 
		getEndTime());
        }
    }

    /**
     * Common intialization for all advertisements to be sent
     */
    private void AdvertisementInit() throws java.net.UnknownHostException {
        setLastTime(new Date());

        mediaList = new Vector();
        attributeList = new Vector();
        changeListenerList = new Vector();
        advertisementCount = 0;
        latestTransmission = new Date();
        previousTransmission = new Date();

        try {
            SAPListener = Listener.getListener();
        } catch (java.net.UnknownHostException e) {}
    }

    /**
     * Determines whether or not it is time to send an advertisement.
     * If so, an advertisement is sent, and the send interval is recalculated
     * @param advertisingAddress the address to which to send the Advertisement
     * @param mSocket the multicast socket to use to send the Advertisement
     */
    void advertise(InetAddress advertisingAddress, MulticastSocket mSocket, 
                   int elapsedTime) throws java.io.IOException {

        // check that the time is right

        if (waitTime <= elapsedTime) {

            // time to send one

            byte advertBuffer[] = getSAPData(false);    // format the SAP data

            if (advertBuffer == null) {

                // 
                // Try again, this time requesting minimal data.
                // 

                advertBuffer = getSAPData(true);
            }
            if (advertBuffer != null) {

                // the info is small enough to fit)

                advertisementSize = advertBuffer.length;   // save the length

                DatagramPacket dp = new DatagramPacket(advertBuffer, 
                                                       advertBuffer.length, 
                                                       advertisingAddress, 
                                                       SD_PORT);

                mSocket.send(dp, (byte) advertisedTTL);    // send the packet
            }

            advertisementCount++;
            previousTransmission = latestTransmission;     // save old timestamp
            latestTransmission = new Date();
	    // recalculate send interval
            waitTime = recalculateInterval(advertisingAddress); 
        } else {
            waitTime -= elapsedTime;                       // wait some more
        }
    }

    /**
     * Stops advertising  by sending a SAP deletion packet
     * @param advertisingAddress the address to which to send the Advertisement
     * @param mSocket the multicast socket to use to send the Advertisement
     */
    void stopAdvertising(InetAddress advertisingAddress, 
                         MulticastSocket mSocket) {

        // first put the SAP info into a StringBuffer

        StringBuffer SAPEncodeBuffer = new StringBuffer();

        SAPEncodeBuffer.append("o=" + getOwner() + " " +        // owner
        (SAPid + NTPConstant) + " " + (version + NTPConstant) + " IN IP4 " 
        + originAddress.getHostAddress() + "\n");

        // encode this part of the packet using UTP-8

        byte SAPEncodeBytes[] = new byte[SAPEncodeBuffer.length()];

        System.arraycopy(SAPEncodeBuffer.toString().getBytes(), 0, 
                         SAPEncodeBytes, 0, SAPEncodeBuffer.length());

        try {
            String SAPEncodeString = new String(SAPEncodeBuffer);

            SAPEncodeBytes = SAPEncodeString.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // now allocate a byte buffer to hold the whole delete packet

        byte deleteBuffer[] = new byte[SAPEncodeBytes.length + 8];

        // fill in the version

        deleteBuffer[0] = (SAPVersion << 5) + (SAPDELETE << 2);

        // fill in the originating address

        System.arraycopy(originAddress.getAddress(), 0, deleteBuffer, 4, 4);

        // fill in the encoded bytes

        System.arraycopy(SAPEncodeBytes, 0, deleteBuffer, 8, 
                         SAPEncodeBytes.length);

        DatagramPacket dp = new DatagramPacket(deleteBuffer, 
                                               deleteBuffer.length, 
                                               advertisingAddress, SD_PORT);

        try {
            mSocket.send(dp, (byte) advertisedTTL);     // send the packet
        } catch (java.io.IOException e) {}
    }

    /**
     * Returns the number of times the Advertisement has been transmitted
     * @return the number of times the Advertisement has been transmitted
     */
    public long getAdvertisementCount() {
        return advertisementCount;
    }

    /**
     * Returns the number of seconds between the last two transmissions 
     * of this Advertisement
     * @return the number of seconds between the last two transmissions 
     * of this Advertisement
     */
    public int getCurrentAdvertisementInterval() {
        return ((int)
	    (latestTransmission.getTime() - previousTransmission.getTime()) / 
	    1000);
    }

    /**
     * Returns the Date of the last transmission of this Advertisement
     * @return the timestamp of the last transmission of this Advertisement
     */
    public Date getAdvertisementTimestamp() {
        return (new Date(latestTransmission.getTime()));
    }

    /**
     * Recalculates the send interval for an advertisement
     * @param advertisingAddress the address to which the Advertisement is sent
     */
    int recalculateInterval(InetAddress advertisingAddress) {

        // get the current count of advertisements on this address

        int currentAdCount = 
            SAPListener.getCurrentAdCount(advertisingAddress);

        // figure the new interval based on the allowed bandwidth

        int newInterval = 8 * currentAdCount * advertisementSize 
                          / SAPbandwidth;

        // can't be less than the default interval

        if (newInterval < DefaultInterval) {
            newInterval = DefaultInterval;
        } 

        //
        // XXX For testing, let's make sure that we advertise frequently.
        //
        if (newInterval > 60)
                newInterval = 60;

        // now make the interval a little fuzzy
        // first get a random number between 0 and 1

        double random = Math.random();

        // now make it a random number between 2/3 and 4/3

        random += 1;
        random *= 2;
        random /= 3;

        // now multiply the random number by the new interval, and 
	// put it back into an int

        newInterval = (int) Math.rint(newInterval * random);
        return (newInterval);
    }

    /**
     * Parses an element of a SAP message.
     * @param element a substring of the SAP message delimited by '0a's
     * @exception InvalidAdvertisementException if the received advertisement
     * cannot be parsed.
     */
    private synchronized void unpackElement(String element, 
	ResourceBundle resources) throws InvalidAdvertisementException, 
	java.net.UnknownHostException {

        int i, k;               // space to play with
        Media media = null;     // holds the last media we parsed

        if (element == null) {      // just ignore blank elements
            return;
        } 

        switch (element.charAt(0)) {

        case '#':               // comment
            break;

        case 'a':               // attribute
            if (media == null) {

                // add them to the attributes

                addAttribute(element.substring(2));
            } else {

                // add them to the latest media entry

                media.setAttributes(media.getAttributes() + " " 
                                    + element.substring(2));
            }

            break;

        case 'b':               // bandwidth
            if (media == null) {
                try {
                    setBandwidth(Integer.parseInt(element.substring(2)));
                } catch (NumberFormatException e) {
                    setBandwidth(-1);
                }
            } else {
                try {
                    media.setBandwidth(Integer.parseInt(element.substring(2)));
                } catch (NumberFormatException e) {
                    media.setBandwidth(-1);
                }
            }

            break;

        case 'c':               // connection information
            k = element.lastIndexOf(" ");  // find it between last space and a /
            i = element.indexOf("/");

	    if ((k == -1) || (i == -1))
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);

	    if (i <= k+1)
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);

            if (media == null) {
                setAdvertisedAddress(element.substring(k + 1, i));
            } else {
                media.setAdvertisedAddress(element.substring(k + 1, i));
            }

            int ttl;

            try {               // the ttl is after the /
                ttl = (byte) Integer.parseInt(element.substring(i + 1));
            } catch (NumberFormatException e) {
                ttl = 1;        // use 1 if there's no good ttl
            }

            if (media == null) {
                setAdvertisedTTL(ttl);
            } else {
                media.setAdvertisedTTL(ttl);
            }

            break;

        case 'e':               // Email address
            setEMailAddress(element.substring(2));

            break;

        case 'i':               // description
            setInfo(element.substring(2));

            break;

        case 'k':               // encryption key  - not supported
            break;

        case 'p':               // phone number
            setPhone(element.substring(2));

            break;

        case 'm':               // media

            // media name starts at offset 2 and ends at the first space

            if ((i = element.indexOf(' ')) == -1) {
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);
            } 

	    if (i <= 2)
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);

            media = new Media(element.substring(2, i));

            // port ends at the next space after the media name

            k = element.indexOf(' ', i + 1);

	    if (k == -1)
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);

            if (k > 0) {
                try {
                    media.setPort(Integer.parseInt(element.substring(i + 1, 
                            k)));
                } catch (NumberFormatException e) {
                    media.setPort(0);
                }
            }

            // protocol ends at the next space after the port

            i = k + 1;          // start after the next space
            k = element.indexOf(' ', i + 1);

	    if (k == -1)
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);

            if (k > 0) {
                media.setProtocol(element.substring(i, k));

            // format is just after the last space

		if (k >= element.length() - 2)
                throw new InvalidAdvertisementException(
		    resources.getString("invalidMedia") + element);

		media.setFormat(element.substring(k + 1, element.length() - 1));
	    }

            mediaList.addElement(media);

            break;

        case 'o':               // originator

            // owner starts at offset 2 and ends at the first space

            if ((i = element.indexOf(' ')) == -1) {
                throw new InvalidAdvertisementException(
		    resources.getString("invalidOwner") + element);
            } 

            setOwner(element.substring(2, i));

            // SAP id ends at the next space after the owner

            k = element.indexOf(' ', i + 1);

            if (k > 0) {
                try {
                    setId(Long.parseLong(element.substring(i + 1, k)) 
                          - NTPConstant);
                } catch (NumberFormatException e) {
                    throw new InvalidAdvertisementException(
			resources.getString("invalidOwner") + element);
                }
            }

            // version ends at the next space after the SAP id

            i = k + 1;          // start after the next space
            k = element.indexOf(' ', i + 1);

            if (k > 0) {
                try {
                    setVersion(Long.parseLong(element.substring(i, k)) 
                               - NTPConstant);
                } catch (NumberFormatException e) {
                    throw new InvalidAdvertisementException(
			resources.getString("invalidOwner") + element);
                }
            }

            // originator address is just after the last space

            k = element.lastIndexOf("IP4 ");

            if (k > 0) {
                setOriginAddress(
		    element.substring(k + 4, element.length() - 1));
            }

            break;

        case 'r':               // repeat time - not supported
            break;

        case 's':               // session name
            setName(element.substring(2));

            break;

        case 't':               // times
            i = element.indexOf(" ");

            long startTime = 0;
            long endTime = 0;

            try {
                startTime = Long.parseLong(element.substring(2, i));
                endTime = Long.parseLong(element.substring(i + 1));
            } catch (NumberFormatException e) {}

            if (startTime == 0) {
                setStartTime(null);
            } else {
                setStartTime(startTime);
            }
            if (endTime == 0) {
                setEndTime(null);
            } else {
                setEndTime(endTime);
            }

            break;

        case 'u':               // URL
            setUrl(element.substring(2));

            break;

        case 'v':               // version
            break;

        case 'z':               // time zone info - not supported
            break;

        default: 
            break;
        }
    }

    /**
     * Formats an advertisement into a SAP buffer
     * @return a byte array of the formatted SAP data
     */
    private byte[] getSAPData(boolean minimalSize) {

        // first put all of the SAP info into two StringBuffers

        StringBuffer SAPEncodeBuffer = new StringBuffer();
        StringBuffer SAPStringBuffer = new StringBuffer();

        SAPEncodeBuffer.append("v=0\n");        // version

        // see if we need a new version number

        if (advertisementChanged) {
            setVersion(System.currentTimeMillis() / 1000);

            advertisementChanged = false;
        }

        SAPEncodeBuffer.append("o=" + getOwner() + " " +        // owner
        (SAPid + NTPConstant) + " " + (version + NTPConstant) + " IN IP4 " 
        + originAddress.getHostAddress() + "\n");

        SAPEncodeBuffer.append("s=" + getName() + "\n");        // session name

        if (minimalSize == false) {
            SAPEncodeBuffer.append("i=" + getInfo() + "\n");    // description
        } else {
            SAPEncodeBuffer.append("i=\n");        // description
	}

        if (minimalSize == false && getUrl() != null && getUrl().length() > 0) {
            SAPEncodeBuffer.append("u=" + getUrl() + "\n");     // URL
        } else {
            SAPEncodeBuffer.append("u=\n");     // URL
	}

        if (minimalSize == false && getEMailAddress() != null 
                && (getEMailAddress().length() > 0)) {
	    // email addr
            SAPEncodeBuffer.append("e=" + getEMailAddress() + "\n"); 
        } else {
            SAPEncodeBuffer.append("e=\n"); // email addr
	}

	if (getPhone() != null && getPhone().length() > 0) { 
            SAPEncodeBuffer.append("p=" + getPhone() + "\n"); // phone number
        } else {
            SAPEncodeBuffer.append("p=\n");
	}

        SAPStringBuffer.append("c=IN IP4 " 
                               + getAdvertisedAddress().getHostAddress() 
                               + "/" + getAdvertisedTTL() + "\n");

        if (getBandwidth() != 0) {
            SAPStringBuffer.append("b=" + getBandwidth() + "\n");
        } 

        long startSeconds = 0, endSeconds = 0;

        if (getStartTime() != null) {
            startSeconds = (getStartTime().getTime() / 1000) + NTPConstant;
        } 
        if (getEndTime() != null) {
            endSeconds = (getEndTime().getTime() / 1000) + NTPConstant;
        } 

        SAPStringBuffer.append("t=" + startSeconds + " " + endSeconds + "\n");

        // add attributes

        int i;
        String attribute;

        for (i = 0; i < attributeList.size(); i++) {
            attribute = (String) attributeList.elementAt(i);

            SAPStringBuffer.append("a=" + attribute + "\n");
        }

        // add media

        Media media;

        for (i = 0; i < mediaList.size(); i++) {
            media = (Media) mediaList.elementAt(i);

            SAPStringBuffer.append("m=" + media.getName() + " " 
                                   + media.getPort() + " " 
                                   + media.getProtocol() + " " 
                                   + media.getFormat() + "\n");

            if (media.getAdvertisedAddress() != null) {
                SAPStringBuffer.append("c=IN IP4 " 
                            + media.getAdvertisedAddress().getHostAddress() 
                            + "/" + media.getAdvertisedTTL() + "\n");
            } 
            if (media.getBandwidth() != 0) {
                SAPStringBuffer.append("b=" + media.getBandwidth() + "\n");
		
            } 
            if (media.getAttributes() != null) {
                SAPStringBuffer.append("a=" + media.getAttributes() + "\n");
            } 
        }

        // encode this part of the packet using UTP-8

        byte SAPEncodeBytes[] = new byte[SAPEncodeBuffer.length()];

        System.arraycopy(SAPEncodeBuffer.toString().getBytes(), 0, 
                         SAPEncodeBytes, 0, SAPEncodeBuffer.length());

        try {
            String SAPEncodeString = new String(SAPEncodeBuffer);

            SAPEncodeBytes = SAPEncodeString.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // make sure it's not too long

	if (SAPEncodeBytes.length + SAPStringBuffer.length() > 1454) {

            int k = (SAPEncodeBytes.length + SAPStringBuffer.length());

            System.out.println(
		"SAP data size is more than 1454 bytes!! Size is " + k);

	    if (minimalSize) {
		System.out.println("Unable to send advertisement for " +
		    getName());
	    }

            return null;
        }

	if (minimalSize) {
	    System.out.println("Sending minimal advertisement (" +
		(SAPEncodeBytes.length + SAPStringBuffer.length()) + 
	 	") for " + getName());
	}

        // now allocate a byte buffer to hold the whole SAP packet

        byte SAPPacket[] = 
            new byte[SAPEncodeBytes.length + SAPStringBuffer.length() + 8];

        // fill in the version

        SAPPacket[0] = SAPVersion << 5;

        // fill in the originating address

        System.arraycopy(originAddress.getAddress(), 0, SAPPacket, 4, 4);

        // fill in the encoded bytes

        System.arraycopy(SAPEncodeBytes, 0, SAPPacket, 8, 
                         SAPEncodeBytes.length);

        // fill in the rest of the SAP data

        System.arraycopy(SAPStringBuffer.toString().getBytes(), 0, SAPPacket, 
                         SAPEncodeBytes.length + 8, SAPStringBuffer.length());

        return (SAPPacket);
    }

    /**
     * Adds a listener for any change in this Advertisement.
     * @param changelistener an instance of AdvertisementChangeListener
     * @return <code>true</code> if the Advertisement exists;
     * <code>false</code> otherwise
     */
    public synchronized boolean addAdvertisementChangeListener(
	AdvertisementChangeListener changeListener) {

        // find an advertisement on one of our lists that matches

        Advertisement advert;

        if ((advert = SAPListener.findOriginalAdvertisement(this)) == null) {
            return false;
        } 

        advert.addListener(changeListener, this);

        return true;
    }

    /**
     * Removes a listener for any change in this Advertisement.
     * @param changelistener an instance of AdvertisementChangeListener
     * @return <code>true</code> if the Advertisement exists;
     * <code>false</code> otherwise
     */
    public synchronized boolean removeAdvertisementChangeListener(
	AdvertisementChangeListener changeListener) {

        // find an advertisement on one of our lists that matches

        Advertisement advert;

        if ((advert = SAPListener.findOriginalAdvertisement(this)) == null) {
            return false;
        } 

        advert.removeListener(changeListener);

        return true;
    }

    /**
     * See if an advertisement matches this one
     * @param advertisement Advertisement to test for match
     * @return <code>true</code> if the advertisements match;
     * <code>false</code> otherwise
     */
    synchronized boolean matches(Advertisement newAdvert) {

        boolean theyMatch = true;

	// possibly this advertisement doesn't include an owner - check for null
        if (((this.getOwner() == null) && (newAdvert.getOwner() == null)) ||
        ((this.getOwner() != null) && (newAdvert.getOwner() != null) &&
        (this.getOwner().compareTo(newAdvert.getOwner()) == 0)))
	    // owners match
	    theyMatch = true;
	else
	    return false;
	
	if (this.getId() != newAdvert.getId())
	    // id's don't match
	    return false;

	if (this.getOriginAddress().equals(newAdvert.getOriginAddress()))
            return true;

        return false;
    }

    /**
     * Adds a listener to the vector of listeners.
     * @param changelistener an instance of AdvertisementChangeListener
     */
    synchronized void addListener(AdvertisementChangeListener changeListener, 
                                  Advertisement copy) {
        changeListenerList.addElement(changeListener);

        // check to see if advertisement has changed in the meantime

        if (copy.getVersion() < getVersion()) {

            // make a copy of the new advertisement

            Advertisement newAdvert = (Advertisement) this.clone();

            // create an event object now

            changeListener.advertisementChange(
		new AdvertisementChangeEvent(this, newAdvert));
        }
    }

    /**
     * Removes a listener from the vector of listeners.
     * @param changelistener an instance of AdvertisementChangeListener
     */
    synchronized void removeListener(
	AdvertisementChangeListener changeListener) {

        changeListenerList.removeElement(changeListener);
    }

    /**
     * Replaces an advertisement with a new version
     * @param advertisement new advertisement
     */
    synchronized void replace(Advertisement newAdvert) {

        // tell any listeners that there was a change

        AdvertisementChangeListener changeListener;
        int j;

        // create an event object

        for (j = 0; j < changeListenerList.size(); j++) {
            Advertisement newAdvertCopy = (Advertisement) newAdvert.clone();
            AdvertisementChangeEvent ace = new AdvertisementChangeEvent(this, 
                    newAdvertCopy);

            changeListener = 
                (AdvertisementChangeListener) changeListenerList.elementAt(j);

            changeListener.advertisementChange(ace);
        }

        // inform anyone listening for any advertisement changes

        SAPListener.informListenersChange(newAdvert);
    }

    synchronized void delete() {

        // tell any listeners that there was a deletion

        AdvertisementChangeListener changeListener;
        int j;

        // create an event object

        for (j = 0; j < changeListenerList.size(); j++) {
            Advertisement advertCopy = (Advertisement) this.clone();
            AdvertisementChangeEvent ace = new AdvertisementChangeEvent(this, 
                    advertCopy);

            changeListener = 
                (AdvertisementChangeListener) changeListenerList.elementAt(j);

            changeListener.advertisementDelete(ace);
        }

        // inform anyone listening for any advertisement changes

        SAPListener.informListenersDelete(this);
    }

    /**
     * Clones this Advertisement object
     */
    public Object clone() {
        Advertisement newAdvertisement;

        try {
            newAdvertisement = new Advertisement(getAdvertisedAddress(), 
                                                 getAdvertisedTTL());
        } catch (java.net.UnknownHostException e) {
            return null;
        }

        newAdvertisement.setId(getId());
        newAdvertisement.setVersion(getVersion());
        newAdvertisement.setOriginAddress(getOriginAddress());
        newAdvertisement.setOwner(getOwner());
        newAdvertisement.setName(getName());
        newAdvertisement.setInfo(getInfo());
        newAdvertisement.setUrl(getUrl());
        newAdvertisement.setEMailAddress(getEMailAddress());
        newAdvertisement.setPhone(getPhone());
        newAdvertisement.setStartTime(getStartTime());
        newAdvertisement.setEndTime(getEndTime());
        newAdvertisement.setBandwidth(getBandwidth());
        newAdvertisement.setAdvertisementCount(getAdvertisementCount());
        newAdvertisement.setLatestTransmission(getAdvertisementTimestamp());
        newAdvertisement.setPreviousTransmission(getPreviousTransmission());

        int i;

        for (i = 0; i < attributeList.size(); i++) {
            newAdvertisement.addAttribute(
		(String) attributeList.elementAt(i));
        }

        Media media, clonedMedia;

        for (i = 0; i < mediaList.size(); i++) {
            media = (Media) mediaList.elementAt(i);
            clonedMedia = (Media) media.clone();

            newAdvertisement.setMedia(clonedMedia);
        }

        return (Object) newAdvertisement;
    }

    /**
     * gets the session id.
     * @return the id for the session
     */
    public long getId() {
        return SAPid;
    }

    /**
     * gets the version.
     * @return the version of the announcement
     */
    public long getVersion() {
        return version;
    }

    /**
     * gets the address of the announcer.
     * @return the address of the source of the announcement
     */
    public InetAddress getOriginAddress() {
        if (originAddress == null) {
            return null;
        } 

        InetAddress origin;

        origin = originAddress;

        try {
            origin.getByName(originAddress.getHostAddress());
        } catch (java.net.UnknownHostException e) {
            origin = null;
        }

        return (origin);
    }

    /**
     * gets the owner name.
     * @return the owner of the session
     */
    public String getOwner() {
        return owner;
    }

    /**
     * gets the session name.
     * @return the name of the session
     */
    public String getName() {
        return name;
    }

    /**
     * gets the session description.
     * @return the session description
     */
    public String getInfo() {
        return info;
    }

    /**
     * gets the url.
     * @return the url for the session
     */
    public String getUrl() {
        return url;
    }

    /**
     * gets the email address of the owner.
     * @return email the e-mail address of the originator
     */
    public String getEMailAddress() {
        return eMailAddress;
    }

    /**
     * gets the phone number of the owner.
     * @return the phone number of the originator
     */
    public String getPhone() {
        return phone;
    }

    /**
     * gets the start time of the session.
     * @return the start time of the session
     */
    public Date getStartTime() {
        if (startTime == null) {
            return null;
        } 

        return (new Date(startTime.getTime()));
    }

    /**
     * gets the end time of the session.
     * @return the end time of the session
     */
    public Date getEndTime() {
        if (endTime == null) {
            return null;
        } 

        return (new Date(endTime.getTime()));
    }

    /**
     * gets the session bandwidth.
     * @return the session bandwidth
     */
    public int getBandwidth() {
        return bandwidth;
    }

    /**
     * gets the global attributes of the session.
     * @return an array of attributes for the session
     */
    public String[] getAttributes() {
        int i;
        String attributeArray[] = new String[attributeList.size()];

        attributeList.copyInto(attributeArray);

        return attributeArray;
    }

    /**
     * gets the time the announcement of this session was heard.
     * @return the last time an announcement was heard for the session
     */
    public Date getLastTime() {
        if (lastTime == null) {
            return null;
        } 

        return (new Date(lastTime.getTime()));
    }

    /**
     * gets the advertised address.
     */
    public InetAddress getAdvertisedAddress() {
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
     * gets the advertised TTL.
     * @return the time-to-live to be used with the session address
     */
    public int getAdvertisedTTL() {
        return (advertisedTTL);
    }

    /**
     * gets the array of media.
     * @return array of media objects
     */
    public synchronized Media[] getMedia() {
        int i, j;
        Media media, clonedMedia;
        Media mediaArray[] = new Media[mediaList.size()];

        for (i = 0, j = 0; i < mediaList.size(); i++) {
            media = (Media) mediaList.elementAt(i);

            if ((clonedMedia = (Media) media.clone()) != null) {
                mediaArray[j++] = clonedMedia;
            } 
        }

        return mediaArray;
    }

    /**
     * retrieves the timestamp of the previous transmission
     * @return time of the previous transmission
     */
    Date getPreviousTransmission() {
        return (new Date(previousTransmission.getTime()));
    }

    /**
     * sets the id.
     * @param id session id.
     */
    public void setId(long id) {
        SAPid = id;

        setAdvertisementChanged();
    }

    /**
     * sets the version.
     * @param version version
     */
    void setVersion(long i) {
        version = i;
    }

    /**
     * sets the originator address.
     * @param addr InetAddress of the originator.
     */
    public void setOriginAddress(InetAddress addr) {
        originAddress = addr;

        // figure out the bandwidth (bytes/sec.) these announcements 
        // are allowed to use.  for administratively-scoped addresses, 
	// use 500bps

        if (originAddress.getHostAddress().startsWith("239")) {
            SAPbandwidth = 500;
        }       // admin scope
        else if (getAdvertisedTTL() < 16) {
            SAPbandwidth = 2000;
        } else if (getAdvertisedTTL() < 128) {
            SAPbandwidth = 1000;
        } else {
            SAPbandwidth = 200;
        }

        setAdvertisementChanged();
    }

    /**
     * sets the originator address.
     * @param address IP address of the originator.
     */
    public void setOriginAddress(String addrString) 
            throws java.net.UnknownHostException {
        originAddress.getByName(addrString);

        // figure out the bandwidth (bytes/sec.) these announcements 
	// are allowed to use. for administratively-scoped addresses, use 500bps

        if (originAddress.getHostAddress().startsWith("239")) {
            SAPbandwidth = 500;
        }       // admin scope
        else if (getAdvertisedTTL() < 16) {
            SAPbandwidth = 2000;
        } else if (getAdvertisedTTL() < 128) {
            SAPbandwidth = 1000;
        } else {
            SAPbandwidth = 200;
        }

        setAdvertisementChanged();
    }

    /**
     * sets the owner name.
     * @param s owner name.
     */
    public void setOwner(String s) {
        owner = s;

        setAdvertisementChanged();
    }

    /**
     * sets the session name.
     * @param s session name.
     */
    public void setName(String s) {
        name = s;

        setAdvertisementChanged();
    }

    /**
     * sets the session description.
     * @param s info about the session.
     */
    public void setInfo(String s) {
        info = s;

        setAdvertisementChanged();
    }

    /**
     * sets the url associated with the session.
     * @param s url associated with the session.
     */
    public void setUrl(String s) {
        url = s;

        setAdvertisementChanged();
    }

    /**
     * sets the owner email address.
     * @param s email address.
     */
    public void setEMailAddress(String s) {
        eMailAddress = s;

        setAdvertisementChanged();
    }

    /**
     * sets the owner phone number.
     * @param s phone number.
     */
    public void setPhone(String s) {
        phone = s;

        setAdvertisementChanged();
    }

    /**
     * sets the start time of the session.
     * @param start start time.
     */
    public void setStartTime(Date start) {
        startTime = start;

        setAdvertisementChanged();
    }

    /**
     * sets the start time of the session.
     * @param start start time (NTP time).
     */
    public void setStartTime(long start) {
        startTime = new Date(1000 * (start - NTPConstant));

        setAdvertisementChanged();
    }

    /**
     * sets the end time of the session.
     * @param end end time.
     */
    public void setEndTime(Date end) {
        endTime = end;

        setAdvertisementChanged();
    }

    /**
     * sets the end time of the session.
     * @param end end time (NTP time).
     */
    public void setEndTime(long end) {
        endTime = new Date(1000 * (end - NTPConstant));

        setAdvertisementChanged();
    }

    /**
     * sets the last time the Advertisement was heard.
     * @param last last time the Advertisement was heard
     */
    void setLastTime(Date last) {
        lastTime = last;
    }

    /**
     * sets the last time the Advertisement was transmitted.
     * @param latest last time the Advertisement was transmitted
     */
    void setLatestTransmission(Date latest) {
        latestTransmission = latest;
    }

    /**
     * sets the previous time the Advertisement was transmitted.
     * @param previous previous time the Advertisement was transmitted
     */
    void setPreviousTransmission(Date previous) {
        previousTransmission = previous;
    }

    /**
     * sets the session bandwidth.
     * @param bwidth the bandwidth of the session
     */
    public void setBandwidth(int bwidth) {
        bandwidth = bwidth;

        setAdvertisementChanged();
    }

    /**
     * adds an attribute to this Advertisement.
     * @param s session attribute to add
     */
    public void addAttribute(String s) {
        attributeList.addElement(s);
        setAdvertisementChanged();
    }

    /**
     * removes an attribute.
     * @param s session attribute to remove
     */
    public void removeAttribute(String s) {
        int i;

        for (i = 0; i < attributeList.size(); i++) {
            if (s.compareTo((String) attributeList.elementAt(i)) == 0) {
                attributeList.removeElementAt(i);
                setAdvertisementChanged();

                return;
            }
        }
    }

    /**
     * sets the advertised address.
     * @param address advertised address
     */
    public void setAdvertisedAddress(InetAddress address) {
        advertisedAddress = address;

        setAdvertisementChanged();
    }

    /**
     * sets the advertised address.
     * @param addrString advertised address
     */
    public void setAdvertisedAddress(String addrString) 
            throws java.net.UnknownHostException {
        advertisedAddress.getByName(addrString);
        setAdvertisementChanged();
    }

    /**
     * sets the advertised TTL.
     * @param ttl time-to-live for this Advertisement
     */
    public void setAdvertisedTTL(int ttl) {
        advertisedTTL = ttl;

        setAdvertisementChanged();
    }

    /**
     * adds a media entry.
     * @param media Media object
     */
    public synchronized void setMedia(Media media) {
        mediaList.addElement(media);
        setAdvertisementChanged();
    }

    /**
     * determines whether or not the Advertisement is a deletion.
     * @return <code>true</code> if the Advertisement is a deletion;
     * <code>false</code> otherwise
     */
    boolean isDeletion() {
        return deletion;
    }

    /**
     * marks the Advertisement as having been changed.
     * 
     */
    private void setAdvertisementChanged() {
        advertisementChanged = true;
    }

    /**
     * sets the Advertisement count.
     * @param count advertisement count
     */
    void setAdvertisementCount(long count) {
        advertisementCount = count;
    }

    private static final int SD_PORT = 9875;  // official port number for SAP v1
    private static final int MAXSAPSIZE = 2048;
    private static final int SAPDELETE = 1;
    private static final int SAPVersion = 1;
    private static final long NTPConstant = 2208988800L;
    private static final int DefaultInterval = 300;     // five minutes
    private static final int OneInterval = 10;   // one tick from the Advertiser
    private long SAPid;                   // id number
    private long version;                 // version number
    private InetAddress originAddress;    // address of the sender
    private String owner = null;          // owner of the session
    private String name = null;           // name of the session
    private String info = null;           // more info about the session
    private String url = null;            // url for the session
    private String eMailAddress = null;   // e-mail address for the session
    private String phone = null;          // phone number
    private Date startTime = null;        // start time
    private Date endTime = null;          // end time
    private Date lastTime;                // time we last heard an advertisement
    private InetAddress advertisedAddress;  // multicast address advertised
    private Vector attributeList;    // list of attributes
    private Vector mediaList;        // list of media
    private int advertisedTTL;       // TTL to use for this advertisement
    private int bandwidth;           // bandwidth for this session
    private long advertisementCount; // number of times advertisement was sent
    private Date latestTransmission;        // timestamp of latest transmission
    private Date previousTransmission;     // timestamp of previous transmission
    private boolean deletion;       // indicates whether or not it's a deletion
    private Vector changeListenerList;  // list of change listeners
    private boolean advertisementChanged;
    private int waitTime;         // number of ticks left til next advertisement
    private int SAPbandwidth;     // bandwidth allowed for advertisement address
    private int advertisementSize;  // size of the last advertisement sent out
    static Listener SAPListener;

}
