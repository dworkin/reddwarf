package com.sun.sgs.kernel.scheduling;

import com.sun.sgs.Quality;
import com.sun.sgs.User;
import com.sun.sgs.kernel.AppContext;
import com.sun.sgs.kernel.Kernel;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.Task;
import com.sun.sgs.kernel.TaskImpl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * The class responsible for executing system-level {@link Task}
 * requests.  This class is the entry point for all system-level tasks
 * in the system.  
 *
 * <p>
 *
 * [description of the task execution ordering]
 *
 * <p>
 *
 * [discussion of whether a task should have a priority or be unordered]
 *
 * <p>
 *
 * [brief comparion of how system-level-tasks differ for application
 * level tasks and descrption of how they subsume user-level tasks.]
 *
 * @since  1.0
 * @author James Megquier
 * @author Seth Proctor
 * @authod David Jurgens
 */
public abstract class AbstractTaskScheduler implements TaskScheduler {

    /**
     * A local reference to the system's global resource coordinator
     *
     * @see Kernel#getResourceCoordinator()
     */
    protected final ResourceCoordinator resourceCoordinator;

    /**
     * The internal FIFO queue of unordered <code>Task</code> requests
     * waiting to be processed.  This pool of <code>Task</objets> is
     * serviced by a separate pool of threads than those
     * <code>Task</code> objects that are prioritizeable.
     */
    protected final Queue<Task> unorderedTasks;

    /**
     * The default number of threads to have in the tool of {@link
     * TaskThread}s.
     */ 
    private static final int DEFAULT_POOL_SIZE = 16;


    /**
     * FIXME: figure out if the pool size is just for testing or actually
     * a useful parameter.
     * TEST: for testing purposes we have all threads in the one app context
     * that we're currently creating...once the task queue is correctly
     * using the context of each task, then this parameter can be removed
     *
     * @param appContext
     *
     * @param priorityPolicy
     */
    public AbstractTaskScheduler(AppContext appContext) {

	resourceCoordinator = Kernel.instance().getResourceCoordinator();

	// See if the system has defined the number of threads to have
	// in the thread pool, else, use the default value.
	String initialPoolSizeVal = Kernel.instance().getSystemProperty("thread_pool_size");
	int initialPoolSize = (initialPoolSizeVal == null) ? DEFAULT_POOL_SIZE : Integer.parseInt(initialPoolSizeVal);
	    
        // now ask for some number of threads to consume the queue
        for (int i = 0; i < initialPoolSize; i++) {
            Task queueTask = new TaskImpl(new TaskQueueRunnable(), appContext, null, NumericPriority.HIGH, Kernel.instance().getSystemUser());
            resourceCoordinator.requestThread().executeTask(queueTask);
        }
    }

    /**
     * Queues a task to run when the resources are available.
     *
     * @param task the <code>Task</code> to run
     */
    public abstract void queueTask(Task task);


    /**
     * Returns the next task to be run by a {@link TaskThread}.  
     *
     * <p>
     *
     * Subclasses should implement this as a thread-safe method.
     * Implementions should be specific about the types of values
     * returned, such as <code>null</code> as well as the blocking
     * semantics of this method.
     *
     * @return the next task to be run.  Which task is returned is
     *         dependent on the behvaior of the subclass.
     */
    protected abstract Task dequeueTask();

    /**
     * This <code>Runnable</code> is used to consume tasks off the queue
     * and execute them.
     * <p>
     * NOTE: This is internal right now for testing...although it may
     * make sense to keep it here, given that no one else need know how
     * tasks are processed.
     * FIXME: there is a significant problem with this implementation, which
     * is that it always represents the current task in the thread, and
     * therefore doesn't set the task & context correctly. This needs to
     * be fixed, but should be done by actually implementing a correct
     * (not test) implementation of this facility. For now, the queue is
     * always running in the single test app context.
     */
    protected class TaskQueueRunnable implements Runnable {
        
        /**
	 * Whether this thread is still dequeueing and running tasks
	 * from the task queue.
	 */
        private boolean stillProcessing;

	public TaskQueueRunnable() {
	    stillProcessing = true;
	}

        /**
         * Runs the task.
         */
        public void run() {
            while (stillProcessing) {
		// Use a try/catch block within the loop to avoid
		// having this thread killed if the dequeued Task were
		// to throw an uncaught exception during execution.
		Task task = dequeueTask();
		try {
		    // Some subclasses may provide a polling interface
		    // where dequeueTask() returns null.  By checking we
		    // allow for blocking as well as polling.
		    if (task != null) {
			task.run();
		    }
		} catch (Throwable t) {
		    System.out.printf("Task %s caused the following error during its execution.\n", task);
		    t.printStackTrace();
		    System.out.printf("Resuming program execution...\n");
		}
	    }
        }

        /**
         * Stops this thread from further processing after it has
         * completed its current task.
         */
        public void stopProcessing() {
            stillProcessing = false;
        }

    }

    /**
     * A <code>KeyedQueue</code> provides multiple {@link Queue}
     * support for queues that are unique based on key name.
     *
     * <p>
     *
     * The <code>KeyedQueue</code> is designed to have a similar
     * method interface to {@link Queue} but intentionally does
     * not support operations that do not fit within the context.
     *
     * <p> 
     *
     * This is not a thread-safe implementation.
     *
     * @since 1.0
     * @author David Jurgens
     */
    protected static class KeyedQueue {

	/**
	 * A mapping from a <code>User</code> key to the
	 * <code>Queue</code> that contains <code>Task</code>s
	 * requested on its behalf.
	 */
	private final Map<User,Queue<Task>> queues;

	/**
	 * A listing from least recent to most recent of the keys that
	 * had tasks enqueued on their behalf where the key dequeued
	 * by {@link Queue.remove()} will return the key who
	 * <code>Task<code> at the head of its queue is the oldest in
	 * the data structure.
	 */
	// DEVELOPER NOTE: We do not use a BlockingQueue here because
	// we expect this queue to be non-empty at all times.
	// Therefore to save on synchronization overhead (i.e. reduce
	// latency), we use a lock free queue and spin on the event
	// that it is empty.
	private final Queue<User> dequeueOrdering;

	/**
	 * Constructs a <code>KeyedQueue</code> with an empty set of
	 * keys and tasks.
	 */
	public KeyedQueue() {
	    queues = new HashMap<User,Queue<Task>>();
	    dequeueOrdering = new LinkedList<User>();
	}

	/**
	 * Returns <code>true</code> if there is an established task
	 * queue for <code>user</code>.
	 *
	 * @param user a user who may have tasks enqueued on its behalf.
	 *
	 * @return <code>true</code> if the user has had a task
	 *         enqueued on its behalf.
	 */
	public boolean containsKey(User user) {
	    return queues.containsKey(user);
	}

	/**
	 * Returns <code>false</code> if there is at least one {@link
	 * Task} waiting to be dequeued.
	 *
	 * @return <code>false</code> if this KeyedQueue has any
	 *         <code>Task</code> requests enqueued.
	 */
	public boolean isEmpty() {
	    return dequeueOrdering.isEmpty();
	}

	/**
	 * Enqeues <code>t</code> into the task queue associated with
	 * the user on whose behalf this task is executed.  For a
	 * given user, tasks for that user are guaranteed to be
	 * dequeued in FIFO order.
	 *
	 * @param task the task to be executed after all prior tasks
	 *             for the user have been dequeued.
	 *
	 * @see Task.getUser()
	 */
	// DEVELOPER NOTE: Queues are never removed for a user that
	// disconnects.  For a long running system, this is a definite
	// memory leak.  Therefore, we should provide an interface to
	// remove unused empty queues, either by a periodic Task, or
	// when a user disconnects, inserting a tasks that removes its
	// associated queues.
	public void offer(Task t) {
	    User u = t.getUser();
	    Queue<Task> tasks = queues.get(u);
	    if (tasks == null) {
		tasks = new LinkedList<Task>();
		queues.put(u, tasks);
	    }
	    tasks.offer(t);
	    dequeueOrdering.offer(u);
	}

	/**
	 * Returns the oldest <code>Task</code> not yet executed but
	 * does not remove it from the list of waiting tasks.
	 *
	 * @return the <code>Task</code> that has been in the queue
	 *         the longest.
	 *
	 * @throws NoSuchElementException if this KeyedQueue is empty
	 */
	public Task peek() {
	    // see note for remove() on why this is safe
	    return queues.get(dequeueOrdering.peek()).peek();	
	}

	/**
	 * Returns the oldest <code>Task</code> not yet executed and
	 * removes it from the list of waiting tasks.
	 *
	 * @return the <code>Task</code> that has been in the queue
	 *         the longest.
	 *
	 * @throws NoSuchElementException if this KeyedQueue is empty
	 */
	public Task remove() {
	    // DEVELOPER NOTE: We are guaranteed to have an
	    // initialized, non-empty Queue for the user if the user
	    // is in dequeueOrdering due to the mandatory order of
	    // operations that must take place for a user to be in
	    // dequeueOrdering.
	    return queues.get(dequeueOrdering.remove()).remove();
	}

	/**
	 * Returns the number of tasks waiting to be executed in the
	 * queue.
	 *
	 * @return the number of tasks
	 */
	public int size() {
	    return dequeueOrdering.size();
	}
	
    }

}
