package com.sun.sgs.tutorial.server.lesson2;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

public class HelloLogger implements AppListener, Serializable {
	static private Logger logger = 
		Logger.getLogger("sgs.tutorial.server");

	public ClientSessionListener loggedIn(ClientSession session) {
		return null;
	}

	

	public void initialize(Properties props) {
		logger.info("Hello World!");

	}

}
