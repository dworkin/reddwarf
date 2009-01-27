package com.sun.sgs.kernel;

/**
 * An interface which defines the {@code Kernel.shutdown} hook for the
 * watchdog service.
 */
public interface KernelShutdownController {

    /**
     * This method instructs the {@code Kernel} to shutdown the node, as a
     * result of a failure reported to the {@code WatchdogService}. It is
     * made public so that services which acquire the object are able to issue
     * a shutdown to the otherwise non-visible method. If a shutdown is issued
     * from a component before the watchdog service is created, the shutdown
     * will be queue until it receives the handle. NOTE: This should be called
     * from a new thread to avoid a possible deadlock due to a service waiting 
     * for the thread that shutdown was called from.
     */
    void shutdownNode();
}
