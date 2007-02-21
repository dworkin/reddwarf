package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;


public class HelloChannels implements AppListener, Serializable {

	final static Logger logger = 
		Logger.getLogger("sgs.tutorial.server");
	
	private Channel fooChan=null;
	private Channel barChan=null;

	public ClientSessionListener loggedIn(ClientSession session) {
		//user has  logged in
		logger.info("User "+session.getName()+" has logged in.");
		return new HelloChannelsSessionListener(session,fooChan,barChan);
	}

	public void initialize(Properties props) {
		logger.info("Hello World!");
		// Create the channels.  This will only happen
		// once per restart from empty object store state
		// This is what we want.  They will exists from this
		// point on and the handles returned remain valid across
		// tasks and even system restarts
		ChannelManager chanMgr = AppContext.getChannelManager();
		fooChan =chanMgr.createChannel("Foo", null ,Delivery.RELIABLE);
		barChan =chanMgr.createChannel("Bar", null ,Delivery.RELIABLE);
	}
		

}

