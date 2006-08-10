package com.sun.sgs.kernel.scheduling;

import com.sun.sgs.Quality;
import com.sun.sgs.User;
import com.sun.sgs.kernel.AppContext;
import com.sun.sgs.kernel.Task;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * A weighted, fair scheduler with three priorities:
 * <code>HIGH</code>, <code>NORMAL</code>, and <code>LOW</code>.
 *
 * [notes on dequeueing strategy]
 *
 *
 * @since  1.0
 * @author David Jurgens
 */
public class FairPriorityTaskScheduler extends AbstractTaskScheduler {

    /**
     * The <code>PriorityPolicy</code> that will determine into which
     * queue a {@link Task} will be enqueued.
     */
    protected final PriorityPolicy priorityPolicy;

    /**
     * A mapping from the available priorities to the queue containing
     * tasks of that priority.
     */
    private final Map<Priority,KeyedQueue> priorityQueues;

    /**
     * A private counter for determining which priority of tasks to
     * dequeue next
     *
     * @see FairPriorityTaskScheduler#dequeueTask()
     */
    private int taskCounter;

    public FairPriorityTaskScheduler(AppContext appContext) {
	super(appContext);

	// DEVELOPER NOTE: these should probably be configurable
	// somewhere but we also need to know them at design time to
	// ensure a fast fair-dequeueing strategy.  Making these
	// optional to a subclass doesn't make as much sense because
	// any subclass would be overriding the method that provides
	// the priorities as well as the the dequeueTask() method (for
	// efficiency), which is essentially a new subclass of
	// AbstractTaskScheduler.
	priorityPolicy = new FairPriorityPolicy(new QueueingModel(NumericPriority.HIGH,
								  NumericPriority.NORMAL,
								  NumericPriority.LOW));

	priorityQueues = new HashMap<Priority, KeyedQueue>();
	priorityQueues.put(NumericPriority.HIGH,   new KeyedQueue());
	priorityQueues.put(NumericPriority.NORMAL, new KeyedQueue());
	priorityQueues.put(NumericPriority.LOW,    new KeyedQueue());
	
	taskCounter = 0;
    }

    /**
     * Queues a task based on the priority specified by the {@link
     * PriorityPolicy} of this scheduler and runs it as resources
     * become available while maintaining fairness among
     * <code>Task</code> requests with different priorities.
     *
     * @param task the <code>Task</code> to run
     */
    public void queueTask(Task task) {
	Priority p = priorityPolicy.getPriority(task);
	priorityQueues.get(p).offer(task);
    }

    /**
     * Returns the next <code>Task</code> to execute based on a fair,
     * weighted dequeing strategy, or <code>null</code> if no
     * <code>Task</code> requests are currently enqueued.
     *
     * <p>
     *
     * Dequeueing is based on a 4:2:1 dequeueing ratio, where for
     * every four high priority tasks, two normal priority tasks, and
     * one low priority task are executed.  This ratio is not
     * guaranteed in the current implementation if tasks are not
     * enqueued in such a manner that it is possible to dequeue using
     * this ratio.  A best effort attempt to keep this ration is made,
     * however.
     *
     * <p>
     *
     * Due to the expected high rate of enqueueing, this method
     * returns null so that a <code>TaskThread</code> will spin until
     * an available Task can be dequeued rather than block until the
     * queue is non-empty.
     *
     * @return the next tasks ready to run.  Tasks are dequeued based
     *         on a priority ordering.
     */
    // DEVEOPER NOTE: although this is a synchronized method, it
    // should not block.  The lock is to prevent concurrent access to
    // the priority queues.
    //
    // DEVELOPER NOTE: this method needs to be optimized.  heavily.
    protected synchronized Task dequeueTask() {
	// We use a power-of-two distribution of execution.  For high,
	// normal, and low priority tasks, we execute 4:2:1 tasks,
	// respectively.  If no task is available at that priority, we
	// use the next lowest priority, or the only available
	// priority if nothing else is enqueued.  If no tasks are
	// enqueued, we return null, so that the task threads spin,
	// since we expect objects to be enqueued regularly

	Task t = null;
	if (taskCounter < 4) {
	    if (priorityQueues.get(NumericPriority.HIGH).size() > 0) {
		t = priorityQueues.get(NumericPriority.HIGH).remove();
	    }
	    else if (priorityQueues.get(NumericPriority.NORMAL).size() > 0) {
		t = priorityQueues.get(NumericPriority.NORMAL).remove();
	    }
	    else if (priorityQueues.get(NumericPriority.LOW).size() > 0) {
		t = priorityQueues.get(NumericPriority.LOW).remove();
	    }
	}
	else if (taskCounter < 6) {
	    if (priorityQueues.get(NumericPriority.NORMAL).size() > 0) {
		t = priorityQueues.get(NumericPriority.NORMAL).remove();
	    }
	    else if (priorityQueues.get(NumericPriority.HIGH).size() > 0) {
		t = priorityQueues.get(NumericPriority.HIGH).remove();
	    }
	    else if (priorityQueues.get(NumericPriority.LOW).size() > 0) {
		t = priorityQueues.get(NumericPriority.LOW).remove();
	    }
	}
	else {
	    if (priorityQueues.get(NumericPriority.LOW).size() > 0) {
		t = priorityQueues.get(NumericPriority.LOW).remove();
	    }
	    else if (priorityQueues.get(NumericPriority.HIGH).size() > 0) {
		t = priorityQueues.get(NumericPriority.HIGH).remove();
	    }
	    else if (priorityQueues.get(NumericPriority.NORMAL).size() > 0) {
		t = priorityQueues.get(NumericPriority.NORMAL).remove();
	    }
	}
	taskCounter = (taskCounter + 1) % 7;
	return t;
    }

}
