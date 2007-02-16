/**
 * 
 */
package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

class HelloChannelsSessionListener
    implements ClientSessionListener, Serializable
{
	private final ClientSession session;

	public HelloChannelsSessionListener(ClientSession session,
			Channel fooChan, Channel barChan) {
		this.session = session;
		// This channel is unmonitored by the server app
		fooChan.join(session, null);
		// this channel is listened to by the server app
		
		barChan.join(session, 
				new HelloChannelsChannelListener(session));
	}

	public void disconnected(boolean graceful) {
		HelloChannels.logger.info("User "+session.getName()+" has logged out.");

	}

	public void receivedMessage(byte[] message) {
		HelloChannels.logger.info("Received direct packet from user.");
		// this line is the only difference from HelloUser2
		session.send(message);

	}

}