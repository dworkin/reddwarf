
package com.sun.sgs.kernel;


/**
 * This interface is used by any component that wants to consume data
 * associated with tasks that are running through the scheduler. Typically
 * each consumer is matched with a <code>ProfilingProducer</code>.
 */
public interface ProfilingConsumer {

    /**
     * Registers the named operation with this consumer, such that the
     * operation can be reported as part of a task's profile.
     *
     * @param name the name of the operation
     *
     * @return an instance of <cpde>ProfiledOperation</code>
     */
    public ProfiledOperation registerOperation(String name);

}
