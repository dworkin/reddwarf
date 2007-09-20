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

package com.sun.sgs.profile;


/**
 * This interface should be implemented by any component that wants to
 * produce data associated with tasks that are running through the scheduler.
 * The data is used for a variety of scheduler optimization and general
 * reporting operations. For <code>Service</code>s and Managers, simply
 * implementing this interface will guarentee that they are registered
 * correctly, if profiling is enabled and if that <code>Service</code> or
 * Manager is supposed to provide runtime data.
 */
public interface ProfileProducer {

    /**
     * Tells this <code>ProfileProducer</code> where to register to report
     * its profiling data.
     *
     * @param profileRegistrar the {@code ProfileRegistrar} to use in
     *                        reporting data to the system
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar);

}
