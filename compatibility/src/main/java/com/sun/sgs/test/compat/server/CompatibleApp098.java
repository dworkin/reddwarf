package com.sun.sgs.test.compat.server;

import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.util.ScalableDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Deque;

/**
 * An application to use for checking compatibility with release 0.9.8 across
 * releases, both for persistent data structures and APIs.
 */
public class CompatibleApp098 extends CompatibleApp {
    
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** Tests an existing {@link ScalableDeque}. **/
    private static class CheckPersistentScalableDeque
	extends InitializeCheckTask
    {
	private static final long serialVersionUID = 1;
	private static final int size = 20;
	private static final String name = "scalableDeque";
	CheckPersistentScalableDeque() { }
	public void initialize() {
	    Deque<Integer> deque = new ScalableDeque<Integer>();
	    for (int i = 0; i < size; i++) {
		deque.add(i);
	    }
	    dataManager.setBinding(name, deque);
	}
	boolean runInternal() {
	    @SuppressWarnings("unchecked")
	    Deque<Integer> deque =
		(Deque<Integer>) dataManager.getBinding(name);
	    Iterator<Integer> iter = deque.iterator();
	    for (int i = 0; i < size; i++) {
		if (!iter.next().equals(i)) {
		    throw new RuntimeException("Value not found: " + i);
		}
	    }
	    return true;
	}
    }

    static {
	new CheckPersistentScalableDeque();
    }

    /** Checks the API of the ScalableDeque class. */
    private static class CheckScalableDequeTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckScalableDequeTask() { }
	boolean runInternal() {
	    ManagedObjectRemoval mor = new ScalableDeque<String>();
	    Collection<String> c = new ScalableDeque<String>(true);
	    new ScalableDeque<String>(c);
	    return true;
	}
    }

    static {
	new CheckScalableDequeTask();
    }
}
