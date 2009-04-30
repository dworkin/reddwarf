/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed here under to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.dsptest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.counters.Counter;
import com.sun.sgs.impl.counters.AtomicIntegerCounter;
import com.sun.sgs.impl.counters.EmptyCounter;
import com.sun.sgs.impl.counters.PoolCounter;
import com.sun.sgs.impl.counters.PrimitiveCounter;
import com.sun.sgs.impl.counters.TaskCounter;

/**
 * Counter benchmarking by counting up to a specific number with a large number
 * of concurrent tasks.
 */
public class CounterPerformanceTest implements AppListener, Serializable
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** Random **/
    private static final Random random = new Random();

    /** Number of concurrent tasks to start with. **/
    public static final int INITIAL_CONCURRENT_TASKS = 10;

    /** Number of concurrent tasks to end with. */
    public static final int MAX_CONCURRENT_TASKS = 150;

    /** Number to count up to in each concurrent task. **/
    public static final int INITIAL_COUNT_NUM = 500;

    /** Name binding of the completed concurrent tasks counter. **/
    private static final String TASKS_COUNTER = "TasksCounter";

    /** Suffix to mark the test for a counter to have both reads and writes. */
    private static final String RW_SUFFIX = "RW";

    /** Output file name in the deployment folder. */
    private static final String OUTPUT_FILENAME = "output.txt";

    /**
     * Counters to test.
     * 
     * Counters whose names end with "RW" will be written to and read
     * alternatively to test concurrency with writes and reads in the same time.
     **/
    public static enum COUNTER_TYPE {
        Warmup, PoolCounter, PoolCounterRW, PoolCounter50, PoolCounter50RW, TaskCounter, TaskCounterRW, ManagedInteger, ManagedIntegerRW, EmptyCounter, None;

        public COUNTER_TYPE next()
        {
            return COUNTER_TYPE.values()[this.ordinal() + 1];
        }

        public boolean isLastCounter()
        {
            return this == COUNTER_TYPE.values()[COUNTER_TYPE.values().length - 1];
        }
    };

    /** The {@link Logger} for this class. */
    private static final Logger logger = Logger
            .getLogger(CounterPerformanceTest.class.getName());

    /**
     * {@inheritDoc}
     * 
     * Initialize counters and fire off the monitoring task.
     */
    public void initialize(Properties props)
    {
        DataManager dataManager = AppContext.getDataManager();
        TaskManager taskManager = AppContext.getTaskManager();

        // Initialize concurrent tasks counter
        dataManager.setBinding(TASKS_COUNTER, new PrimitiveCounter());

        // Initialize counters (data structures) to test
        dataManager.setBinding(COUNTER_TYPE.PoolCounter.toString(),
                new PoolCounter());
        dataManager.setBinding(COUNTER_TYPE.PoolCounterRW.toString(),
                new PoolCounter());
        dataManager.setBinding(COUNTER_TYPE.PoolCounter50.toString(),
                new PoolCounter(0, 50));
        dataManager.setBinding(COUNTER_TYPE.PoolCounter50RW.toString(),
                new PoolCounter(0, 50));
        dataManager.setBinding(COUNTER_TYPE.ManagedInteger.toString(),
                new AtomicIntegerCounter());
        dataManager.setBinding(COUNTER_TYPE.ManagedIntegerRW.toString(),
                new AtomicIntegerCounter());
        dataManager.setBinding(COUNTER_TYPE.TaskCounter.toString(),
                new TaskCounter());
        dataManager.setBinding(COUNTER_TYPE.TaskCounterRW.toString(),
                new TaskCounter());
        dataManager.setBinding(COUNTER_TYPE.EmptyCounter.toString(),
                new EmptyCounter());

        // Write output file header
        writeToFile("NumConcurrentTasks ");
        for (int i = 0; i < COUNTER_TYPE.values().length; i++) {
            writeToFile(COUNTER_TYPE.values()[i].toString() + " ");
        }
        writeToFile("\n");

        // Schedule the task that will start performance testing
        taskManager.scheduleTask(new MonitoringTask(), 0);
        logger.info("\n[*] Timing counters...\n" + "[*] # Concurrent Tasks: "
                + INITIAL_CONCURRENT_TASKS);
    }

    /**
     * Start all the concurrent tasks at slightly different times.
     * 
     * @param task
     * @param numConcurrentTasks
     */
    private static void scheduleTasks(COUNTER_TYPE type, int numConcurrentTasks)
    {
        for (int i = 0; i < numConcurrentTasks; i++) {
            AppContext.getTaskManager().scheduleTask(
                    new ScalableIncrementTask(type), random.nextInt(30));
        }
    }

    /**
     * Get the integer value of the current counter being tested.
     * 
     * @param type
     * @return the value of the counter
     */
    private static int getNum(COUNTER_TYPE type)
    {
        if (type == COUNTER_TYPE.Warmup || type == COUNTER_TYPE.None) {
            // exclude when we don't actually have a counter to read
            return 0;
        } else if (EnumSet.allOf(COUNTER_TYPE.class).contains(type)) {
            // read the value using the interface if we do have a counter
            return ((Counter) getObj(type.toString())).get();
        } else {
            // should not get here
            return -1;
        }
    }

    /**
     * Task that will start and time the concurrent tasks with each version of a
     * concurrent counter.
     */
    private static final class MonitoringTask implements Task, Serializable
    {
        /**
         * The serializable version of the class.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The time when the monitoring for a counter is started.
         */
        private long startTime = System.currentTimeMillis();

        /**
         * The number of concurrent tasks running.
         */
        private int numConcurrentTasks = INITIAL_CONCURRENT_TASKS;

        /**
         * The current type of counter that is being tested.
         */
        private COUNTER_TYPE type = COUNTER_TYPE.values()[0];

        /**
         * 
         */
        public MonitoringTask()
        {
            // Fire off a warmup test; might be other tasks running on startup.
            scheduleTasks(type, numConcurrentTasks);
            writeToFile(Integer.toString(numConcurrentTasks));
        }

        /**
         * 
         */
        @Override
        public void run() throws Exception
        {
            PrimitiveCounter concurrentTasksCounter = getObj(TASKS_COUNTER);
            if (concurrentTasksCounter.get() >= numConcurrentTasks) {

                long time = System.currentTimeMillis() - startTime;
                logger.info("CounterType: " + type.toString() + " / Time: "
                        + time + " / Final #: " + getNum(type));
                writeToFile(" " + time);

                if (concurrentTasksCounter.get() != numConcurrentTasks) {
                    logger.warning("Number of completed concurrent "
                            + "tasks is incorrect");
                    return;
                }

                if (type.isLastCounter()) {
                    if (numConcurrentTasks == MAX_CONCURRENT_TASKS) {
                        // test complete
                        System.exit(1);
                        return;
                    }

                    type = COUNTER_TYPE.values()[0];
                    numConcurrentTasks += 10;
                    logger.info("Now Running with concurrency: "
                            + numConcurrentTasks);
                    writeToFile("\n" + numConcurrentTasks);
                } else
                    type = type.next();

                // reset the completed concurrent tasks counter
                concurrentTasksCounter.set(0);
                startTime = System.currentTimeMillis();
                scheduleTasks(type, numConcurrentTasks);
            }

            AppContext.getTaskManager().scheduleTask(this, 1000);
        }
    }

    /**
     * Write data to an output file for plotting.
     * Example method to this with gnuplot:
     * 
     * set macros
     * set key autotitle columnhead
     * 
     * file="'output.txt'"
     * opt='with lines smooth bezier'
     * 
     * cd '~/dsptest/target/sgs-server-dist-0.9.9/'
     * plot @file using 1:3 @opt, @file using 1:4 @opt, @file using 1:5 @opt, 
     * @file using 1:6 @opt, @file using 1:7 @opt, @file using 1:8 @opt, 
     * @file using 1:9 @opt, @file using 1:10 @opt, @file using 1:11 @opt
     * 
     * @param str string to write
     */
    private static void writeToFile(String str)
    {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(
                    OUTPUT_FILENAME, true));
            out.write(str);
            out.close();
        } catch (Exception e) {
            logger.warning("Error writing to file");
        }
    }

    /**
     * This task is spawned multiple times concurrently. It will try to modify
     * one of the counters created in the constructor. This simulates a highly
     * contended counter.
     */
    private static final class ScalableIncrementTask
            implements Task, Serializable
    {
        /** The version of the serialized form. */
        private static final long serialVersionUID = 1;
        private int count = 0;
        private final COUNTER_TYPE type;

        public ScalableIncrementTask(COUNTER_TYPE type)
        {
            this.type = type;
        }

        @Override
        public void run() throws Exception
        {
            if (count >= INITIAL_COUNT_NUM) {
                ((PrimitiveCounter) getObj(TASKS_COUNTER)).inc();
                return;
            }

            if (type == COUNTER_TYPE.None || type == COUNTER_TYPE.Warmup) {
                // do nothing
            } else {
                Counter counter = getObj(type.toString());
                counter.inc();

                if (type.toString().endsWith(RW_SUFFIX) && count % 2 == 0) {
                    // do a read every other count if we are testing read/write
                    counter.get();
                }
            }

            count++;
            AppContext.getTaskManager().scheduleTask(this);
        }
    }

    /**
     * Get the object from data manager.
     * 
     * @param <T>
     * @param name name binding
     * @return the object from the data store
     */
    @SuppressWarnings("unchecked")
    private static final <T> T getObj(String name)
    {
        return (T) AppContext.getDataManager().getBinding(name);
    }

    public ClientSessionListener loggedIn(ClientSession session)
    {
        return null;
    }
}