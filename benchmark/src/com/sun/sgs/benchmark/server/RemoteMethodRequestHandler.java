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

import java.util.List;

/**
 *
 *
 */
public class RemoteMethodRequestHandler 
    implements ClientSessionListener, ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    private static final boolean DEBUG = false;

    private final ClientSession session;

    public RemoteMethodRequestHandler(ClientSession session) {
	this.session = session;
    }

    public void receivedMessage(byte[] message) {
	if (DEBUG) System.out.printf("%s sent message\n", session);
	try {
	    ByteArrayInputStream bais = new ByteArrayInputStream(message);
	    ObjectInputStream ois = new ObjectInputStream(bais);
	    MethodRequest request = (MethodRequest)(ois.readObject());
	    
	    if (DEBUG) System.out.println("method request: " + request);

	    // use the task factory to generate tasks on behalf of
	    // this method request, then schedule them to run
	    TaskFactory factory = TaskFactory.instance();
	    TaskManager manager = AppContext.getTaskManager();
            List<Runnable> operations;
            
            try {
                operations = (request.isCompressed()) 
                    ? factory.getOperations(session,
                        request.getOpCode(), request.getArgs())
                    : factory.getOperations(session,
                        request.getMethodName(), request.getObjectArgs());
            }
            catch (UnsupportedOperationException uoe) {
                System.out.println(uoe.toString());
                return;
            }
            
	    for (Runnable operation : operations) {
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
        catch (ClassCastException cce) {
            System.out.println(cce.toString());
        }
	catch (ClassNotFoundException cnfe) {
            System.out.println(cnfe.toString());
        }
	catch (InvalidClassException ice) {
            System.out.println(ice.toString());
        }
	catch (StreamCorruptedException sce) {
            System.out.println(sce.toString());
        }
	catch (OptionalDataException ode) {
            System.out.println(ode.toString());
        }
	catch (IOException ioe) {
            System.out.println(ioe.toString());
        }
    }

    public void disconnected(boolean graceful) {
	System.out.printf("%s disconnected\n", session);
    }
}
