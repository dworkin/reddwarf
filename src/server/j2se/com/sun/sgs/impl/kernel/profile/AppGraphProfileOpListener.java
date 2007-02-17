
package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfiledOperation;
import com.sun.sgs.kernel.ProfileOperationListener;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;


/**
 * This implementation of <code>ProfileOperationListener</code> tracks the
 * set of operatins in a given task, and uses this to create a graph of
 * task "fingerprints." It is still very experimental, and in practice would
 * actually be part of the scheduler, not a reporting mechanism, but for
 * testing the idea it just reports some basic statistics. By default the
 * report time interval is 5 seconds.
 * <p>
 * This listener reports its findings on a server socket. Any number of
 * users may connect to that socket to watch the reports. The default
 * port used is 43006.
 * <p>
 * The <code>com.sun.sgs.impl.kernel.profile.AppGraphProfileOpListener.</code>
 * root is used for all properties in this class. The <code>reportPort</code>
 * key is used to specify an alternate port on which to report profiling
 * data. The <code>reportPeriod</code> key is used to specify the length of
 * time, in milliseconds, between reports.
 */
public class AppGraphProfileOpListener implements ProfileOperationListener {

    // the map of users to their current node in the graph
    private HashMap<TaskOwner,Node> userMap;

    // the map from application to its tracked detail
    private HashMap<KernelAppContext,AppInfo> appMap;

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    /**
     * Creates an instance of <code>AppGraphProfileOpListener</code>.
     *
     * @param properties the <code>Properties</code> for this listener
     * @param owner the <code>TaskOwner</code> to use for all tasks run by
     *              this listener
     * @param taskScheduler the <code>TaskScheduler</code> to use for
     *                      running short-lived or recurring tasks
     * @param resourceCoord the <code>ResourceCoordinator</code> used to
     *                      run any long-lived tasks
     *
     * @throws IOException if the server socket cannot be created
     */
    public AppGraphProfileOpListener(Properties properties, TaskOwner owner,
                                     TaskScheduler taskScheduler,
                                     ResourceCoordinator resourceCoord)
        throws IOException
    {
        userMap = new HashMap<TaskOwner,Node>();
        appMap = new HashMap<KernelAppContext,AppInfo>();

        int port =
            Integer.parseInt(properties.
                             getProperty(getClass().getName() + ".reportPort",
                                         "43006"));
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod =
            Long.parseLong(properties.
                           getProperty(getClass().getName() + ".reportPeriod",
                                       "5000"));
        handle = taskScheduler.
            scheduleRecurringTask(new AppGraphRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void notifyNewOp(ProfiledOperation op) {
        // these aren't needed by this listener
    }

    /**
     * {@inheritDoc}
     */
    public void notifyThreadCount(int schedulerThreadCount) {
        // for now we don't care about this statistic
    }

    /**
     * {@inheritDoc}
     */
    public void report(KernelRunnable task, boolean transactional,
                       TaskOwner owner, long scheduledStartTime,
                       long actualStartTime, long runningTime,
                       List<ProfiledOperation> ops, int retryCount,
                       boolean succeeded) {
        Node currentNode = userMap.get(owner);

        // calculate the task's fingerprint
        String fingerprint = task.getClass().getName();
        for (ProfiledOperation op : ops)
            fingerprint += "." + op.getId();

        // get the detail for this application, creating it if the
        // application hasn't been observed yet
        AppInfo appInfo = appMap.get(owner.getContext());
        if (appInfo == null) {
            appInfo = new AppInfo(owner.getContext().toString());
            appMap.put(owner.getContext(), appInfo);
        }

        // find the node for the reported task based on its fingerprint,
        // inserting a new node if nothing aleady matches
        Node nextNode = appInfo.taskMap.get(fingerprint);
        if (nextNode == null) {
            nextNode = new Node(appInfo);
            appInfo.taskMap.put(fingerprint, nextNode);
        }

        // notify the graph of the transition, and update the user's
        // position in the graph
        if (currentNode != null)
            currentNode.transitionedTo(nextNode);
        userMap.put(owner, nextNode);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        handle.cancel();
    }

    /**
     * Private utility class that manages application detail
     */
    private static class AppInfo {
        // the name of the application
        final String name;
        // the task graph for this application
        HashMap<String,Node> taskMap = new HashMap<String,Node>();
        // the number of observed correct and incorrect guesses for the
        // entire application
        long right = 0;
        long wrong = 0;
        AppInfo(String name) { this.name = name; }
    }

    /**
     * Private class that represents a single node in an application graph.
     */
    private class Node {
        // the application who's graph this node is part of
        private final AppInfo owningApp;
        // the observed transitions from this node
        private HashMap<Node,Integer> nodeMap = new HashMap<Node,Integer>();
        Node(AppInfo owningApp) { this.owningApp = owningApp; }
        /**
         * Tells this node that a user transitioned from it to the given
         * node. This will add the new node to the transition graph, if not
         * already present, and update the transition count.
         */
        public void transitionedTo(Node node) {
            // first off, figure out what we would have predicted, based on
            // the simple metric of which transition count is highest
            int highestCount = 0;
            Node percentageMatch = null;
            for (Entry<Node,Integer> entry : nodeMap.entrySet()) {
                if (entry.getValue() > highestCount) {
                    highestCount = entry.getValue();
                    percentageMatch = entry.getKey();
                }
            }

            // record whether the guess would have been right
            if (percentageMatch == node)
                owningApp.right++;
            else
                owningApp.wrong++;

            // now make sure the new node is in our transition graph with
            // the right transition count
            int count = 1;
            if (nodeMap.containsKey(node))
                count = nodeMap.get(node) + 1;
            nodeMap.put(node, count);
        }
    }

    /**
     * Private internal class that is used to run a recurring task that
     * reports on the collected data.
     */
    private class AppGraphRunnable implements KernelRunnable {
        public void run() throws Exception {
            double allRight = 0;
            double allTransitions = 0;
            String reportStr = "";
            for (AppInfo appInfo : appMap.values()) {
                long totalTransitions = appInfo.right + appInfo.wrong;
                allTransitions += totalTransitions;
                allRight += appInfo.right; 
                double correctTrasitions =
                    (double)(appInfo.right) / (double)totalTransitions;
                reportStr += "AppGraph for " + appInfo.name + ":\n";
                reportStr += "  Nodes=" + appInfo.taskMap.size() +
                    "  Transitions=" + totalTransitions +
                    "  PercentCorrect=" + correctTrasitions + "\n";
            }
            double overall = 100 * (allRight / allTransitions);
            reportStr += "AppGraphOverall: " + overall + "%\n\n";

            networkReporter.report(reportStr);
        }
    }

}
