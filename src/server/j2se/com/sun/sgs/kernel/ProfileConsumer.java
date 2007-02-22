
package com.sun.sgs.kernel;


/**
 * This interface should be implemented by components that accept profiling
 * data associated with tasks that are running through the scheduler.
 * Typically each consumer is matched with a <code>ProfileProducer</code>.
 */
public interface ProfileConsumer {

    /**
     * Registers the named operation with this consumer, such that the
     * operation can be reported as part of a task's profile.
     *
     * @param name the name of the operation
     *
     * @return an instance of <cpde>ProfileOperation</code>
     */
    public ProfileOperation registerOperation(String name);

}
