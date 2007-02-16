/**
 * 
 */
package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;

class HelloChannelsChannelListener 
	implements ChannelListener, Serializable{

	private ClientSession session;
	//private Channel channel;
	
	public HelloChannelsChannelListener(ClientSession session){
		this.session=session;
	
	}
	
	public void receivedMessage(Channel channel, ClientSession session, byte[] message) {
		HelloChannels.logger.info("Recieved msg from "+session.getName()+
				" on session "+channel.getName());
		
	}
	
}