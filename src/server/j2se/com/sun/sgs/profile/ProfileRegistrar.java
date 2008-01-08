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

package com.sun.sgs.profile;


/**
 * A registration interface where <code>ProfileProducers</code>s register
 * and get <code>ProfileConsumer</code>s used to consume profiling data.
 */
public interface ProfileRegistrar {

    /**
     * Registers the given <code>ProfileProducer</code>.
     *
     * @param producer the <code>ProfileProducer</code> being registered
     *
     * @return a <code>ProfileConsumer</code> that will consume profiling
     *         data from the provided <code>ProfileProducer</code>
     */
    public ProfileConsumer registerProfileProducer(ProfileProducer producer);

}
