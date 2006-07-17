
/*
 * ManagedRunnable.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Jul 14, 2006	 1:58:50 PM
 * Desc: 
 *
 */

package com.sun.sgs;

import com.sun.sgs.ManagedObject;


/**
 * This interface is the base for all tasks that can be executed. It is
 * simply a standard <code>Runnable</code> that is also a
 * <code>ManagedObject</code> so that it can be managed by the system.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ManagedRunnable extends ManagedObject, Runnable
{


}
