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

package com.sun.sgs.impl.service.task;

/**
 * A remote service API for communicating between peer task service
 * implementations.
 */
public interface TaskServer {

    /**
     * Notifies this server that the task stored in the data store as a
     * {@link PendingTask} with the given name binding is being handed off
     * and should be run locally.
     *
     * @param objName the name binding of the {@code PendingTask} object
     *                that is to be handed off to this node
     */
    void handoffTask(String objName);

}
