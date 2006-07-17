
/*
 * ExternalServiceManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 7:32:10 PM
 * Desc: 
 *
 */

package com.sun.sgs.manager;


/**
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class ExternalServiceManager
{

    /**
     *
     */
    public abstract ExternalServiceManager getInstance();

    /**
     * FIXME: This needs to provide some kind of interface to external
     * services, which in turn can do things like raw IO, JNI, etc.
     */

}
