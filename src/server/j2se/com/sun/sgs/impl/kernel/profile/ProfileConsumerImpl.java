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

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileCounter;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileSample;


/**
 * This simple implementation of <code>ProfileConsumer</code> is paired
 * with a <code>ProfileProducer</code> and reports all data to a
 * backing <code>ProfileCollectorImpl</code>.
 */
class ProfileConsumerImpl implements ProfileConsumer {

    // the name of the associated consumer
    private final String producerName;

    // the collector that aggregates our data
    private final ProfileCollectorImpl profileCollector;

    /**
     * Creates an instance of <code>ProfileConsumerImpl</code>.
     *
     * @param profileProducer the associated <code>ProfileProducer</code>
     * @param profileCollector the backing <code>ProfileCollectorImpl</code>
     */
    ProfileConsumerImpl(ProfileProducer profileProducer,
                        ProfileCollectorImpl profileCollector) {
        if (profileProducer == null)
            throw new NullPointerException("The producer must not be null");
        if (profileCollector == null)
            throw new NullPointerException("The collector must not be null");

        this.producerName = profileProducer.getClass().getName();
        this.profileCollector = profileCollector;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if no more operations can be registered
     */
    public ProfileOperation registerOperation(String name) {
        return profileCollector.registerOperation(name, producerName);
    }

    /**
     * {@inheritDoc}
     */
    public ProfileCounter registerCounter(String name, boolean taskLocal) {
        return profileCollector.registerCounter(name, producerName, taskLocal);
    }

    /**
     * {@inheritDoc}
     */
    public ProfileSample registerSampleSource(String name, boolean taskLocal,
					       long maxSamples) {
	return profileCollector.registerSampleSource(name, taskLocal, 
						     maxSamples);
    }

}
