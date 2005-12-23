/**
 *
 * <p>Title: PDTimerEvent.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.gloutils.pdtimer;

import java.io.Serializable;
import java.lang.reflect.Method;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 *
 * <p>Title: PDTimerEvent.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimerEvent implements Serializable {

	/**
	 * hardwired serial version UID
	 */
	private static final long serialVersionUID = 1L;
	public boolean isActive=true;
	ACCESS_TYPE accessType;
	long delay;
	boolean repeat;
	GLOReference target;
	String methodName;
	Object[] parameters;

	
	/**
	 * @param access
	 * @param delay
	 * @param repeat
	 * @param target
	 * @param methodName
	 * @param parameters
	 */
	public PDTimerEvent(ACCESS_TYPE access, long delay, boolean repeat, GLOReference target, String methodName, Object[] parameters) {
		// TODO Auto-generated constructor stub
		this.accessType = access;
		this.delay = delay;
		this.repeat = repeat;
		this.target = target;
		this.methodName = methodName;
		this.parameters = parameters;
	}

	/**
	 * @param task
	 */
	public void fire(SimTask task) {
		if (!isActive){
			System.err.println("ERROR: Nonactive timer event fired!");
			return;
		}
		Object targetObject = target.peek(task);
		Class[] classes = new Class[parameters.length+1];
		classes[0] = SimTask.class;
		for(int i=0;i<parameters.length;i++){
			classes[i+1]=parameters[i].getClass();
		}
		try {
			Method method = targetObject.getClass().getMethod(methodName,classes);
			task.queueTask(accessType,target,method,parameters);
			isActive = false;
		} catch (SecurityException e) {
			
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			
			e.printStackTrace();
		}
		
	}

	/**
	 * @return
	 */
	public long delayTime() {		
		return delay;
	}

	/**
	 * @return
	 */
	public boolean requiresCleanup() {		
		return !isActive;
	}

	/**
	 * @return
	 */
	public boolean isRepeating() {		
		return repeat;
	}

	/**
	 * 
	 */
	public void reset() {
		isActive=true;
		
	}

	/**
	 * @return
	 */
	public boolean isMoribund() {
		return (!isActive)&&(!repeat);
	}

	/**
	 * @return
	 */
	public boolean isActive() {	
		return isActive;
	}

	

}
