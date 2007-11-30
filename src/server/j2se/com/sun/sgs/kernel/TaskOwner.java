/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.auth.Identity;


/**
 * This interface provides details about the owner of a task. All tasks run
 * through the <code>TaskScheduler</code> have a <code>TaskOwner</code>. The
 * owner may be a user (i.e., a <code>ClientSession</code>), or it may be some
 * component of the system (e.g., a <code>Service</code>).
 * <p>
 * All implementations of <code>TaskOwner</code> must implement
 * <code>hashCode</code> and <code>equals</code>.
 */
public interface TaskOwner
{

    /**
     * Returns the context in which this <code>TaskOwner</code>'s tasks run.
     *
     * @return the <code>TaskOwner</code>'s <code>KernelAppContext</code>
     */
    public KernelAppContext getContext();

    /**
     * Returns the <code>Identity</code> for this <code>TaskOwner</code>.
     *
     * @return this task's owner's <code>Identity</code>
     */
    public Identity getIdentity();

}
