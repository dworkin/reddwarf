package com.sun.gi.apps.trivial;

import java.lang.reflect.Method;

import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TrivialBoot implements SimBoot {
  /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final boolean TEST_GLO_PARAM=false;
	private int count=1;

	public TrivialBoot() {
	}

	public void boot(SimTask task,boolean firstBoot) {
		if (firstBoot){
			System.out.println("First boot of trivial test");
		}
		System.out.println("Ran TrivialBoot.boot "+(count++)+ " times");
		if (TEST_GLO_PARAM){
			System.out.println("Testing queue of a GLO as a task parmaq (should throw exception");
			Method m;
			try {
				m = getClass().getMethod("illegalTask",new Class[]{SimTask.class,
					TrivialBoot.class});
				task.queueTask(task.makeReference(this),m,new Object[] {this});
			} catch (SecurityException e) {
				
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				
				e.printStackTrace();
			} catch (InstantiationException e) {
				
				e.printStackTrace();
			}
			
		}		
	}
	
	public void illegalTask(SimTask task, TrivialBoot boot){
		System.err.println("Should not be here");
	}

}
