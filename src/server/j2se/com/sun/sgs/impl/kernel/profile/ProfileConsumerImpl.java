
package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileProducer;


/**
 * This simple implementation of <code>ProfileConsumer</code> is paired
 * with a <code>ProfileProducer</code> and reports all data to a
 * backing <code>ProfileCollector</code>.
 */
public class ProfileConsumerImpl implements ProfileConsumer {

    // the name of the associated consumer
    private final String producerName;

    // the collector that aggregates our data
    private final ProfileCollector profileCollector;

    /**
     * Creates an instance of <code>ProfileConsumerImpl</code>.
     *
     * @param profileProducer the associated <code>ProfileProducer</code>
     * @param profileCollector the backing <code>ProfileCollector</code>
     */
    public ProfileConsumerImpl(ProfileProducer profileProducer,
                               ProfileCollector profileCollector) {
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

}
