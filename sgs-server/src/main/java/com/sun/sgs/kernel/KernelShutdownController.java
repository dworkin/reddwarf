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
     * a shutdown to the otherwise non-visible method.
     */
    void shutdownNode();
}
