package com.sun.sgs.kernel.scheduling;

import com.sun.sgs.kernel.Task;

/**
 * The <code>PriorityPolicy</code> provides a {@link TaskScheduler} an
 * implementation-independent way of assigning a priority value to a
 * {@link Task} request based on the meta-data it provides.
 *
 * <p>
 *
 * A <code>PriorityPolicy</code> should derive its priority evaluation
 * from the provided {@link QueueingModel}.  This
 * <code>QueueingModeling</code> provides information about the number
 * of queues a <code>TaskScheduler</code> has, as well as the relative
 * weights between each queue.  For example, a weighted, fair
 * deqeueing algorithm will behave differently if the queues are
 * weighted <code>HIGH</code>, <code>NORMAL</code>, and
 * <code>LOW</code> than if the priorities were
 * <code>REAL_TIME</code>, <code>HIGH</code> and <code>NORMAL</code>,
 * even though both algorithms uses the same number of queues.
 *
 * <p>
 *
 * Since this is an interface specification, the model cannot enforce
 * a constructor convention.  However, all current implemtnations
 * provide a constructor that receives a {@link QueueingModel}.
 * Implementations that do not provided a constructor that takes in a
 * <code>QueueingModel</code> should explicitly state which model or
 * algorithm is used by default.
 *
 *
 * @see QueueingModel
 * @since  1.0
 * @author David Jurgens
 */
public interface PriorityPolicy {
 
    /**
     * Returns the execution priority of this task based on the
     * meta-data of <code>t</code>, the {@link QueueingModel} provided,
     * and the priority-assignment algorithm contained in this
     * implementation.
     *
     * @param t a Task not yet enqueued by the {@link TaskScheduler}
     *          whose meta-data has been fully assigned
     *
     * @return  the priority of <code>t</code>
     */ 
    public Priority getPriority(Task t);


    /**
     * Sets the <code>QueueingModel</code> that describes the number
     * of and weights of queues in the system, by which this
     * <code>PriorityPolicy</code> will determine priorities for
     * {@link Task} objects.
     *
     * @param model the <code>QueueingModel</code> that reflects the
     *              queueing features of the component that is using this
     *              <code>PriorityPolicy</code>
     */
    public void setQueueingModel(QueueingModel model);


}
