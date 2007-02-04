
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.app.profile.ProfilingManager;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import java.lang.reflect.Constructor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is the root scheduler class that is used by the rest of the system
 * to schedule tasks. It handles incoming tasks and profiling data, and
 * passes these on to a <code>SystemScheduler</code>. Essentially, this root
 * scheduler handles basic configuration and marshalling, but leaves all
 * the real work to its children.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class MasterTaskScheduler implements TaskScheduler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(MasterTaskScheduler.
                                           class.getName()));

    // the scheduler used for this system
    private final SystemScheduler systemScheduler;

    // the system's resource coordinator
    private final ResourceCoordinator resourceCoordinator;

    // the task handler used to run tasks
    private final TaskHandler taskHandler;

    /**
     * The property used to define the system scheduler.
     */
    public static final String SYSTEM_SCHEDULER_PROPERTY =
        "com.sun.sgs.impl.kernel.schedule.SystemScheduler";

    // the default system scheduler
    private static final String DEFAULT_SYSTEM_SCHEDULER =
        "com.sun.sgs.impl.kernel.schedule.SingleAppSystemScheduler";

    /**
     * The property used to define the default number of initial consumer
     * threads.
     */
    public static final String INITIAL_CONSUMER_THREADS_PROPERTY =
        "com.sun.sgs.impl.kernel.schedule.InitialConsumerThreads";

    // the default number of initial consumer threads
    private static final String DEFAULT_INITIAL_CONSUMER_THREADS = "4";

    // the actual number of threads we're currently using
    private AtomicInteger threadCount;

    // the default priority for tasks
    private static Priority defaultPriority = Priority.getDefaultPriority();

    /**
     * Creates an instance of <code>MasterTaskScheduler</code>.
     *
     * @param properties the system properties
     * @param resourceCoordinator the system's <code>ResourceCoordinator</code>
     * @param taskHandler the system's <code>TaskHandler</code>
     *
     * @throws Exception if there are any failures during configuration
     */
    public MasterTaskScheduler(Properties properties,
                               ResourceCoordinator resourceCoordinator,
                               TaskHandler taskHandler)
        throws Exception
    {
        logger.log(Level.CONFIG, "Creating the Master Task Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");
        if (resourceCoordinator == null)
            throw new NullPointerException("Resource Coord cannot be null");
        if (taskHandler == null)
            throw new NullPointerException("Task Handler cannot be null");

        String systemSchedulerName =
            properties.getProperty(SYSTEM_SCHEDULER_PROPERTY,
                                   DEFAULT_SYSTEM_SCHEDULER);
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Using {0} as the System Scheduler",
                       systemSchedulerName);

        // get the system scheduler instance
        Class<?> systemSchedulerClass = Class.forName(systemSchedulerName);
        Constructor<?> systemSchedulerConstructor =
            systemSchedulerClass.getConstructor(Properties.class);
        systemScheduler =
            (SystemScheduler)(systemSchedulerConstructor.
                    newInstance(properties)); 

        this.resourceCoordinator = resourceCoordinator;
        this.taskHandler = taskHandler;

        int startingThreads =
            Integer.parseInt(properties.
                    getProperty(INITIAL_CONSUMER_THREADS_PROPERTY,
                                DEFAULT_INITIAL_CONSUMER_THREADS));
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Using {0} initial consumer threads",
                       startingThreads);
        threadCount = new AtomicInteger(startingThreads);

        // create the initial consuming threads
        for (int i = 0; i < startingThreads; i++) {
            resourceCoordinator.
                startTask(new MasterTaskConsumer(this,
                                                 systemScheduler, taskHandler),
                          null);
        }
    }

    /**
     * Registers an application that will be scheduling tasks.
     *
     * @param context the application's <code>KernelAppContext</code>
     * @param properties the application's <code>Properties</code>
     */
    public void registerApplication(KernelAppContext context,
                                    Properties properties) {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Registering application {0}", context);

        if (context == null)
            throw new NullPointerException("Context cannot be null");
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        systemScheduler.registerApplication(context, properties);
    }

    /**
     * Registers the given <code>ProfilingManager</code>, resulting in the
     * manager being given a <code>ProfileReporter</code>.
     *
     * @param manager the <code>ProfilingManager</code> being registered
     */
    public void registerProfilingManager(ProfilingManager manager) {
        // FIXME: lookup or create the reporter and provide to the manager
        // once we start the profiling work and define these interfaces
        logger.log(Level.CONFIG, "Registering profiling manager");
    }

    /**
     * Notifies the scheduler that a thread has been interrupted and is
     * finishing its work.
     */
    void notifyThreadLeaving() {
        // FIXME: we're not yet trying to adapt the number of threads being
        // used, so we assume that threads are only lost when the system
        // wants to shutdown...in practice, this should look at some
        // threshold and see if another consumer needs to be created
        if (threadCount.getAndDecrement() == 0)
            logger.log(Level.CONFIG, "No more threads are consuming tasks");
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner) {
        ScheduledTask t = new ScheduledTask(task, owner, defaultPriority,
                                            System.currentTimeMillis());
        return systemScheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
                                       Priority priority) {
        ScheduledTask t = new ScheduledTask(task, owner, priority,
                                            System.currentTimeMillis());
        return systemScheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
                                       long startTime) {
        ScheduledTask t = new ScheduledTask(task, owner, defaultPriority,
                                            startTime);
        return systemScheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTasks(Collection<? extends KernelRunnable>
                                        tasks, TaskOwner owner) {
        if (tasks == null)
            throw new NullPointerException("Collection cannot be null");

        HashSet<TaskReservation> reservations = new HashSet<TaskReservation>();
        try {
            // make sure that all the reservations can be made...
            for (KernelRunnable task : tasks) {
                ScheduledTask t =
                    new ScheduledTask(task, owner, defaultPriority,
                                      System.currentTimeMillis());
                reservations.add(systemScheduler.reserveTask(t));
            } 
        } catch (TaskRejectedException tre) {
            // ...and if any fails, cancel all the reservations that were
            // made successfully
            for (TaskReservation reservation : reservations)
                reservation.cancel();
            throw tre;
        }

        return new GroupTaskReservation(reservations);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner) {
        systemScheduler.
            addTask(new ScheduledTask(task, owner, defaultPriority,
                                      System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner,
                             Priority priority) {
        systemScheduler.
            addTask(new ScheduledTask(task, owner, priority,
                                      System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner,
                             long startTime) {
        systemScheduler.
            addTask(new ScheduledTask(task, owner, defaultPriority,
                                      startTime));
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     TaskOwner owner,
                                                     long startTime,
                                                     long period) {
        return systemScheduler.
            addRecurringTask(new ScheduledTask(task, owner, defaultPriority,
                                               startTime, period));
    }

    /**
     * Private inner class that represents a group of reservations. When a
     * reservation is made for a group, they are all accpeted or none of them
     * is accepted. When used or cancelled, it also applies to all tasks.
     */
    private class GroupTaskReservation implements TaskReservation {
        HashSet<TaskReservation> reservations;
        private boolean finished = false;
        public GroupTaskReservation(HashSet<TaskReservation> reservations) {
            this.reservations = reservations;
        }
        public void cancel() {
            if (finished)
                throw new IllegalStateException("cannot cancel reservation");
            for (TaskReservation reservation : reservations)
                reservation.cancel();
            finished = true;
        }
        public void use() {
            if (finished)
                throw new IllegalStateException("cannot use reservation");
            for (TaskReservation reservation : reservations)
                reservation.use();
            finished = true;
        }
    }

}
