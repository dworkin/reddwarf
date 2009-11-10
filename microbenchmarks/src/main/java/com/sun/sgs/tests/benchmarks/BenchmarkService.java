/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.sgs.tests.benchmarks;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 */
public class BenchmarkService implements Service {

    // logger for this class
    private static final LoggerWrapper logger = new LoggerWrapper(
            Logger.getLogger(BenchmarkService.class.getName()));
    
    private final TransactionScheduler txnScheduler;
    private final TransactionProxy txnProxy;
    private final Identity owner;

    private final DataService dataService;
    private final TaskService taskService;
    //private final ChannelManager channelManager;

    public BenchmarkService(Properties props,
                            ComponentRegistry systemRegistry,
                            TransactionProxy txnProxy) {
        this.txnScheduler = systemRegistry.getComponent(
                TransactionScheduler.class);
        this.txnProxy = txnProxy;
        this.owner = txnProxy.getCurrentOwner();

        this.dataService = txnProxy.getService(DataService.class);
        this.taskService = txnProxy.getService(TaskService.class);
        //this.channelManager = txnProxy.getService(ChannelManager.class);
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void ready() throws Exception {
        new Thread(new BenchmarkThread()).start();
    }

    @Override
    public void shutdown() {

    }

    private static void log(long totalNanos, long numTxns, String... messages) {
        String nanosPerOperation = String.format("  %8d ns/txn", totalNanos / numTxns);
        System.out.println(nanosPerOperation + ", " + messages[0]);
        for (int i = 1; i < messages.length; i++) {
            System.out.println("                   " + messages[i]);
        }
    }

    private class BenchmarkThread implements Runnable {
        private ThreadMXBean threads;
        private int counter;
        public BenchmarkThread() {
            threads = ManagementFactory.getThreadMXBean();
        }

        public void run() {
            sleep(1000);
            System.out.println();
            System.out.println("Transaction Overhead Benchmark");
            txnOverhead();
            System.out.println();

            System.out.println("DataManager Overhead Benchmark");
            /*createReferenceOverhead(1000 * 10, 100, 100);
            createReferenceOverhead(1000 * 10, 100, 1000);
            setBindingOverhead(1000 * 10, 100, 100);
            setBindingOverhead(1000 * 10, 100, 1000);*/
            // getBinding all cold
            getBindingOverhead(1000 * 10, 100, 100, false, false);
            getBindingOverhead(1000 * 10, 100, 1000, false, false);
            // getBinding hot
            /*getBindingOverhead(1000 * 10, 100, 100, true, false);
            getBindingOverhead(1000 * 10, 100, 1000, true, false);
            // getBindingForUpdate all cold
            getBindingOverhead(1000 * 10, 100, 100, false, true);
            getBindingOverhead(1000 * 10, 100, 1000, false, true);
            // getBindingForUpdate hot
            getBindingOverhead(1000 * 10, 100, 100, true, true);
            getBindingOverhead(1000 * 10, 100, 1000, true, true);*/
            removeBindingOverhead(100, 100, 100);
            removeBindingOverhead(100, 100, 1000);
            removeObjectOverhead(100, 100, 100);
            removeObjectOverhead(100, 100, 1000);
            /*getObjectIdOverhead(1000 * 10, 100, 100);
            getObjectIdOverhead(1000 * 10, 100, 1000);
            markForUpdateOverhead(1000 * 10, 100, 100);
            markForUpdateOverhead(1000 * 10, 100, 1000);*/

        }

        private void txnOverhead() {
            long startNanos = System.nanoTime();
            long txns = 1000 * 1000;
            for (long i = 0; i < txns; i++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "Empty"; }
                        public void run() {}
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "empty transactions");
        }

        private void createReferenceOverhead(final long txns, final long ops, final int objectSize) {
            long startNanos = System.nanoTime();
            for (long i = 0; i < txns; i++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "createReference"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                ManagedReference<ManagedInteger> tmp = dataService.createReference(new ManagedInteger(objectSize));
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "createReference, " + objectSize + " bytes, " + ops + " times");
        }

        private void setBindingOverhead(final long txns, final long ops, final int objectSize) {
            long startNanos = System.nanoTime();
            for (long i = 0; i < txns; i++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "setBinding"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                dataService.setBinding("name" + j, new ManagedInteger(objectSize));
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "setBinding, " + objectSize + " bytes, " + ops + " times");
        }

        private void getBindingOverhead(final long txns,
                                        final long ops,
                                        final int objectSize,
                                        final boolean hot,
                                        final boolean forUpdate) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long startNanos = System.nanoTime();
            for (long i = 0; i < txns; i++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "getBinding"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                if (hot && forUpdate) {
                                    ManagedInteger tmp = (ManagedInteger) dataService.getBindingForUpdate("name0");
                                } else if (!hot && forUpdate) {
                                    ManagedInteger tmp = (ManagedInteger) dataService.getBindingForUpdate("name" + j);
                                } else if (hot && !forUpdate) {
                                    ManagedInteger tmp = (ManagedInteger) dataService.getBinding("name0");
                                } else {
                                    ManagedInteger tmp = (ManagedInteger) dataService.getBinding("name" + j);
                                }
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();

            String forUpdateIndicator = forUpdate ? "getBindingForUpdate" : "getBinding";
            String hotIndicator = hot ? "1 cold, " + (ops - 1) + " hot" : ops + " cold";
            log(endNanos - startNanos, txns,
                forUpdateIndicator + ", " + objectSize + " bytes, " + ops + " times, " + hotIndicator);
        }

        private void removeBindingOverhead(final long txns,
                                           final long ops,
                                           final int objectSize) {
            // set the initial bindings
            setBindings(txns * ops, objectSize);

            long startNanos = System.nanoTime();
            counter = 0;
            for (long i = 0; i < txns; i++, counter += ops) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "removeBinding"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                dataService.removeBinding("name" + (j + counter));
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "removeBinding, " + objectSize + " bytes, " + ops + " times");
        }

        private void removeObjectOverhead(final long txns,
                                          final long ops,
                                          final int objectSize) {
            // set the initial bindings
            setBindings(txns * ops, objectSize);

            long startNanos = System.nanoTime();
            counter = 0;
            for (long i = 0; i < txns; i++, counter += ops) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "removeObject"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                ManagedInteger tmp = (ManagedInteger) dataService.getBinding("name" + (j + counter));
                                dataService.removeObject(tmp);
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "getBinding, " + objectSize + " bytes, " + ops + " times",
                "removeObject, " + objectSize + " bytes, " + ops + " times");
        }

        private void getObjectIdOverhead(final long txns,
                                         final long ops,
                                         final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long startNanos = System.nanoTime();
            counter = 0;
            for (long i = 0; i < txns; i++, counter++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "getObjectId"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                ManagedInteger tmp = (ManagedInteger) dataService.getBinding("name" + j);
                                dataService.getObjectId(tmp);
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "getBinding, " + objectSize + " bytes, " + ops + " times",
                "getObjectId, " + objectSize + " bytes, " + ops + " times");
        }

        private void markForUpdateOverhead(final long txns,
                                           final long ops,
                                           final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long startNanos = System.nanoTime();
            counter = 0;
            for (long i = 0; i < txns; i++, counter++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "markForUpdate"; }
                        public void run() {
                            for (int j = 0; j < ops; j++) {
                                ManagedInteger tmp = (ManagedInteger) dataService.getBinding("name" + j);
                                dataService.markForUpdate(tmp);
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            long endNanos = System.nanoTime();
            log(endNanos - startNanos, txns,
                "getBinding, " + objectSize + " bytes, " + ops + " times",
                "markForUpdate, " + objectSize + " bytes, " + ops + " times");
        }

        private void setBindings(final long numBindings, final int objectSize) {
            counter = 0;
            while (counter < numBindings) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() {
                            return "setBinding";
                        }
                        public void run() {
                            for (; counter < numBindings && taskService.shouldContinue(); counter++) {
                                dataService.setBinding("name" + counter, new ManagedInteger(objectSize));
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (Exception ignore) {
            }
        }
    }


    private static class ManagedInteger implements ManagedObject, Serializable {
        public int i = 0;
        public byte[] buffer;
        public ManagedInteger(int size) {
            buffer = new byte[size];
        }
        public void update() { i++; }
    }

}
