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
 * ControlledChannel.java
 */
package com.sun.multicast.reliable.channel;

import com.sun.multicast.util.UnsupportedException;

/**
 * An implementation of <code>Channel</code> for channels with access control 
 * (like ones that were received via SAP). This class is package-local because 
 * it should only be created through the <code>LocalPCM</code> class and 
 * accessed through the methods in the <code>Channel</code> interface.
 * 
 * <P>This class creates a LocalChannel object to store the actual channel 
 * information.  The ControlledChannel class is primarily responsible for 
 * updating and controlling access to the LocalChannel object.
 * @see                         Channel
 * @see                         LocalPCM
 */
class ControlledChannel extends PassthroughChannel {

    /**
     * Constant that indicates full access.
     */
    static final int ACCESS_FULL = 0;

    /**
     * Constant that indicates read-only access.
     */
    static final int ACCESS_READ_ONLY = 1;

    /**
     * Creates a new <code>ControlledChannel</code> that operates on a given 
     * channel.
     * @param channel the channel to which controlled access is to be granted.
     */
    ControlledChannel(Channel channel, int accessControl) {
        super(channel);

        access = accessControl;
    }

    /**
     * Check access control before allowing a method call.
     * 
     * @param methodID an int that identifies the method being called
     * (one of CHANNEL_METHOD_*)
     * @exception UnauthorizedUserException if access is denied
     */
    void checkAccess(int methodID) throws UnauthorizedUserException {
        if (access == ACCESS_READ_ONLY) {
            if ((methodID == CHANNEL_METHOD_DESTROY) || 
		(methodID == CHANNEL_METHOD_DUPLICATE) || 
		((methodID >= CHANNEL_METHOD_SET_CHANNEL_NAME) && 
		(methodID <= CHANNEL_METHOD_REMOVE_RECEIVER)) || 
		(methodID == CHANNEL_METHOD_ADD_SENDER) || 
		(methodID == CHANNEL_METHOD_REMOVE_SENDER) || 
		(methodID == CHANNEL_METHOD_ADD_ADMINISTRATOR) || 
		(methodID == CHANNEL_METHOD_REMOVE_ADMINISTRATOR) || 
		(methodID == CHANNEL_METHOD_SET_DYNAMIC_FILTER_LIST)) {

                throw new UnauthorizedUserException();
            }
        }
    }

    private int access;

}
