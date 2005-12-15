/**
 *
 * <p>Title: TimerManagerListener.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.timer;

import java.io.Serializable;

import com.sun.gi.logic.SimTask;

/**
 *
 * <p>Title: TimerManagerListener.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface TimerManagerListener extends Serializable {
	public void timerEvent(SimTask task, long eventID);
}
