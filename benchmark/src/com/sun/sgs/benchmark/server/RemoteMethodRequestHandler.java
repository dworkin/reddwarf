package com.sun.sgs.benchmark.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.benchmark.app.BehaviorException;
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

    public static void runMethodRequest(MethodRequest request,
        ClientSession session)
    {
        if (DEBUG) System.out.printf("Executing request: %s\n", request);
        
        // use the task factory to generate tasks on behalf of
        // this method request, then schedule them to run
        TaskFactory factory = TaskFactory.instance();
        TaskManager manager = AppContext.getTaskManager();
        List<Runnable> operations;
        
        try {
            if (request.hasObjectArgs()) {
                operations = factory.getOperations(session,
                    request.getMethodName(),
                    request.getObjectArgs());
            } else {
                operations = factory.getOperations(session,
                    request.getMethodName(),
                    request.getByteArgs());
            }
        } catch (BehaviorException e) {
            System.err.println(request.getMethodName() + ": " + e.toString());
            return;
        } catch (UnsupportedOperationException e) {
            System.err.println(e);
            return;
        }
        
        for (Runnable operation : operations) {
            try {
                operation.run();
            }
            catch (Exception e) {
                if (e instanceof ExceptionRetryStatus) {
                    /**
                     * Not really an error since this can happen during normal
                     * operations, so just ignore.
                     */
                } else {
                    // REMINDER: add logging
                    e.printStackTrace();
                }
            }
        }
    }

    // Implement ClientSessionListener

    public void receivedMessage(byte[] message) {
        if (DEBUG) System.out.printf("Received %d bytes from client %s\n",
            message.length, session);
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(message);
	    ObjectInputStream ois = new ObjectInputStream(bais);
	    MethodRequest request = (MethodRequest)(ois.readObject());
	    runMethodRequest(request, session);
        } catch (ClassCastException cce) {
            System.err.println(cce.toString());
        } catch (ClassNotFoundException cnfe) {
            System.err.println(cnfe.toString());
        } catch (IOException ioe) {
            System.err.println(ioe.toString());
        }
    }
    
    public void disconnected(boolean graceful) {
        /**
         * Note: it is not uncommon for this to print out multiple times per
         * disconnection (particularly if many users disconnect all at once)
         * because the task may fail and retry, but this print occurs every time
         * even if the task fails.
         */
	System.out.printf("%s [%s] disconnected\n", session.getName(),
            session.toString());
    }
}
