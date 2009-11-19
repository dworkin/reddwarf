/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.tests.workload;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 *
 */
public class WorkloadGeneratorService implements Service {

    public static final String PACKAGE_NAME = "com.sun.sgs.tests.workload";

    public static final String GENERATORS_PROPERTY = PACKAGE_NAME + ".generators";
    public static final Integer DEFAULT_GENERATORS = 300;

    public static final String BINDINGS_PROPERTY = PACKAGE_NAME + ".bindings";
    public static final Integer DEFAULT_BINDINGS = 1000;

    public static final String ACCESSES_PROPERTY = PACKAGE_NAME + ".accesses";
    public static final Integer DEFAULT_ACCESSES = 10;

    public static final String WRITES_PROPERTY = PACKAGE_NAME + ".writes";
    public static final Integer DEFAULT_WRITES = 2;

    public static final String TASKS_PER_SECOND_PROPERTY = PACKAGE_NAME + ".tasks.per.second";
    public static final Integer DEFAULT_TASKS_PER_SECOND = 10;
    
    private final ComponentRegistry systemRegistry;
    private final TransactionProxy txnProxy;
    private final TaskScheduler taskScheduler;
    private final TransactionScheduler txnScheduler;
    private final Identity owner;

    private final DataService dataService;
    private final TaskService taskService;

    private final int generators;
    private final int bindings;
    private final int accesses;
    private final int writes;
    private final int reads;
    private final int tasksPerSecond;

    private final int taskThreads;
    private final int txnThreads;

    private final int size = 500;
    private int counter = 0;

    public WorkloadGeneratorService(Properties props,
                                    ComponentRegistry systemRegistry,
                                    TransactionProxy txnProxy) throws Exception {
        this.systemRegistry = systemRegistry;
        this.txnProxy = txnProxy;
        this.taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        this.txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        this.owner = txnProxy.getCurrentOwner();

        this.dataService = txnProxy.getService(DataService.class);
        this.taskService = txnProxy.getService(TaskService.class);

        PropertiesWrapper wProps = new PropertiesWrapper(props);
        this.generators = wProps.getIntProperty(GENERATORS_PROPERTY,
                                                DEFAULT_GENERATORS,
                                                0, Integer.MAX_VALUE);
        this.bindings = wProps.getIntProperty(BINDINGS_PROPERTY,
                                              DEFAULT_BINDINGS,
                                              0, Integer.MAX_VALUE);
        this.writes = wProps.getIntProperty(WRITES_PROPERTY,
                                            DEFAULT_WRITES,
                                            0, Integer.MAX_VALUE);
        this.accesses = wProps.getIntProperty(ACCESSES_PROPERTY,
                                              DEFAULT_ACCESSES,
                                              0, Integer.MAX_VALUE);
        this.reads = this.accesses - this.writes;
        this.tasksPerSecond = wProps.getIntProperty(TASKS_PER_SECOND_PROPERTY,
                                                    DEFAULT_TASKS_PER_SECOND,
                                                    0,
                                                    Integer.MAX_VALUE);

        Class<?> taskSchedulerClass = taskScheduler.getClass();
        Field taskThreadsPropertyField = taskSchedulerClass.getDeclaredField(
                "CONSUMER_THREADS_PROPERTY");
        Field defaultTaskThreadsField = taskSchedulerClass.getDeclaredField(
                "DEFAULT_CONSUMER_THREADS");
        taskThreadsPropertyField.setAccessible(true);
        defaultTaskThreadsField.setAccessible(true);
        String taskThreadsProperty = (String) taskThreadsPropertyField.get(taskScheduler);
        String defaultTaskThreads = (String) defaultTaskThreadsField.get(taskScheduler);
        this.taskThreads = Integer.parseInt(wProps.getProperty(taskThreadsProperty, defaultTaskThreads));

        Class<?> txnSchedulerClass = txnScheduler.getClass();
        Field txnThreadsPropertyField = txnSchedulerClass.getDeclaredField(
                "CONSUMER_THREADS_PROPERTY");
        Field defaultTxnThreadsField = txnSchedulerClass.getDeclaredField(
                "DEFAULT_CONSUMER_THREADS");
        txnThreadsPropertyField.setAccessible(true);
        defaultTxnThreadsField.setAccessible(true);
        String txnThreadsProperty = (String) txnThreadsPropertyField.get(txnScheduler);
        String defaultTxnThreads = (String) defaultTxnThreadsField.get(txnScheduler);
        this.txnThreads = Integer.parseInt(wProps.getProperty(txnThreadsProperty, defaultTxnThreads));
    }

    public String getName() {
        return this.getClass().getName();
    }

    public void ready() throws Exception {
        setBindings(100, "dummy", size);
        setBindings(bindings, "name", size);

        // kick off a number of workload generators that should
        // generate tasks with a poisson distribution of interarrival times
        taskScheduler.scheduleTask(new KernelRunnable() {
            public String getBaseTaskType() {
                return "LaunchGenerators";
            }
            public void run() throws Exception {
                for (int i = 0; i < generators; i++) {
                    taskScheduler.scheduleTask(new TaskGenerator(), owner, System.currentTimeMillis());
                    Thread.sleep(nextExponential(50));
                }
            }
        }, owner);
    }

    public void shutdown() {

    }

    private long nextExponential(long mean) {
        return (long) (((double) mean) * -1.0 * Math.log(Math.random()));
    }

    private void setBindings(final long numBindings, final String prefix, final int objectSize) {
        counter = 0;
        for (; counter < numBindings; counter++) {
            try {
                txnScheduler.runTask(new KernelRunnable() {
                    public String getBaseTaskType() {
                        return "setBinding";
                    }
                    public void run() {
                        dataService.setBinding(prefix + counter, new ManagedInteger(objectSize));
                    }
                }, owner);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private static class ManagedInteger implements ManagedObject, Serializable {
        private static int nextValue = 0;
        public int i;
        public byte[] buffer;
        public ManagedInteger(int size) {
            i = nextValue++;
            buffer = new byte[size];
        }
        public void update() { i++; }
    }

    private class TaskGenerator implements KernelRunnable {
        public String getBaseTaskType() {
            return this.getClass().getName();
        }

        public void run() {
            txnScheduler.scheduleTask(new TestTask(), owner);
            taskScheduler.scheduleTask(this, owner, System.currentTimeMillis() + nextExponential(1000 / tasksPerSecond));
        }
    }

    private class TestTask implements KernelRunnable {
        
        int[] objectIndexes = new int[writes];

        public TestTask() {
            for (int i = 0; i < writes; i++) {
                objectIndexes[i] = (int) (Math.random() * (bindings - 1));
            }
        }

        public String getBaseTaskType() {
            return this.getClass().getName();
        }

        public void run() throws Exception {
            // read access a few dummy objects to simulate task overhead
            for (int i = 0; i < reads; i++) {
                dataService.getBinding("dummy" + i);
            }

            // write access specified number of objects
            for (int i = 0; i < writes; i++) {
                ManagedInteger obj = (ManagedInteger) dataService.getBinding("name" + objectIndexes[i]);
                obj.update();
            }
        }

    }

}
