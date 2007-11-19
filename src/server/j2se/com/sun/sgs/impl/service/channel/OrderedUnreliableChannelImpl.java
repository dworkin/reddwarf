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
import java.util.Set;
import java.util.logging.Level;

class OrderedUnreliableChannelImpl extends ChannelImpl {
    
    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;

    OrderedUnreliableChannelImpl(ChannelState state) {
	super(state);
    }
    
    protected void sendToAllMembers(final byte[] channelMessage) {
	logger.log(Level.FINEST, "sendToAllMembers channel:{0}", state.name);
	long localNodeId = getLocalNodeId();
	final byte[] channelIdBytes = state.channelIdBytes;
	final byte[] protocolMessage =
	    getChannelMessage(channelMessage);
	for (final long nodeId : state.getChannelServerNodeIds()) {
	    Set<ClientSession> recipients =
		state.getSessions(nodeId);
	    if (nodeId == localNodeId) {
		for (ClientSession session : recipients) {
		    sendProtocolMessageOnCommit(session, protocolMessage);
		}
		
	    } else {
		final ChannelServer server = state.getChannelServer(nodeId);
		final byte[][] recipientIds = new byte[recipients.size()][];
		int i = 0;
		for (ClientSession session : recipients) {
		    recipientIds[i++] = session.getSessionId().getBytes();
		}

		logger.log(
		    Level.FINEST,
		    "sendToAllMembers channel:{0} " +
		    "schedule task to forward to node:{1}", state.name,
		    nodeId);
		runTaskOnCommit(
		    null,
		    new Runnable() {
			public void run() {
			    try {
				logger.log(
				    Level.FINEST,
				    "sendToAllMembers channel:{0} " +
				    "forwarding to node:{1}", state.name,
				    nodeId);
				server.send(channelIdBytes, recipientIds,
					    protocolMessage, state.delivery);
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
