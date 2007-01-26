package com.sun.sgs.test.app.tasks;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import java.io.Serializable;

import java.util.Date;
import java.util.Properties;

/**
 * An ad-hoc SGS test application that exercises the DataManager
 * and TaskManager.
 */
public class TestApplication
	implements AppListener, Serializable
{
    private static final long serialVersionUID = 1L;

    private static final String DATE_NAME = "startupDate";

    private int fooCount = 0;
    private ManagedReference fooRef;
    
    static void destroyNamedManagedObject(String name) {
        DataManager dataManager = AppContext.getDataManager();
        try {
            try {
        	ManagedObject obj =
        	    dataManager.getBinding(name, ManagedObject.class);
    	        // TODO: Should we mark it for update before removing it?
        	dataManager.removeObject(obj);
            } catch (ObjectNotFoundException e) { /* ignore */ }
            // let NameNotBoundException be caught by the outer try

            dataManager.removeBinding(name);
        } catch (NameNotBoundException e) { /* ignore */ }
    }
    
    static void destroyManagedObject(ManagedObject obj) {
	try {
	    // TODO: Should we mark it for update before removing it?
	    AppContext.getDataManager().removeObject(obj);
        } catch (ObjectNotFoundException e) { /* ignore */ }
    }
    
    static void destroyManagedReference(ManagedReference ref) {
	try {
	    if (ref == null) return;
	    AppContext.getDataManager().
	    	removeObject(ref.getForUpdate(ManagedObject.class));
        } catch (ObjectNotFoundException e) { /* ignore */ }
    }

    /**
     * {@inheritDoc}
     */
    public void initialize(Properties props) {
        DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);

        System.out.println("fooCount = " + fooCount);
        fooCount++;

        try {
            ManagedDate date =
                dataManager.getBinding(DATE_NAME, ManagedDate.class);
            System.out.println("Originally started on " + date);
        } catch (NameNotBoundException nnbe) {
            ManagedDate start = new ManagedDate();
            System.out.println(
        	    "Starting fresh on " + start);
            dataManager.setBinding(DATE_NAME, start);
            fooRef = dataManager.createReference(new Foo("Karl"));
        }
        
        TaskManager taskManager = AppContext.getTaskManager();

        Task task =  new SerializableTask(fooRef);

        taskManager.scheduleTask(task);
        taskManager.scheduleTask(task, 3800);
        PeriodicTaskHandle handle =
            taskManager.schedulePeriodicTask(task, 2000, 100);
        PeriodicTaskHandle handle2 =
            taskManager.schedulePeriodicTask(task, 3050, 100);
        taskManager.scheduleTask(new CancelTask(handle, handle2), 4010);
        taskManager.scheduleTask(new RequestShutdownTask(), 6000);
    }

    /**
     * {@inheritDoc}
     */
    public ClientSessionListener loggedIn(ClientSession session) {
	// Reject any client login attempts.
        return null;
    }

    // FIXME: this used to be shuttingDown, but we took that out.
    // When should we call cleanup(), or should we drop that part
    // of this test? -JM
    void cleanup() {
        destroyManagedReference(fooRef);
        destroyNamedManagedObject(DATE_NAME);
    }

    static class Foo implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1L;
        private final String name;
        private int count;
        Foo(String name) {
            this.name = name;
        }
        
        void incrementCounter() {
            ++count;
        }

        /**
         * {@inheritDoc}
         */
        public String toString() {
            return String.format("[%s:%d]", name, count);
        }
    }

    static class ManagedDate extends Date implements ManagedObject {
	private static final long serialVersionUID = 1L;
    }

    static class SerializableTask implements Task, Serializable {
	private static final long serialVersionUID = 1L;
        private final ManagedReference fooRef;

        Foo getFoo() {
            //return fooRef.getForUpdate(Foo.class);
            return fooRef.get(Foo.class);
        }

        SerializableTask(ManagedReference fooRef) {
            this.fooRef = fooRef;
        }

        /**
         * {@inheritDoc}
         */
        public void run() throws Exception {
            Foo foo = getFoo();
            //foo.incrementCounter();
            System.out.format("[%d] Foo = %s%n",
        	    System.currentTimeMillis(), foo);
        }
    }

    static class CancelTask implements Task, Serializable {
	private static final long serialVersionUID = 1L;
        private final PeriodicTaskHandle handle;
        private final PeriodicTaskHandle handle2;

        CancelTask(PeriodicTaskHandle handle,
        	PeriodicTaskHandle handle2) {
            this.handle = handle;
            this.handle2 = handle2;
        }

        /**
         * {@inheritDoc}
         */
        public void run() throws Exception {
            System.out.println("cancel periodic tasks");
            handle.cancel();
            handle2.cancel();
        }
    }
    
    static class RequestShutdownTask implements Task, Serializable {
	private static final long serialVersionUID = 1L;
        
        /**
         * {@inheritDoc}
         */
        public void run() throws Exception {
            System.out.println("requesting shutdown");
            //com.sun.sgs.impl.kernel.AppShutdownTask.requestShutdown();
        }
    }
}
