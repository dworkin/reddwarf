/**
 *
 * <p>Title: SGSError.java</p>
 * <p>Description: This is a parent class that groups all the SGS system
 * errors that can occur during a task's opration. SGSError and its sub-clases should 
 * **never** be caught by application code.</p>

 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.logic;

/**
 *
 * <p>Title: SGSError.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class SGSError extends Error {
	public SGSError(String message){
		super(message);
	}
}
