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

package com.sun.multicast.reliable.applications.slinger;

import java.lang.IllegalArgumentException;
import java.util.ResourceBundle;
import java.text.MessageFormat;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public class GeneralUtils {

    /**
     * Do not use, all-static class.
     */
    public GeneralUtils() {}

    /**
     * A constant indicating a document should be shown in the current frame.
     * It is the second parameter for the method:
     * java.applet.AppletContext.showDocument(URL, String).
     * It is provided here for general use.
     * @see 
     * java.applet.AppletContext#showDocument(java.net.URL, java.lang.String)
     */
    public static String frameTarget_self = "_self";

    /**
     * A constant indicating a document should be shown in the parent frame.
     * It is the second parameter for the method:
     * java.applet.AppletContext.showDocument(URL, String).
     * It is provided here for general use.
     * @see 
     * java.applet.AppletContext#showDocument(java.net.URL, java.lang.String)
     */
    public static String frameTarget_parent = "_parent";

    /**
     * A constant indicating a document should be shown in the topmost frame.
     * It is the second parameter for the method:
     * java.applet.AppletContext.showDocument(URL, String).
     * It is provided here for general use.
     * @see 
     * java.applet.AppletContext#showDocument(java.net.URL, java.lang.String)
     */
    public static String frameTarget_top = "_top";

    /**
     * A constant indicating a document should be shown in a new unnamed
     * top-level window.
     * It is the second parameter for the method:
     * java.applet.AppletContext.showDocument(URL, String).
     * It is provided here for general use.
     * @see 
     * java.applet.AppletContext#showDocument(java.net.URL, java.lang.String)
     */
    public static String frameTarget_blank = "_blank";

    /**
     * Compares two objects passed in for equality.
     * Handle null objects.
     * @param objectA one of the objects to be compared
     * @param objectB one of the objects to be compared
     */
    public static boolean objectsEqual(Object objectA, Object objectB) {
        if (objectA == null) {
            return (objectB == null);
        } 

        return objectA.equals(objectB);
    }

    /**
     * Checks to make sure the percent parameter is in range.
     * @exception IllegalArgumentException
     * if the specified percentage value is unacceptable
     */
    public static void checkValidPercent(double percent) 
            throws IllegalArgumentException {
        if (percent > 1 || percent < 0) {
            Object[] args = {
                new Double(percent)
            };

            throw new IllegalArgumentException(MessageFormat.format(
		errors.getString("InvalidPercent2"), args));
        }
    }

    /**
     * Remove a character at the given index from the given string
     * @param String string the string to remove a character from.
     * @param int index the index of the character to remove.
     * @return the string without the character at the given index.
     * If the string is null or empty, null or empty will be returned.
     * If the index is out of bounds, the orignial string is returned.
     */
    public static String removeCharAtIndex(String string, int index) {
        if (string == null || string == "") {
            return string;
        } 

        int length = string.length();

        if (index > length || index < 0) {
            return string;
        } 

        String left = index > 0 ? string.substring(0, index) : "";
        String right = index + 1 < length ? string.substring(index + 1) : "";

        return left + right;
    }

    static protected ResourceBundle errors = 
        ResourceBundle.getBundle("SlingerResources");
}

