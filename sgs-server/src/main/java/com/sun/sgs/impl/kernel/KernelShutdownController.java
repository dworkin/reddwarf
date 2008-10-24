package com.sun.sgs.impl.kernel;

/**
 * This is a singleton which is created by the {@code Kernel} and passed to
 * the {@code WatchdogService} when it is created. This object allows the
 * {@code Kernel} to be referenced when a shutdown of the node is necessary,
 * such as when a service on the node has failed or has become inconsistent.
 * While this class is declared with public visibility, it can only be
 * instantiated with package access. In fact, the only class which should be
 * concerned with creating an instance is the {@code Kernel}.
 * <p>
 * The {@code WatchdogService} has access to the {@code shutdownNode()} method
 * when it decides that the node is to be shut down. This decision of whether
 * to shut down the node or not is left to be implemented. The default
 * behavior is to shut down the node when a service has reported a failure.
 * Future implementations may have the {@code WatchdogService} assess the
 * severity of the failure and make a decision based on the information.
 */
public final class KernelShutdownController {

    /**
     * A reference to the {@code Kernel} used for call-back of the
     * {@code Kernel.shutdown()} method
     */
    private final Kernel kernel;

    /** The singleton reference of the {@code KernelShutdownController} */
    private static KernelShutdownController ctrl = null;

    /**
     * Retrieves the instance of the {@code KernelShutdownController}
     * 
     * @param kernel the {@code Kernel} instance
     * @return a singleton of the {@code KernelShutdownController}
     */
    static KernelShutdownController getSingleton(Kernel kernel) {
	if (ctrl == null) {
	    ctrl = new KernelShutdownController(kernel);
	}
	return ctrl;
    }

    /**
     * Private constructor called by the {@code getInstance()} method to
     * create an instance of the {@code KernelShutdownController}.
     * 
     * @param kernelRef the {@code Kernel} reference
     */
    private KernelShutdownController(Kernel kernel) {
	this.kernel = kernel;
    }

    /**
     * This method instructs the {@code Kernel} to shutdown the node, as a
     * result of a failure reported to the {@code WatchdogService}.
     */
    public void shutdownNode() {
	kernel.shutdown();
    }

}
