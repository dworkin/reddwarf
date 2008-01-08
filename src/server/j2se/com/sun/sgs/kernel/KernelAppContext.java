/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 */
public interface KernelAppContext
{

}
