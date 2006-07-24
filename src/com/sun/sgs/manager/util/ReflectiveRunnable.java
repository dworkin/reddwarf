
package com.sun.sgs.manager.util;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;

import java.lang.reflect.Method;


/**
 * This is a utility class that provides the ability call an arbitrary
 * method from some task.
 * <p>
 * NOTE: This is just here as a place-holder example of how we can support
 * many of the existing Darkstar mechanisms. It needs a little work before
 * it can be generally useful.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class ReflectiveRunnable implements ManagedRunnable
{
	private static final long serialVersionUID = 1L;

	// the referenced object that will be called
    private ManagedReference<? extends ManagedObject> reference;

    // the method to call
    private Method method;

    // the parameters passed to the method
    private Object [] parameters;

    /**
     * 
     */
    public ReflectiveRunnable(
            ManagedReference<? extends ManagedObject> reference,
            Method method, Object [] parameters) {
        this.reference = reference;
        this.method = method;

        // FIXME: this probably needs to be a copy, unless we come up
        // with some kind of semantics for protecting user copies
        this.parameters = parameters;
    }

    /**
     * FIXME: not implemented
     */
    public void run() {

    }

}
