package com.sun.sgs.benchmark.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A single master task
 *
 */
public class MasterTask implements AppListener, Serializable {

    private static final String APP_KEY = "com.sun.sgs.benchmark.app";

    private static final long serialVersionUID = 0x7FADEFF;

    public MasterTask() {

    }

    public void initialize(Properties properties) {
	
	// set up the TaskFactory
	TaskFactory factory = TaskFactory.instance();

	String appPropertiesFile = properties.getProperty(APP_KEY);
	System.out.printf("loading properties file: %s\n", 
			  appPropertiesFile);
	
	Properties appProperties = new Properties();
	try {
	    appProperties.load(new FileReader(appPropertiesFile));
	}
	catch (IOException ioe) {
	    // log that we couldn't load the app
	    ioe.printStackTrace();	    
	}

	// have the TaskFactory load the appropriate modules based on
	// the properties file.
	factory.loadModules(appProperties);
    }

    public ClientSessionListener loggedIn(ClientSession session) {

	System.out.printf("client connected: \n\t%s\n", session);

	// create a new ClientSessionHandler to generate tasks
	return new RemoteMethodRequestHandler(session);
    }
    
}