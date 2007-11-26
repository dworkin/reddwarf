/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.sharedutil.HexDumper;
import java.util.Set;
import java.util.logging.Level;

class OrderedUnreliableChannelImpl extends ChannelImpl {
    
    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    OrderedUnreliableChannelImpl(Delivery delivery) {
	super(delivery);
    }
    
    protected void sendToAllMembers(final byte[] channelMessage) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "sendToAllMembers channel:{0}, message:{1}",
		this, HexDumper.format(channelMessage));
	}
	long localNodeId = getLocalNodeId();
	final byte[] protocolMessage = getChannelMessage(channelMessage);
	for (final long nodeId : getChannelServerNodeIds()) {
	    Set<ClientSession> recipients = getSessions(nodeId);
	    if (nodeId == localNodeId) {
		for (ClientSession session : recipients) {
		    sendProtocolMessageOnCommit(session, protocolMessage);
		}
		
	    } else {
		final ChannelServer server = getChannelServer(nodeId);
		final byte[][] recipientIds = new byte[recipients.size()][];
		int i = 0;
		for (ClientSession session : recipients) {
		    recipientIds[i++] = session.getSessionId().getBytes();
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "sendToAllMembers channel:{0} " +
			"schedule task to forward to node:{1}", this, nodeId);
		}
		runTaskOnCommit(
		    null,
		    new Runnable() {
			public void run() {
			    try {
				logger.log(
				    Level.FINEST,
				    "sendToAllMembers channel:{0} " +
				    "forwarding to node:{1}", this, nodeId);
				server.send(channelIdBytes, recipientIds,
					    protocolMessage, delivery);
			    } catch (Exception e) {
				// skip unresponsive channel server
				logger.logThrow(
				    Level.WARNING, e,
				    "Contacting channel server:{0} on " +
				    " node:{1} throws ", server, nodeId);
			    }
			}});
	    }
	}
    }
}
