
package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.ProfiledOperation;
import com.sun.sgs.kernel.ProfilingConsumer;
import com.sun.sgs.kernel.ProfilingProducer;


/**
 * This simple implementation of <code>ProfilingConsumer</code> is paired
 * with a <code>ProfilingConsumer</code> and reports all data to a
 * backing <code>ProfilingCollector</code>.
 */
public class ProfilingConsumerImpl implements ProfilingConsumer {

    // the name of the associated consumer
    private final String producerName;

    // the collector that aggregates our data
    private final ProfilingCollector profilingCollector;

    /**
     * Creates an instance of <code>ProfilingConsumerImpl</code>.
     *
     * @param profilingProducer the associated <code>ProfilingProducer</code>
     * @param profilingCollector the backing <code>ProfilingCollector</code>
     */
    public ProfilingConsumerImpl(ProfilingProducer profilingProducer,
                                 ProfilingCollector profilingCollector) {
        if (profilingProducer == null)
            throw new NullPointerException("The producer must not be null");
        if (profilingCollector == null)
            throw new NullPointerException("The collector must not be null");

        this.producerName = profilingProducer.getClass().getName();
        this.profilingCollector = profilingCollector;
    }

    /**
     * {@inheritDoc}
     */
    public ProfiledOperation registerOperation(String name) {
        return profilingCollector.registerOperation(name, producerName);
    }

}
