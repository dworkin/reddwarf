/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.app;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * An annotation that instructs the system to associate a new owning identity
 * with a task. All tasks run in the system are owned by some identity,
 * and by default a calling task's owning identity also owns any tasks that
 * are scheduled (using the methods on {@code TaskManager}) from that calling
 * task. Using this annotation informs the system that a new owning identity
 * should be created and used instead. This is typically useful when
 * scheduling work that represents the start of some new behavior or actor
 * in the system.
 * <p>
 * Note that this annotation does not have the {@code Inherited} annotation.
 * For a task to be run with a new owning identity, the concrete class of
 * the task must have the {@code RunWithNewIdentity} annotation as the
 * annotation will not be inherited from a superclass or implemented interface.
 *
 * @see TaskManager
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface RunWithNewIdentity {

}
