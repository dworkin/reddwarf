package com.sun.sgs.impl.kernel;

/**
 * An interface for controlling Kernel shutdown. If the {@code Kernel} provides 
 * an object to a component or service, it is giving permission to that 
 * component or service to call {@link Kernel#shutdown}.
 */
public interface KernelShutdownController {

    /**
     * Instructs the {@code Kernel} to shutdown the node, as a result of a
     * failure detected in a service or a component. If this method is called 
     * during startup, it may be delayed until the Kernel is completely booted.
     * 
     * @param caller the class that called the shutdown. This is to
     * differentiate between being called from a service and being called from
     * a component.
     */
    void shutdownNode(Object caller);
}
