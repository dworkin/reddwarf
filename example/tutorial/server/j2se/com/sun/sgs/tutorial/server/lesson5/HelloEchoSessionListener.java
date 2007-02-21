/**
 * 
 */
package com.sun.sgs.tutorial.server.lesson5;

import java.io.Serializable;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

class HelloEchoSessionListener
    implements ClientSessionListener, Serializable
{
	private final ClientSession session;

	public HelloEchoSessionListener(ClientSession session) {
		this.session = session;
	}

	public void disconnected(boolean graceful) {
		HelloEcho.logger.info("User "+session.getName()+" has logged out.");

	}

	public void receivedMessage(byte[] message) {
		HelloEcho.logger.info("Received direct packet from user.");
		// this line is the only difference from HelloUser2
		session.send(message);

	}

}