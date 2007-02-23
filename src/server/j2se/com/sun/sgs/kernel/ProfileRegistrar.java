
package com.sun.sgs.kernel;


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
     *         data from the provided <code>ProfileProducer</code>, or
     *         <code>null</code> if profiling data is not being collected
     */
    public ProfileConsumer registerProfileProducer(ProfileProducer producer);

}
