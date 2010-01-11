/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
