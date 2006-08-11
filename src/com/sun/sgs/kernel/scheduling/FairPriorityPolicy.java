package com.sun.sgs.kernel.scheduling;

import com.sun.sgs.kernel.Task;

/**
 * A <code>PriorityPolicy</code> implementation that utilizies the
 * provided {@link com.sun.sgs.kernel.scheduling.Priority} provided by
 * {@link com.sun.sgs.kernel.Task#getPriority()} and does not weight
 * this priority based on the {@link com.sun.sgs.kernel.AppContext} or
 * {@link com.sun.sgs.User} of a given {@link Task}.
 *
 * 
 * @since  1.0
 * @author David Jurgens
 */
public class FairPriorityPolicy implements PriorityPolicy {

    /**
     * The model used to determine what priorities are available and
     * their distribution.
     */
    private QueueingModel model;

    public FairPriorityPolicy(QueueingModel model) {
	this.model = model;
    }

    /**
     * Returns the priority assigned to <code>t</code> at its
     * constructions, or the closest <code>Priority</code> in the
     * provided <code>QueueingModel</code> if {@link
     * com.sun.sgs.kernel.Task#getPriority()} does not return a <code>Priority</code> in
     * the set of available priorities, or returns <code>null</code>
     * if <code>t</code> requests a priority that does not match any
     * available priority.
     *
     * @param t the task to be prioritized by this policy.  Typically
     *          this task will be enqueued based on the returned 
     *          priority for later execution.
     *
     * @return the closest valid priority to {@link com.sun.sgs.kernel.Task#getPriority()} or
     *         <code>null</code> if no such priority exists.
     */
    public Priority getPriority(Task t) {
	Priority p = t.getPriority();
	return (model.contains(p)) ? p : model.getClosestPriority(p);
    }

    /**
     * Sets the <code>QueueingModel</code> used by this policy to
     * evaluate priorities for <code>Task</code> requests.
     *
     * @param model the <code>QueueingModel</code> to use.
     *
     * @see com.sun.sgs.kernel.scheduling.FairPriorityPolicy#getPriority(com.sun.sgs.kernel.Task)
     */
    public void setQueueingModel(QueueingModel model) {
	this.model = model;
    }

}
