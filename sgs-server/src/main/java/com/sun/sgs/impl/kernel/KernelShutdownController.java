/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

/**
 * An interface for controlling Kernel shutdown. If the {@code Kernel} provides 
 * an object to a component or service, it is giving permission to that 
 * component or service to call {@link Kernel#shutdown}.
 */
public interface KernelShutdownController {

    /**
     * Instructs the {@code Kernel} to shutdown the node, as a result of a
     * failure detected in a service or a component. If this method is called 
     * during startup, it may be delayed until the Kernel is completely booted.
     * 
     * @param caller the class that called the shutdown. This is to
     * differentiate between being called from a service and being called from
     * a component.
     */
    void shutdownNode(Object caller);
}
