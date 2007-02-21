package com.sun.sgs.tutorial.server.lesson1;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * This is the most basic Darkstar Server Application.  All it does is 
 * print a message when  it is started up and another when it is 
 * gracefully shut down.
 * 
 * All Darkstar apps begin with a special object called an AppListener 
 * that receives these events.
 * 
 * The AppListener is a ManagedObject and all Managed Objects must 
 * implement Serializable as well.  For the moment, just accept that as 
 * a bit of "Magic".  ManagedObjects will be explained in greater detail 
 * in a later tutorial lesson.
 * 
 * @author Jeff Kesselman
 *
 */
public class HelloWorld implements AppListener, Serializable {

	public ClientSessionListener loggedIn(ClientSession session) {
		// TODO Auto-generated method stub
		return null;
	}

	

	/**
	 * This callback is called when the app first starts up
	 */
	public void initialize(Properties props) {
		System.out.println("Hello World!");

	}

}
