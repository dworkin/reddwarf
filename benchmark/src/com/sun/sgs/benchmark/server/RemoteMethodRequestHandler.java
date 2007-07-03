package com.sun.sgs.benchmark.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.benchmark.shared.MethodRequest;

import java.io.ByteArrayInputStream;
import java.io.InvalidClassException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.StreamCorruptedException;

/**
 *
 *
 */
public class RemoteMethodRequestHandler 
    implements ClientSessionListener, ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    private final ClientSession session;

    public RemoteMethodRequestHandler(ClientSession session) {
	this.session = session;
    }

    public void receivedMessage(byte[] message) {
	System.out.printf("%s sent message\n", session);
	try {
	    ByteArrayInputStream bais = new ByteArrayInputStream(message);
	    ObjectInputStream ois = new ObjectInputStream(bais);
	    MethodRequest request = (MethodRequest)(ois.readObject());

	    // use the task factory to generate tasks on behalf of
	    // this method request, then schedule them to run
	    TaskFactory factory = TaskFactory.instance();
	    TaskManager manager = AppContext.getTaskManager();
	    for (Runnable operation : (request.isCompressed()) 
		     ? factory.getOperations(session,
					     request.getOpCode(), 
					     request.getArgs())
		     : factory.getOperations(session,
					     request.getMethodName(),
					     request.getObjectArgs())) {
		try {
		    operation.run();
		}
		// NOTE: not sure what to catch here
		catch (Exception e) {
		    // REMINDER: add logging
		    e.printStackTrace();
		}
	    }
		 
	}
	// fail silently
	catch (ClassCastException cce) { } 
	catch (ClassNotFoundException cnfe) { }
	catch (InvalidClassException ice) { }
	catch (StreamCorruptedException sce) { }
	catch (OptionalDataException ode) { }
	catch (IOException ioe) { }
    }

    public void disconnected(boolean graceful) {
	System.out.printf("%s disconnected\n", session);
    }
}