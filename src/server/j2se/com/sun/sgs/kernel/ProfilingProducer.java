
package com.sun.sgs.kernel;


/**
 * This interface is used by any component that wants to produce data
 * associated with tasks that are running through the scheduler. The data
 * is used for a variety of scheduler optimization and general reporting
 * operations. For <code>Service</code>s and Managers, simply implementing
 * this interface will guarentee that they are registered correctly, if
 * profiling is enabled and if that <code>Service</code> or Manager is
 * supposed to provide runtime data.
 */
public interface ProfilingProducer {

    /**
     * Tells this <code>ProfilingProducer</code> how to report its profiling
     * data by using the provided <code>ProfilingConsumer</code>. This method
     * is called at most once, and only if profiling is enabled.
     *
     * @param profilingConsumer the <code>ProfilingConsumer</code> to use in
     *                          reporting data to the system
     */
    public void setProfilingConsumer(ProfilingConsumer profilingConsumer);

}
