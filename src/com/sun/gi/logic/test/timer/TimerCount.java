/**
 *
 * <p>Title: TimerCount.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.logic.test.timer;

import java.io.Serializable;

import com.sun.gi.logic.SimTask;

/**
 *
 * <p>Title: TimerCount.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TimerCount implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8916778872579494400L;
	long tickCount=0;
	
	public void increment(SimTask task){
		
		System.out.println("Tick count = "+tickCount++);
	}
}
