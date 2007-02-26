/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * This interface provides access to the context in which a given task
 * is running. Tasks either run in the context of an application or in
 * the context of the system itself.
 * <p>
 * Note that from the point of view of a <code>Service</code>, there is no
 * visibility into different contexts. Each <code>Service</code> instance
 * runs in exactly one context, and interacts with other
 * <code>Service</code>s running in the same context. Likewise, an
 * application doesn't have any ability to see other applications. It
 * runs in a single context, and needs only know how to resolve its
 * managers. That said, <code>Service</code>s do need to be able to
 * identify their own context (e.g., for scheduling tasks).
 * <p>
 * All implementations of <code>KernelAppContext</code> must implement
 * <code>hashCode</code> and <code>equals</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface KernelAppContext
{

}
