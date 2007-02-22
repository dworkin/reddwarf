
package com.sun.sgs.kernel;


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
     * @param profileConsumer the <code>ProfileConsumer</code> to use in
     *                        reporting data to the system
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar);

}
