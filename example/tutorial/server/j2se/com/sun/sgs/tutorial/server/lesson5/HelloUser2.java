package com.sun.sgs.tutorial.server.lesson5;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

public class HelloUser2 implements AppListener, Serializable {

	final static Logger logger = 
		Logger.getLogger("sgs.tutorial.server");

	public ClientSessionListener loggedIn(ClientSession session) {
		//user has  logged in
		logger.info("User "+session.getName()+" has logged in.");
		return new HelloUserSessionListener(session);
	}

	public void initialize(Properties props) {
		logger.info("Hello World!");

	}


}
