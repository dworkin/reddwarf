
/*
 * ManagedObject.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 6:50:46 PM
 * Desc: 
 *
 */


package com.sun.sgs;

import java.io.Serializable;


/**
 * This is the core interface used to identify any classes that may be
 * managed by the system. Managed objects may be shared throughout the
 * system, and are accessed via <code>ManagedReference</code>s.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ManagedObject extends Serializable
{



}
