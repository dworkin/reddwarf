
/*
 * ReflectiveRunnable.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Jul 10, 2006	12:16:10 AM
 * Desc: 
 *
 */

package com.sun.sgs;

import com.sun.sgs.ManagedReference;

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
    private ManagedReference reference;

    // the method to call
    private Method method;

    // the parameters passed to the method
    private Object [] parameters;

    /**
     * 
     * FIXME: The accessType should be an Enum.
     */
    public ReflectiveRunnable(int accessType, ManagedReference reference,
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
