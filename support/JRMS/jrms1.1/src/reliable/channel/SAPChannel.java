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
 * SAPChannel.java
 */
package com.sun.multicast.reliable.channel;

import java.util.Date;
import java.util.ResourceBundle;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import com.sun.multicast.util.BadBASE64Exception;
import com.sun.multicast.util.BASE64Encoder;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.advertising.Advertisement;
import com.sun.multicast.advertising.InvalidAdvertisementException;
import com.sun.multicast.advertising.AdvertisementChangeEvent;
import com.sun.multicast.advertising.AdvertisementChangeListener;

/**
 * An implementation of <code>Channel</code> for channels received via SAP.
 * This class is package-local because it should only be created through the
 * <code>LocalPCM</code> class and accessed through the methods in
 * the <code>Channel</code> interface.
 * 
 * <P>This class is derived from ControlledChannel and relies on the methods
 * and fields of that class, for the most part. The SAPChannel class has
 * code for creating the channel from an Advertisement and updating it if
 * the Advertisement changes.
 * @see                         Channel
 * @see                         ControlledChannel
 * @see                         Advertisement
 * @see                         LocalPCM
 */
class SAPChannel extends ControlledChannel implements 
    AdvertisementChangeListener {

    /**
     * Creates a new <code>SAPChannel</code> that updates a channel based on an
     * <code>Advertisement</code>.
     * <P>This constructor is private because LocalPCM should use the 
     * createSAPChannel method.
     * @param channel the channel to be updated
     * @param advertisement the advertisement that the SAPChannel should be 
     * based on
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    private SAPChannel(LocalChannel channel, Advertisement advertisement) 
            throws InvalidAdvertisementException {
        // super(channel, ControlledChannel.ACCESS_READ_ONLY);
	super(channel, ControlledChannel.ACCESS_FULL);

        chan = channel;
        ourAd = advertisement;

        updateFromAdvertisement(ourAd);
        ourAd.addAdvertisementChangeListener(this);
    }

    /**
     * Get the first attribute in an Advertisement that begins with a prefix.
     * @param ad the Advertisement
     * @param prefix the prefix
     * @return the text that appears after the prefix (null if prefix not found)
     */
    static String adGetAttribute(Advertisement ad, String prefix) {
        String[] attrs = ad.getAttributes();

        for (int i = 0; i < attrs.length; i++) {
            if (attrs[i].startsWith(prefix)) {
                return (attrs[i].substring(prefix.length()));
            } 
        }

        return (null);
    }

    /**
     * Get the first attribute in an Advertisement that begins with a prefix.
     * Throw an exception if not found.
     * @param ad the Advertisement
     * @param prefix the prefix
     * @return the text that appears after the prefix
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the prefix does not appear in any of the attributes
     */
    static String adGetAttributeRequired(Advertisement ad, String prefix) 
            throws InvalidAdvertisementException {
        String result = adGetAttribute(ad, prefix);

        if (result == null) {
            throw new InvalidAdvertisementException();
        } 

        return (result);
    }

    /**
     * Tests if an Advertisement refers to a channel.
     * @param ad the Advertisement to be tested
     * @return <code>true</code> if the advertisement refers to a channel;
     * <code>false</code> otherwise
     */
    static boolean adIsChannel(Advertisement ad) {
        return (adGetAttribute(ad, CHANNEL_ID_ATTR) != null);
    }

    /**
     * Gets the channel ID referred to in an Advertisement.
     * @param ad the Advertisement
     * @return the channel ID
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    static long adGetChannelID(Advertisement ad) 
            throws InvalidAdvertisementException {
        if (ad == cachedAd) {
            return (cachedID);
        } 

        long result;

        try {
            result = Long.parseLong(adGetAttributeRequired(ad, 
                    CHANNEL_ID_ATTR));
        } catch (NumberFormatException e) {
            throw new InvalidAdvertisementException();
        }

        cachedAd = ad;
        cachedID = result;

        return (result);
    }

    /**
     * Gets the cipherMode referred to in an Advertisement.
     * @param ad the Advertisement
     * @return the CipherMode
     * @exception com.sun.multicast.reliable.sap.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    static boolean adGetCipherMode(Advertisement ad) 
            throws InvalidAdvertisementException {
        Boolean bool = new Boolean(adGetAttributeRequired(ad, 
                CIPHER_MODE_ATTR));

        return bool.booleanValue();
    }

    /**
     * Gets the transport profile referred to in an Advertisement.
     * @param ad the Advertisement
     * @return the transport profile
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    static TransportProfile adGetTProfile(Advertisement ad) 
            throws InvalidAdvertisementException {
        try {
            String tpString = adGetAttributeRequired(ad, 
                                                     TRANSPORT_PROFILE_ATTR);
            TransportProfile tp = 
                (TransportProfile) Util.readObject(
		BASE64Encoder.decode(tpString.getBytes("UTF8")));

            return (tp);
        } catch (BadBASE64Exception e) {
            throw new InvalidAdvertisementException();
        } catch (UnsupportedEncodingException e) {
            throw new InvalidAdvertisementException();
        }
    }

    /**
     * Gets any additional advertised data referred to in an Advertisement.
     * If there is none, returns null.
     * @param ad the Advertisement
     * @return the additional advertised data (or null)
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    static String adGetAdditionalAdvertisedData(Advertisement ad) 
            throws InvalidAdvertisementException {
        String aadString = adGetAttribute(ad, 
                                          ADDITIONAL_ADVERTISED_DATA_ATTR);

        if (aadString != null) {
            try {
                aadString = 
                    new String(BASE64Encoder.decode(
			aadString.getBytes("UTF8")), "UTF8");
            } catch (BadBASE64Exception e) {
                throw new InvalidAdvertisementException();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return (aadString);
    }

    /**
     * Gets a Long object containing the channel ID.
     * 
     * <P>This method isn't public. It's just an optimization used by the 
     * LocalPCM to avoid creating a new Long object whenever it wants to put 
     * a channel ID into a Hashtable or Vector.
     * @return a Long object containing the channel ID
     */
    Long getChannelIDObject() {
        return (chan.getChannelIDObject());
    }

    /**
     * Create a SAPChannel based on an Advertisement.
     * This method is not public because it should only be used by the LocalPCM.
     * @param ad the Advertisement
     * @return the channel
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    static SAPChannel createSAPChannel(Advertisement ad) 
            throws InvalidAdvertisementException {

        // Get basic data out of ad.

        long channelID = adGetChannelID(ad);
        LocalChannel local = new LocalChannel(null, channelID);

        // Create the SAPChannel

        return (new SAPChannel(local, ad));
    }

    /**
     * Update this SAPChannel based on an Advertisement.
     * @param ad the Advertisement
     * @exception com.sun.multicast.advertising.InvalidAdvertisementException 
     * if the advertisement is not a valid channel advertisement
     */
    private void updateFromAdvertisement(Advertisement ad) 
            throws InvalidAdvertisementException {
        TransportProfile tProfile = adGetTProfile(ad);
        boolean cipherMode = adGetCipherMode(ad);

        // System.out.println("Received CipherMode as " + cipherMode);

        try {
            chan.setApplicationName(ad.getOwner());
            chan.setChannelName(ad.getName());
            chan.setDataStartTime(ad.getStartTime());
            chan.setSessionEndTime(ad.getEndTime());
            chan.setTransportProfile(tProfile);
            // chan.setDynamicFilterList(ad.getDynamicFilterList());

            if (cipherMode == true) {
                chan.enableCipher();
            } else {
                chan.disableCipher();
            }

            chan.setAbstract(ad.getInfo());
            chan.setContactName(ad.getEMailAddress());
            chan.setAdditionalAdvertisedData(adGetAdditionalAdvertisedData(ad));
        } catch (UnauthorizedUserException e) {
            throw new InvalidAdvertisementException();
        } catch (InvalidChannelException e) {
            throw new InvalidAdvertisementException();
        }
    }

    /**
     * Handle a change in the advertisement for this channel.
     * @param ace an AdvertisementChangeEvent describing the change
     */
    public void advertisementChange(AdvertisementChangeEvent ace) {
        Advertisement newAd = ace.getChangedAdvertisement();

        ourAd.removeAdvertisementChangeListener(this);

        ourAd = newAd;

        try {
            updateFromAdvertisement(ourAd);
        } catch (InvalidAdvertisementException e) {}

        ourAd.addAdvertisementChangeListener(this);
    }

    /**
     * Handle the deletion of the advertisement for this channel.
     * @param ace an AdvertisementChangeEvent describing the change
     */
    public void advertisementDelete(AdvertisementChangeEvent ace) {

    // @@@ If the deletion was due to an implicit timeout, we should keep 
    // the channel around and start watching for a new advertisement with 
    // the same channel id to hook up again.
    // @@@ If the deletion was due to an explicit cancellation or explicit 
    // timeout, we should destroy the channel.

    }

    private LocalChannel chan;
    private Advertisement ourAd;
    private static Advertisement cachedAd = 
        null;                               // Cached by/for adGetChannelID
    private static long cachedID = 0;       // Cached by/for adGetChannelID
    static final String CHANNEL_ID_ATTR = 
        "jrmscid:";                   // SAP attribute prefix
    static final String TRANSPORT_PROFILE_ATTR = 
        "jrmstp:";            // SAP attribute prefix
    static final String ADDITIONAL_ADVERTISED_DATA_ATTR = 
        "jrmsad:";              // SAP attribute prefix
    static final String CIPHER_MODE_ATTR = 
        "jrmscm:";                  // SAP attribute prefix

}
