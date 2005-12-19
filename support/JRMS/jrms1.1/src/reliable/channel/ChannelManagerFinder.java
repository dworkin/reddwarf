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
 * ChannelManagerFinder.java
 */
package com.sun.multicast.reliable.channel;

import java.rmi.Naming;
import com.sun.multicast.reliable.RMException;

/**
 * A channel manager finder. This class contains static methods that may be used
 * to find
 * <code>ChannelManager</code> and <code>PrimaryChannelManager</code> objects. 
 * The caller application obtains the channel manager's name externally 
 * (e.g. directory or web site).
 * The name should be in the form of a URL, of the form 
 * <protocol>://<host>/<pathname>.
 * If the name is null, the channel manager finder returns a local channel 
 * manager.
 * 
 * @see                         ChannelManager
 * @see                         PrimaryChannelManager
 */

// @@@ Should cache ClientPCMs returned to avoid making many 
// for the same principal.

public class ChannelManagerFinder {

    /**
     * Private constructor to disallowing instantiation.
     */
    private ChannelManagerFinder() {}

    /**
     * Get a <code>ChannelManager</code> object that goes with the supplied 
     * principal name.  If the local <code>ChannelManager</code> object is 
     * desired, pass null for the name.
     * @param principal the principal name (null to get the local 
     * <code>ChannelManager</code> object)
     * @return a <code>ChannelManager</code> object for that name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception ChannelManagerNotFoundException if the channel manager 
     * could not be found
     */
    public static ChannelManager getChannelManager(String principal) 
            throws RMException, ChannelManagerNotFoundException {
        if (principal == null) {
            return ((ChannelManager) LocalPCM.getLocalPCM());
        } else 
	    throw new ChannelManagerNotFoundException();
	//	 not yet implemented
	// {
	//    try {
	//
	//    
	//        ChannelManager cm = (ChannelManager)
	//            Naming.lookup("//" + principal + "/RemotePCM");
	//        ChannelManager cm = (ChannelManager) Naming.lookup(principal);
	//
	//        return (new ClientPCM((PrimaryChannelManager) cm));
	//    } catch (Exception e) {
	//        throw new ChannelManagerNotFoundException();
	//    }
	// }
    }

    /**
     * Get a <code>PrimaryChannelManager</code> object that goes with the 
     * supplied principal name.
     * If the local <code>PrimaryChannelManager</code> object is desired, pass 
     * null for the name.
     * @param principal the principal name (null to get the local 
     * <code>PrimaryChannelManager</code> object)
     * @return a <code>PrimaryChannelManager</code> object for that name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception ChannelManagerNotFoundException if the channel manager 
     * could not be found
     */
    public static PrimaryChannelManager getPrimaryChannelManager(
	String principal) throws RMException, ChannelManagerNotFoundException {

        if (principal == null) {
            return ((PrimaryChannelManager) LocalPCM.getLocalPCM());
        } else 
	    throw new ChannelManagerNotFoundException();
	//	    Not yet implemented  
	// {
	//    try {
	//
	//        PrimaryChannelManager pcm = (PrimaryChannelManager)
	//	      Naming.lookup("//" + principal + "/RemotePCM");
	//        PrimaryChannelManager pcm = (PrimaryChannelManager)
	//	      Naming.lookup(principal);
	//
	//        return (new ClientPCM(pcm));
	//    } catch (Exception e) {
	//        throw new ChannelManagerNotFoundException();
	//    }
	// }
    }

}
