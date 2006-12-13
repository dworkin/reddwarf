
package com.sun.sgs.impl.app.profile;


/**
 * This interface is used by managers that want to support run-time
 * profiling for scheduler optimization and reporting capabilities. This
 * is used by default for the standard <code>ChannelManager</code>,
 * <code>DataManager</code>, and <code>TaskManager</code>. 
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface ProfilingManager {

    /**
     * Tells this <code>ProfilingManager</code> how to report its profiling
     * data by using the provided <code>ProfileReporter</code>. This method
     * is called at application startup on all managers that implement the
     * <code>ProfilingManager</code> interface, but will not be called after
     * that point.
     *
     * @param profileReporter the <code>ProfileReporter</code> to use in
     *                        reporting data to the system
     */
    public void setProfileReporter(ProfileReporter profileReporter);

}
