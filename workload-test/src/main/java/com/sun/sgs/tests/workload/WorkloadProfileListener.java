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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

/**
 *
 */
public class WorkloadProfileListener implements ProfileListener {

    private static class Snapshot {
        public long completedTransactions;
        public long retriedTransactions;
        public long transactionRetries;
        public long failedTransactionAttempts;

        public long totalRuntime;
        public long totalLagtime;
        
        public long totalTasks;
        public long totalReadyCount;

        public void clear() {
            completedTransactions = 0;
            retriedTransactions = 0;
            transactionRetries = 0;
            failedTransactionAttempts = 0;
            totalRuntime = 0;
            totalLagtime = 0;
            totalTasks = 0;
            totalReadyCount = 0;
        }
    }

    private final Object lock = new Object();
    private Snapshot currentSnapshot = new Snapshot();
    private long period = 5000;
    private long lastWindowStart;

    public WorkloadProfileListener(Properties properties,
                                   Identity owner,
                                   ComponentRegistry registry) {
        RecurringTaskHandle handle = registry.getComponent(TaskScheduler.class).
                scheduleRecurringTask(new SnapshotRunnable(period), owner,
                                      System.currentTimeMillis() + period,
                                      period);
        lastWindowStart = System.currentTimeMillis();
        handle.start();
    }

    public void propertyChange(PropertyChangeEvent event) {

    }

    public void report(ProfileReport profileReport) {
        synchronized (lock) {
            currentSnapshot.totalTasks++;
            currentSnapshot.totalReadyCount += profileReport.getReadyCount();

            if (profileReport.wasTaskTransactional()) {
                if (profileReport.wasTaskSuccessful()) {
                    currentSnapshot.completedTransactions++;
                    currentSnapshot.totalRuntime += profileReport.getRunningTime();
                    currentSnapshot.totalLagtime += profileReport.getActualStartTime() - profileReport.getScheduledStartTime();

                    int tries = profileReport.getRetryCount();
                    if (tries > 1) {
                        currentSnapshot.retriedTransactions++;
                        currentSnapshot.transactionRetries += tries - 1;
                    }
                } else {
                    currentSnapshot.failedTransactionAttempts++;
                }
            }
        }
    }

    public void shutdown() {

    }

    private class SnapshotRunnable implements KernelRunnable {
        private final long reportPeriod;
        SnapshotRunnable(long reportPeriod) {
            this.reportPeriod = reportPeriod;
        }
        public String getBaseTaskType() {
            return SnapshotRunnable.class.getName();
        }
        public void run() throws Exception {
            synchronized (lock) {
                long actualPeriod = System.currentTimeMillis() - lastWindowStart;
                System.out.printf("Snapshot [period=%dms]:%n" +
                                  "  mean throughput  : %6.2f txn/sec%n" +
                                  "  mean latency     : %6.2f ms/txn%n" +
                                  "  mean runtime     : %6.2f ms/txn%n" +
                                  "  mean ready count : %6.2f ms/txn%n" +
                                  "  transaction attempts:%n" +
                                  "    successful     : %6d%n" +
                                  "    failed         : %6d%n" +
                                  "  transaction retries:%n" +
                                  "    retried        : %6d (%2.2f%%)%n" +
                                  "    mean retries per failure : %2.2f%n" +
                                  "    retries left   : %6d%n",
                                  reportPeriod,
                                  (double) currentSnapshot.completedTransactions / (double) actualPeriod * 1000.0,
                                  (double) (currentSnapshot.totalLagtime + currentSnapshot.totalRuntime) / (double) currentSnapshot.completedTransactions,
                                  (double) currentSnapshot.totalRuntime / (double) currentSnapshot.completedTransactions,
                                  (double) currentSnapshot.totalReadyCount / (double) currentSnapshot.totalTasks,
                                  currentSnapshot.completedTransactions,
                                  currentSnapshot.failedTransactionAttempts,
                                  currentSnapshot.retriedTransactions,
                                  (double) currentSnapshot.retriedTransactions / (double) currentSnapshot.completedTransactions * 100.0,
                                  currentSnapshot.retriedTransactions == 0 ? 0 : (double) currentSnapshot.transactionRetries / (double) currentSnapshot.retriedTransactions,
                                  currentSnapshot.failedTransactionAttempts - currentSnapshot.transactionRetries);

                lastWindowStart = System.currentTimeMillis();
                currentSnapshot.clear();
            }
        }
    }
}
