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
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Properties;

/**
 *
 */
public class WorkloadProfileListener implements ProfileListener {
    
    public static final String PACKAGE_NAME = "com.sun.sgs.tests.workload";
    
    public static final String SNAPSHOT_PERIOD_PROPERTY = PACKAGE_NAME + ".snapshot.period";
    public static final Integer DEFAULT_SNAPSHOT_PERIOD = 5000;

    public static final String TOTAL_WINDOW_PROPERTY = PACKAGE_NAME + ".total.window";
    public static final Integer DEFAULT_TOTAL_WINDOW = 12;

    private final Object lock = new Object();
    private final TaskScheduler taskScheduler;
    private final Identity owner;
    private final File workloadDirectory;
    private final File workloadFile;
    private final PrintWriter output;

    private Snapshot currentSnapshot;
    private Snapshot[] totalSnapshots;
    private long period;
    private int totalWindow;
    private int nextTotal = 0;
    private boolean totalAvailable = false;

    public WorkloadProfileListener(Properties properties,
                                   Identity owner,
                                   ComponentRegistry registry) throws Exception {
        PropertiesWrapper wProps = new PropertiesWrapper(properties);
        this.period = wProps.getIntProperty(SNAPSHOT_PERIOD_PROPERTY,
                                            DEFAULT_SNAPSHOT_PERIOD);
        this.totalWindow = wProps.getIntProperty(TOTAL_WINDOW_PROPERTY,
                                                 DEFAULT_TOTAL_WINDOW);
        this.totalSnapshots = new Snapshot[totalWindow];

        this.owner = owner;
        this.taskScheduler = registry.getComponent(TaskScheduler.class);
        this.workloadDirectory = new File("workload");
        if (workloadDirectory.exists() && !workloadDirectory.isDirectory()) {
            throw new RuntimeException("workload directory is a file");
        }
        if (!workloadDirectory.exists() && !workloadDirectory.mkdir()) {
            throw new RuntimeException("couldn't create workload directory");
        }
        this.workloadFile = new File(workloadDirectory, String.valueOf(System.currentTimeMillis()));
        this.output = new PrintWriter(new BufferedWriter(new FileWriter(workloadFile)), true);
        output.println("Throughput, Latency, Max Latency, Runtime, Ready Count, Successful, " +
                       "Failed, Retried, Retry Percentage, Retries Per Failure, Retries Left" +
                       ", Total, " +
                       "Throughput, Latency, Max Latency, Runtime, Ready Count, Successful, " +
                       "Failed, Retried, Retry Percentage, Retries Per Failure, Retries Left");

        RecurringTaskHandle handle = taskScheduler.
                scheduleRecurringTask(new SnapshotRunnable(period), owner,
                                      System.currentTimeMillis() + period,
                                      period);
        currentSnapshot = new Snapshot();
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

                    long latency = profileReport.getRunningTime() + profileReport.getActualStartTime() - profileReport.getScheduledStartTime();
                    if (latency > currentSnapshot.maxLatency) {
                        currentSnapshot.maxLatency = latency;
                    }

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
    
    private static class Stats {
        long reportPeriod;
        long actualPeriod;
        double throughput;
        double latency;
        long maxLatency;
        double runtime;
        double readyCount;
        long successfulAttempts;
        long failedAttempts;
        long retriedAttempts;
        double retryPercentage;
        double retriesPerFailure;
        long retriesLeft;
        
        public Stats(Snapshot s, long reportPeriod) {
            this.reportPeriod = reportPeriod;
            actualPeriod = System.currentTimeMillis() - s.windowStart;
            throughput = (double) s.completedTransactions / (double) actualPeriod * 1000.0;
            latency = (double) (s.totalLagtime + s.totalRuntime) / (double) s.completedTransactions;
            maxLatency = s.maxLatency;
            runtime = (double) s.totalRuntime / (double) s.completedTransactions;
            readyCount = (double) s.totalReadyCount / (double) s.totalTasks;
            successfulAttempts = s.completedTransactions;
            failedAttempts = s.failedTransactionAttempts;
            retriedAttempts = s.retriedTransactions;
            retryPercentage = (double) s.retriedTransactions / (double) s.completedTransactions * 100.0;
            retriesPerFailure = s.retriedTransactions == 0 ? 0 : (double) s.transactionRetries / (double) s.retriedTransactions;
            retriesLeft = s.failedTransactionAttempts - s.transactionRetries;
        }
        
        public String readableFormat() {
            return String.format("Snapshot [period=%dms]:%n" +
                                 "  mean throughput  : %6.2f txn/sec%n" +
                                 "  mean latency     : %6.2f ms/txn%n" +
                                 "  max latency      : %6d ms%n" +
                                 "  mean runtime     : %6.2f ms/txn%n" +
                                 "  mean ready count : %6.2f tasks%n" +
                                 "  transaction attempts:%n" +
                                 "    successful     : %6d%n" +
                                 "    failed         : %6d%n" +
                                 "  transaction retries:%n" +
                                 "    retried        : %6d (%2.2f%%)%n" +
                                 "    mean retries per failure : %2.2f%n" +
                                 "    retries left   : %6d%n",
                                 reportPeriod,
                                 throughput,
                                 latency,
                                 maxLatency,
                                 runtime,
                                 readyCount,
                                 successfulAttempts,
                                 failedAttempts,
                                 retriedAttempts,
                                 retryPercentage,
                                 retriesPerFailure,
                                 retriesLeft);
        }
        
        public String csvFormat() {
            return String.format("%.2f, %.2f, %d, %.2f, %.2f, %d, %d, %d, %.2f, %.2f, %d",
                                 throughput, latency, maxLatency, runtime, readyCount, successfulAttempts, failedAttempts,
                                 retriedAttempts, retryPercentage, retriesPerFailure, retriesLeft);
        }
    }

    private static class Snapshot {
        public long windowStart;
        public long completedTransactions;
        public long retriedTransactions;
        public long transactionRetries;
        public long failedTransactionAttempts;

        public long totalRuntime;
        public long totalLagtime;
        public long maxLatency;

        public long totalTasks;
        public long totalReadyCount;

        public Snapshot() {
            windowStart = System.currentTimeMillis();
        }

        public Snapshot(Snapshot[] shots) {
            windowStart = System.currentTimeMillis();
            for (Snapshot s : shots) {
                if (s.windowStart < windowStart) {
                    windowStart = s.windowStart;
                }
                if (s.maxLatency > maxLatency) {
                    maxLatency = s.maxLatency;
                }
                completedTransactions += s.completedTransactions;
                retriedTransactions += s.retriedTransactions;
                transactionRetries += s.transactionRetries;
                failedTransactionAttempts += s.failedTransactionAttempts;
                totalRuntime += s.totalRuntime;
                totalLagtime += s.totalLagtime;
                totalTasks += s.totalTasks;
                totalReadyCount += s.totalReadyCount;
            }
        }

        public void clear() {
            windowStart = System.currentTimeMillis();
            completedTransactions = 0;
            retriedTransactions = 0;
            transactionRetries = 0;
            failedTransactionAttempts = 0;
            totalRuntime = 0;
            totalLagtime = 0;
            maxLatency = 0;
            totalTasks = 0;
            totalReadyCount = 0;
        }
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
                Stats latestStats = new Stats(currentSnapshot, reportPeriod);
                System.out.println(latestStats.readableFormat());
                
                totalSnapshots[nextTotal++] = currentSnapshot;
                if (nextTotal >= totalWindow) {
                    nextTotal = 0;
                    totalAvailable = true;
                }
                taskScheduler.scheduleTask(new TotalRunnable(latestStats), owner);
                currentSnapshot = new Snapshot();
            }
        }
    }

    private class TotalRunnable implements KernelRunnable {

        private Stats latest;

        public TotalRunnable(Stats latest) {
            this.latest = latest;
        }

        public String getBaseTaskType() {
            return TotalRunnable.class.getName();
        }

        public void run() throws Exception {
            synchronized (output) {
                output.print(latest.csvFormat());
                if (totalAvailable) {
                    Stats total = new Stats(new Snapshot(totalSnapshots), totalSnapshots.length * period);
                    output.print(", Total, " + total.csvFormat());
                }
                output.println();
            }
        }

    }
}
