
package com.sun.sgs.kernel;

import com.sun.sgs.ManagedRunnable;
import com.sun.sgs.Quality;

import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * This class is the entry point for all events in the system.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class EventQueue
{

    // the system's resource coordinator
    private ResourceCoordinator resourceCoordinator;

    // the queue that lines up events
    private ConcurrentLinkedQueue<Task> eventQueue;

    /**
     * FIXME: figure out if the pool size is just for testing or actually
     * a useful parameter.
     */
    public EventQueue(ResourceCoordinator resourceCoordinator,
                      int initialPoolSize) {
        this.resourceCoordinator = resourceCoordinator;

        // create a simple queue, just for testing
        eventQueue = new ConcurrentLinkedQueue<Task>();

        // now ask for some number of threads to consume the queue
        for (int i = 0; i < initialPoolSize; i++) {
            Task queueTask = new Task(new EventQueueRunnable(), null);
            resourceCoordinator.requestThread().executeTask(queueTask);
        }
    }

    /**
     * Queue an event to run when the resources are available.
     *
     * @param task the <code>Task</code> to run
     */
    public void queueEvent(Task task) {
        // add the new task to the queue
        eventQueue.add(task);

        // make sure someone is awake to process this task
        synchronized(eventQueue) {
            eventQueue.notify();
        }
    }

    /**
     * This <code>Runnable</code> is used to consume tasks off the queue
     * and execute them.
     * <p>
     * NOTE: This is internal right now for testing...although it may
     * make sense to keep it here, given that no one else need know how
     * events are processed.
     */
    private class EventQueueRunnable implements Runnable {
        
        // flag noting whether this thread is still supposed to process tasks
        private boolean stillProcessing = true;

        /**
         * Runs the task.
         */
        public void run() {
            while (stillProcessing) {
                // try to get a task off the queue
                Task task = eventQueue.poll();
                
                if (task != null) {
                    // we got a task, so run it
                    task.run();
                } else {
                    // there was nothing available, so wait until there is
                    // something to process
                    try {
                        synchronized(eventQueue) {
                            eventQueue.wait();
                        }
                    } catch (InterruptedException ie) {
                        // FIXME: we don't know yet what to do with this
                        System.err.println("EventQueue thread interrupted");
                    }
                }
            }            
        }

        /**
         * Stops this thread from further processing.
         * <p>
         * FIXME: this isn't implemented yet, because it's not clear what
         * the right interruption and hand-off semantics will be for the
         * task threads, and for now we don't need this method.
         */
        public void stopProcessing() {
            
        }

    }

}
