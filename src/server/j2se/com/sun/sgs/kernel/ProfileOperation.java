
package com.sun.sgs.kernel;


/**
 * This interface represents a single operation that can be reported as
 * happening during the life of a task running through the scheduler.
 */
public interface ProfileOperation {

    /**
     * Returns the name of this operation.
     *
     * @return the name
     */
    public String getOperationName();

    /**
     * Returns the identifier for this operation.
     *
     * @return the identifier
     */
    public int getId();

    /**
     * Tells this operation to report that it is happening. This may be
     * called any number of times during a single task.
     *
     * @throws IllegalStateException if this is called outside the scope
     *                               of a task run through the scheduler
     */
    public void report();

}
