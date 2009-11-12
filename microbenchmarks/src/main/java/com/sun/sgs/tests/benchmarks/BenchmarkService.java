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
import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.TransactionAbortedException;
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

    private long txnTime;
    private long createReference100;
    private long createReference1000;
    private long setBinding100;
    private long setBinding1000;
    private long getBindingCold100;
    private long getBindingCold1000;
    private long getBindingHot100;
    private long getBindingHot1000;
    private long getBindingForUpdateCold100;
    private long getBindingForUpdateCold1000;
    private long getBindingForUpdateHot100;
    private long getBindingForUpdateHot1000;
    private long removeBinding100;
    private long removeBinding1000;
    private long removeObject100;
    private long removeObject1000;
    private long getObjectId100;
    private long getObjectId1000;
    private long markForUpdate100;
    private long markForUpdate1000;
    private long getCold100;
    private long getCold1000;
    private long getHot100;
    private long getHot1000;
    private long getForUpdateCold100;
    private long getForUpdateCold1000;
    private long getForUpdateHot100;
    private long getForUpdateHot1000;

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

    private static long log(long totalNanos, long numTxns, String... messages) {
        long nanosPerTxn = totalNanos / numTxns;
        String nanosPerTxnOutput = String.format("  %8d ns/txn", nanosPerTxn);
        System.out.println(nanosPerTxnOutput + ", " + messages[0]);
        for (int i = 1; i < messages.length; i++) {
            System.out.println("                   " + messages[i]);
        }

        return nanosPerTxn;
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
            txnTime = txnOverhead();
            System.out.println();

            System.out.println("DataManager Overhead Benchmark");
            createReference100 = createReferenceOverhead(10000, 100, 100);
            createReference1000 = createReferenceOverhead(10000, 10, 1000);
            setBinding100 = setBindingOverhead(10000, 100, 100);
            setBinding1000 = setBindingOverhead(10000, 10, 1000);
            getBindingCold100 = getBindingColdOverhead(10000, 100, 100);
            getBindingCold1000 = getBindingColdOverhead(10000, 10, 1000);
            getBindingHot100 = getBindingHotOverhead(10000, 100, 100);
            getBindingHot1000 = getBindingHotOverhead(10000, 10, 1000);
            getBindingForUpdateCold100 = getBindingForUpdateColdOverhead(10000, 100, 100);
            getBindingForUpdateCold1000 = getBindingForUpdateColdOverhead(10000, 10, 1000);
            getBindingForUpdateHot100 = getBindingForUpdateHotOverhead(10000, 100, 100);
            getBindingForUpdateHot1000 = getBindingForUpdateHotOverhead(10000, 10, 1000);
            removeBinding100 = removeBindingOverhead(1000, 100, 100);
            removeBinding1000 = removeBindingOverhead(1000, 10, 1000);
            removeObject100 = removeObjectOverhead(1000, 100, 100);
            removeObject1000 = removeObjectOverhead(1000, 10, 1000);
            getObjectId100 = getObjectIdOverhead(10000, 100, 100);
            getObjectId1000 = getObjectIdOverhead(10000, 10, 1000);
            markForUpdate100 = markForUpdateOverhead(10000, 100, 100);
            markForUpdate1000 = markForUpdateOverhead(10000, 10, 1000);
            getCold100 = getColdOverhead(10000, 100, 100);
            getCold1000 = getColdOverhead(10000, 10, 1000);
            getHot100 = getHotOverhead(10000, 100, 100);
            getHot1000 = getHotOverhead(10000, 10, 1000);
            getForUpdateCold100 = getForUpdateColdOverhead(10000, 100, 100);
            getForUpdateCold1000 = getForUpdateColdOverhead(10000, 10, 1000);
            getForUpdateHot100 = getForUpdateHotOverhead(10000, 100, 100);
            getForUpdateHot1000 = getForUpdateHotOverhead(10000, 10, 1000);
            System.out.println();

            System.out.println("DataManager Overhead Summary");
            System.out.printf("  %8d ns/op, %s\n", createReference100, "createReference, 100 bytes");
            System.out.printf("  %8d ns/op, %s\n", createReference1000, "createReference, 1000 bytes");
            System.out.printf("  %8d ns/op, %s\n", setBinding100, "setBinding, 100 bytes");
            System.out.printf("  %8d ns/op, %s\n", setBinding1000, "setBinding, 1000 bytes");
            System.out.printf("  %8d ns/op, %s\n", getBindingCold100, "getBinding, 100 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getBindingCold1000, "getBinding, 1000 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getBindingHot100, "getBinding, 100 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", getBindingHot1000, "getBinding, 1000 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", getBindingForUpdateCold100, "getBindingForUpdate, 100 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getBindingForUpdateCold1000, "getBindingForUpdate, 1000 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getBindingForUpdateHot100, "getBindingForUpdate, 100 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", getBindingForUpdateHot1000, "getBindingForUpdate, 1000 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", removeBinding100, "removeBinding, 100 bytes");
            System.out.printf("  %8d ns/op, %s\n", removeBinding1000, "removeBinding, 1000 bytes");
            System.out.printf("  %8d ns/op, %s\n", removeObject100, "removeObject, 100 bytes");
            System.out.printf("  %8d ns/op, %s\n", removeObject1000, "removeObject, 1000 bytes");
            System.out.printf("  %8d ns/op, %s\n", getObjectId100, "getObjectId, 100 bytes");
            System.out.printf("  %8d ns/op, %s\n", getObjectId1000, "getObjectId, 1000 bytes");
            System.out.printf("  %8d ns/op, %s\n", markForUpdate100, "markForUpdate, 100 bytes");
            System.out.printf("  %8d ns/op, %s\n", markForUpdate1000, "markForUpdate, 1000 bytes");
            System.out.printf("  %8d ns/op, %s\n", getCold100, "get, 100 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getCold1000, "get, 1000 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getHot100, "get, 100 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", getHot1000, "get, 1000 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", getForUpdateCold100, "getForUpdate, 100 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getForUpdateCold1000, "getForUpdate, 1000 bytes, cold");
            System.out.printf("  %8d ns/op, %s\n", getForUpdateHot100, "getForUpdate, 100 bytes, hot");
            System.out.printf("  %8d ns/op, %s\n", getForUpdateHot1000, "getForUpdate, 1000 bytes, hot");
        }

        private long txnOverhead() {
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
                    return -1;
                }
            }
            long endNanos = System.nanoTime();
            return log(endNanos - startNanos, txns,
                       "empty transactions");
        }

        private long createReferenceOverhead(final long txns, final long ops, final int objectSize) {
            long time = performOperations(txns, ops, objectSize, Operation.CREATE_REFERENCE);
            if (time < 0) { return -1; }
            long perTxn = log(time, txns,
                              "createReference, " + objectSize + " bytes, " + ops + " times");
            return (perTxn - txnTime) / ops;
        }

        private long setBindingOverhead(final long txns, final long ops, final int objectSize) {
            long time = performOperations(txns, ops, objectSize, Operation.SET_BINDING);
            if (time < 0) { return -1; }
            long perTxn = log(time, txns,
                              "setBinding, " + objectSize + " bytes, " + ops + " times");
            return (perTxn - txnTime) / ops;
        }

        private long getBindingColdOverhead(final long txns,
                                            final long ops,
                                            final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_BINDING_COLD);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, " + ops + " times, " + ops + " cold");
            return (perTxn - txnTime) / ops;
        }

        private long getBindingHotOverhead(final long txns,
                                           final long ops,
                                           final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_BINDING_HOT);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, " + ops + " times, 1 cold, " + (ops - 1) + " hot");
            long coldOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            return (perTxn - txnTime - coldOverhead) / (ops - 1);
        }

        private long getBindingForUpdateColdOverhead(final long txns,
                                                     final long ops,
                                                     final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_BINDING_FOR_UPDATE_COLD);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBindingForUpdate, " + objectSize + " bytes, " + ops + " times, " + ops + " cold");
            return (perTxn - txnTime) / ops;
        }

        private long getBindingForUpdateHotOverhead(final long txns,
                                                    final long ops,
                                                    final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_BINDING_FOR_UPDATE_HOT);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBindingForUpdate, " + objectSize + " bytes, " + ops + " times, 1 cold, " + (ops - 1) + " hot");
            long coldOverhead = objectSize == 100 ? getBindingForUpdateCold100 : getBindingForUpdateCold1000;
            return (perTxn - txnTime - coldOverhead) / (ops - 1);
        }

        private long removeBindingOverhead(final long txns,
                                           final long ops,
                                           final int objectSize) {
            // set the initial bindings
            setBindings(txns * ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.REMOVE_BINDING);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "removeBinding, " + objectSize + " bytes, " + ops + " times");
            return (perTxn - txnTime) / ops;
        }

        private long removeObjectOverhead(final long txns,
                                          final long ops,
                                          final int objectSize) {
            // set the initial bindings
            setBindings(txns * ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.REMOVE_OBJECT);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, " + ops + " times",
                              "removeObject, " + objectSize + " bytes, " + ops + " times");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            return (perTxn - txnTime - getBindingOverhead * ops) / ops;
        }

        private long getObjectIdOverhead(final long txns,
                                         final long ops,
                                         final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_OBJECT_ID);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, " + ops + " times",
                              "getObjectId, " + objectSize + " bytes, " + ops + " times");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            return (perTxn - txnTime - getBindingOverhead * ops) / ops;
        }

        private long markForUpdateOverhead(final long txns,
                                           final long ops,
                                           final int objectSize) {
            // set the initial bindings
            setBindings(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.MARK_FOR_UPDATE);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, " + ops + " times",
                              "markForUpdate, " + objectSize + " bytes, " + ops + " times");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            return (perTxn - txnTime - getBindingOverhead * ops) / ops;
        }

        private long getColdOverhead(final long txns,
                                     final long ops,
                                     final int objectSize) {
            setReferences(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_COLD);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, 1 time",
                              "get, " + objectSize + " bytes, " + (ops - 1) + " times, cold");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            return (perTxn - txnTime - getBindingOverhead) / (ops - 1);
        }

        private long getHotOverhead(final long txns,
                                    final long ops,
                                    final int objectSize) {
            setReferences(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_HOT);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, 1 time",
                              "get, " + objectSize + " bytes, 1 time, cold",
                              "get, " + objectSize + " bytes, " + (ops - 2) + " times, hot");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            long getOverhead = objectSize == 100 ? getCold100 : getCold1000;
            return (perTxn - txnTime - getBindingOverhead - getOverhead) / (ops - 2);
        }

        private long getForUpdateColdOverhead(final long txns,
                                              final long ops,
                                              final int objectSize) {
            setReferences(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_FOR_UPDATE_COLD);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, 1 time",
                              "getForUpdate, " + objectSize + " bytes, " + (ops - 1) + " times, cold");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            return (perTxn - txnTime - getBindingOverhead) / (ops - 1);
        }

        private long getForUpdateHotOverhead(final long txns,
                                             final long ops,
                                             final int objectSize) {
            setReferences(ops, objectSize);

            long time = performOperations(txns, ops, objectSize, Operation.GET_FOR_UPDATE_HOT);
            if (time < 0) { return -1; }

            long perTxn = log(time, txns,
                              "getBinding, " + objectSize + " bytes, 1 time",
                              "getForUpdate, " + objectSize + " bytes, 1 time, cold",
                              "getForUpdate, " + objectSize + " bytes, " + (ops - 2) + " times, hot");
            long getBindingOverhead = objectSize == 100 ? getBindingCold100 : getBindingCold1000;
            long getForUpdateOverhead = objectSize == 100 ? getForUpdateCold100 : getForUpdateCold1000;
            return (perTxn - txnTime - getBindingOverhead - getForUpdateOverhead) / (ops - 2);
        }

        private void setReferences(final long numReferences, final int objectSize) {
            try {
                txnScheduler.runTask(new KernelRunnable() {
                    public String getBaseTaskType() {
                        return "setBinding";
                    }
                    public void run() {
                        ManagedInteger node = new ManagedInteger(objectSize);
                        dataService.setBinding("top", node);
                        for (int j = 0; j < numReferences; j++) {
                            node.setNextNode(new ManagedInteger(objectSize));
                            node = node.nextNode.get();
                        }
                    }
                }, owner);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        private void setBindings(final long numBindings, final int objectSize) {
            counter = 0;
            for (; counter < numBindings; counter++) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() {
                            return "setBinding";
                        }
                        public void run() {
                            dataService.setBinding("name" + counter, new ManagedInteger(objectSize));
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        private long performOperations(final long txns,
                                       final long ops,
                                       final int objectSize,
                                       final Operation op) {
            long startNanos = System.nanoTime();
            counter = 0;
            for (long i = 0; i < txns; i++, counter += ops) {
                try {
                    txnScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() { return "markForUpdate"; }
                        public void run() {
                            ManagedInteger tmp = null;
                            for (int j = 0; j < ops; j++) {
                                switch(op) {
                                    case CREATE_REFERENCE:
                                        dataService.createReference(new ManagedInteger(objectSize));
                                        break;
                                    case SET_BINDING:
                                        dataService.setBinding("name" + j, new ManagedInteger(objectSize));
                                        break;
                                    case GET_BINDING_COLD:
                                        tmp = (ManagedInteger) dataService.getBinding("name" + j);
                                        break;
                                    case GET_BINDING_HOT:
                                        tmp = (ManagedInteger) dataService.getBinding("name0");
                                        break;
                                    case GET_BINDING_FOR_UPDATE_COLD:
                                        tmp = (ManagedInteger) dataService.getBindingForUpdate("name" + j);
                                        break;
                                    case GET_BINDING_FOR_UPDATE_HOT:
                                        tmp = (ManagedInteger) dataService.getBindingForUpdate("name0");
                                        break;
                                    case REMOVE_BINDING:
                                        dataService.removeBinding("name" + (j + counter));
                                        break;
                                    case REMOVE_OBJECT:
                                        tmp = (ManagedInteger) dataService.getBinding("name" + (j + counter));
                                        dataService.removeObject(tmp);
                                        break;
                                    case GET_OBJECT_ID:
                                        tmp = (ManagedInteger) dataService.getBinding("name" + j);
                                        dataService.getObjectId(tmp);
                                        break;
                                    case MARK_FOR_UPDATE:
                                        tmp = (ManagedInteger) dataService.getBinding("name" + j);
                                        dataService.markForUpdate(tmp);
                                        break;
                                    case GET_COLD:
                                        if (j == 0) {
                                            tmp = (ManagedInteger) dataService.getBinding("top");
                                        } else {
                                            tmp = tmp.nextNode.get();
                                        }
                                        break;
                                    case GET_HOT:
                                        if (j == 0) {
                                            tmp = (ManagedInteger) dataService.getBinding("top");
                                        } else {
                                            tmp.nextNode.get();
                                        }
                                        break;
                                    case GET_FOR_UPDATE_COLD:
                                        if (j == 0) {
                                            tmp = (ManagedInteger) dataService.getBinding("top");
                                        } else {
                                            tmp = tmp.nextNode.getForUpdate();
                                        }
                                        break;
                                    case GET_FOR_UPDATE_HOT:
                                        if (j == 0) {
                                            tmp = (ManagedInteger) dataService.getBinding("top");
                                        } else {
                                            tmp.nextNode.getForUpdate();
                                        }
                                        break;
                                }
                            }
                        }
                    }, owner);
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
            }
            long endNanos = System.nanoTime();

            return endNanos - startNanos;
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (Exception ignore) {
            }
        }
    }


    private static class ManagedInteger implements ManagedObject, Serializable {
        private static int nextValue = 0;
        public int i;
        public byte[] buffer;
        public ManagedReference<ManagedInteger> nextNode = null;
        public ManagedInteger(int size) {
            i = nextValue++;
            buffer = new byte[size];
        }
        public void update() { i++; }
        public void setNextNode(ManagedInteger node) {
            nextNode = AppContext.getDataManager().createReference(node);
        }
    }

    private static enum Operation {
        CREATE_REFERENCE,
        SET_BINDING,
        GET_BINDING_COLD,
        GET_BINDING_HOT,
        GET_BINDING_FOR_UPDATE_COLD,
        GET_BINDING_FOR_UPDATE_HOT,
        REMOVE_BINDING,
        REMOVE_OBJECT,
        GET_OBJECT_ID,
        MARK_FOR_UPDATE,
        GET_COLD,
        GET_HOT,
        GET_FOR_UPDATE_COLD,
        GET_FOR_UPDATE_HOT
    }

}
