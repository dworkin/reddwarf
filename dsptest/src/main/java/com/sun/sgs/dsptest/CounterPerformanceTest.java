/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
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
import com.sun.sgs.impl.counters.NormalInteger;
import com.sun.sgs.impl.counters.ScalableCounter;
import com.sun.sgs.impl.counters.ScalableCounter2;
import com.sun.sgs.impl.counters.ScalableInteger;
import com.sun.sgs.impl.counters.ScalableStatCounter;

/**
 * Counter benchmarking by counting up to a specific number with a large number
 * of concurrent tasks.
 */
public class CounterPerformanceTest implements AppListener, Serializable
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** Number of concurrent tasks to start with. **/
    public static final int INITIAL_CONCURRENT_TASKS = 10;

    /** Number to count up to in each concurrent task. **/
    public static final int INITIAL_COUNT_NUM = 500;

    /** Name binding of the concurrent tasks counter. **/
    private static final String TASKS_COUNTER = "TasksCounter";

    /** Counters to test. **/
    public static enum COUNTER_TYPE {
        Warmup, ScalableCounterWithoutGet, ScalableCounterWithGet, None, NormalInteger, ScalableInteger, ScalableStatCounter, ScalableCounter2;

        public COUNTER_TYPE next()
        {
            return COUNTER_TYPE.values()[this.ordinal() + 1];
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
        dataManager.setBinding(TASKS_COUNTER, new NormalInteger());

        // Initialize counters (data structures) to test
        dataManager.setBinding(COUNTER_TYPE.ScalableCounterWithGet.toString(),
                new ScalableCounter());
        dataManager.setBinding(COUNTER_TYPE.ScalableCounterWithoutGet
                .toString(), new ScalableCounter());
        dataManager.setBinding(COUNTER_TYPE.ScalableInteger.toString(),
                new ScalableInteger());
        dataManager.setBinding(COUNTER_TYPE.NormalInteger.toString(),
                new NormalInteger());
        dataManager.setBinding(COUNTER_TYPE.ScalableStatCounter.toString(),
                new ScalableStatCounter());
        dataManager.setBinding(COUNTER_TYPE.ScalableCounter2.toString(),
                new ScalableCounter2());

        // Write output file header
        writeToFile("ConcurrentTasks ");
        for (int i = 0; i < COUNTER_TYPE.values().length; i++) {
            writeToFile(COUNTER_TYPE.values()[i].toString() + " ");
        }
        writeToFile("\n");

        // Schedule task that will start performance testing
        taskManager.scheduleTask(new MonitoringTask(), 0);
        logger.info("\n[*] Timing counters...\n" + "[*] # Concurrent Tasks: "
                + INITIAL_CONCURRENT_TASKS);
    }

    /**
     * Start all the concurrent tasks.
     * 
     * @param task
     * @param numConcurrentTasks
     */
    private static void scheduleTasks(Task task, int numConcurrentTasks)
    {
        for (int i = 0; i < numConcurrentTasks; i++) {
            AppContext.getTaskManager().scheduleTask(task,
                    new Random().nextInt(30));
        }
    }

    /**
     * Get the integer value of the current counter being tested.
     * 
     * @param type
     * @return
     */
    private static int getNum(COUNTER_TYPE type)
    {
        switch (type) {
        case None:
        case Warmup:
            return 0;
        case NormalInteger:
            return ((NormalInteger) getObj(type.toString())).get();
        case ScalableCounter2:
            return ((ScalableCounter2) getObj(type.toString())).get();
        case ScalableCounterWithGet:
        case ScalableCounterWithoutGet:
            return ((ScalableCounter) getObj(type.toString())).get();
        case ScalableInteger:
            return ((ScalableInteger) getObj(type.toString())).get();
        case ScalableStatCounter:
            return ((ScalableStatCounter) getObj(type.toString())).get();
        default:
            return 0;
        }
    }

    /**
     * Task that will start and time the concurrent tasks with each version of a
     * scalable integer.
     */
    private static final class MonitoringTask implements Task, Serializable
    {
        private static final long serialVersionUID = 1L;
        private long startTime = System.currentTimeMillis();
        private int numConcurrentTasks = INITIAL_CONCURRENT_TASKS;
        private COUNTER_TYPE type = COUNTER_TYPE.values()[0];

        public MonitoringTask()
        {
            // Fire off a warmup test; might be other tasks running on startup.
            scheduleTasks(
                    new ScalableIncrementTask(0, INITIAL_COUNT_NUM, type),
                    numConcurrentTasks);
            writeToFile("" + numConcurrentTasks);
        }

        public void run() throws Exception
        {
            NormalInteger si = getObj(TASKS_COUNTER);
            if (si.get() >= numConcurrentTasks) {
                long time = System.currentTimeMillis() - startTime;
                logger.info("CounterType: " + type.toString() + " / Time: "
                        + time + " / Final #: " + getNum(type));
                writeToFile(" " + time);

                if (si.get() != numConcurrentTasks) {
                    logger
                            .warning("Number of completed concurrent tasks is incorrect");
                }

                // reset
                si.set(0);

                if (type == COUNTER_TYPE.values()[COUNTER_TYPE.values().length - 1]) {
                    numConcurrentTasks += 10;
                    type = COUNTER_TYPE.values()[0];
                    logger.info("Now Running with concurrency: "
                            + numConcurrentTasks);
                    if (numConcurrentTasks == 110) {
                        System.exit(1);
                        return;
                    }

                    writeToFile("\n" + numConcurrentTasks);
                } else
                    type = type.next();

                startTime = System.currentTimeMillis();
                scheduleTasks(new ScalableIncrementTask(0, INITIAL_COUNT_NUM,
                        type), numConcurrentTasks);
            }

            AppContext.getTaskManager().scheduleTask(this, 500);
        }
    }

    /**
     * Write to an output file.
     * 
     * To plot this: plot "file.txt" using 1:x with lines smooth bezier.
     * 
     * @param str
     */
    private static void writeToFile(String str)
    {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(
                    "output.txt", true));
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
        private final int count, count_max;
        private final COUNTER_TYPE type;

        public ScalableIncrementTask(int count, int count_max,
                COUNTER_TYPE type)
        {
            this.count = count;
            this.count_max = count_max;
            this.type = type;
        }

        @Override
        public void run() throws Exception
        {
            if (count >= count_max) {
                ((NormalInteger) getObj(TASKS_COUNTER)).inc();
                return;
            }

            if (type == COUNTER_TYPE.None || type == COUNTER_TYPE.Warmup) {

            } else if (type == COUNTER_TYPE.ScalableCounterWithGet) {
                ScalableCounter c = getObj(type.toString());
                c.get();
                c.add(1);
            } else if (type == COUNTER_TYPE.ScalableCounterWithoutGet) {
                ScalableCounter c = getObj(type.toString());
                c.add(1);
            } else if (type == COUNTER_TYPE.ScalableInteger) {
                ((ScalableInteger) getObj(type.toString())).incrementAndGet();
            } else if (type == COUNTER_TYPE.NormalInteger) {
                ((NormalInteger) getObj(type.toString())).inc();
            } else if (type == COUNTER_TYPE.ScalableStatCounter) {
                ScalableStatCounter c = getObj(type.toString());
                c.inc();
            } else if (type == COUNTER_TYPE.ScalableCounter2) {
                ScalableCounter2 c = getObj(type.toString());
                c.inc();
            } else {
                throw new IllegalStateException("Unimplemented case: "
                        + type.toString());
            }

            AppContext.getTaskManager().scheduleTask(
                    new ScalableIncrementTask(count + 1, count_max, type));
        }
    }
    
    /**
     * Get the object from data manager.
     * 
     * @param <T>
     * @param name name binding
     * @return
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