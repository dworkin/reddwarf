/**
 * 
 */
package com.sun.sgs.tutorial.server.lesson5;

import java.io.Serializable;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

class HelloUserSessionListener
    implements ClientSessionListener, Serializable
{
	private final ClientSession session;

	public HelloUserSessionListener(ClientSession session) {
		this.session = session;
	}

	public void disconnected(boolean graceful) {
		HelloUser2.logger.info("User "+session.getName()+" has logged out.");

	}

	public void receivedMessage(byte[] message) {
		HelloUser2.logger.info("Received direct packet from user.");

	}

}