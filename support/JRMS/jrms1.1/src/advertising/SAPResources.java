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
 * SAPResources.java
 */
package com.sun.multicast.advertising.resources;

import java.util.ListResourceBundle;

/**
 * A ListResourceBundle containing US English resources for the SAP classes.
 * 
 * This class should not be used by code outside of the 
 * com.sun.multicast package.
 */
public class SAPResources extends ListResourceBundle {

    /**
     * Returns the contents of the SAP resources object
     * @return contents of the SAP resources object
     */
    public Object[][] getContents() {
        return contents;
    }

    static final Object[][] contents = {
        {
            "noData", "No data in announcement"
        }, {
            "invalidVersionNumber", 
            "Announcement has an invalid version number"
        }, {
            "notAnAnnouncement", "Received packet is not an announcement"
        }, {
            "encryptionSpecified", "Encryption specified"
        }, {
            "compressionSpecified", "Compression specified"
        }, {
            "authenticationSpecified", "Authentication specified"
        }, {
            "messageHash", "Message hash specified"
        }, {
            "tooShort", "Announcement is too short"
        }, {
            "timeExpired", "Announcement time has expired"
        }, {
            "invalidMedia", "Announcement contains invalid media"
        }, {
            "invalidOwner", "Announcement contains invalid owner"
        }, {
            "invalidConnection", 
	    "Announcement contains invalid connection information"
        }
    };

}
