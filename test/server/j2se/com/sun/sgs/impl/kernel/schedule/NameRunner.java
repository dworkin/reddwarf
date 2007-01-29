
package com.sun.sgs.impl.kernel.schedule;

import org.junit.internal.runners.InitializationError; 
import org.junit.internal.runners.TestClassRunner;

import org.junit.runner.Description;

import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;


/**
 * This is a custom implementation of JUunit4's <code>TestClassRunner</code>
 * that adds support for reporting the name of each test when it starts.
 */
public class NameRunner extends TestClassRunner {

    private static String testName;

    public NameRunner(Class<?> c) throws InitializationError, Exception {
        super(c);
    }

    public void run(RunNotifier runNotifier) {
        runNotifier.addListener(new RunListenerImpl());
        super.run(runNotifier);
    }

    private class RunListenerImpl extends RunListener {
        public void testStarted(Description description) throws Exception {
            if (description.isTest())
                System.err.println("Testcase: " +
                                   description.getDisplayName());
        }
    }

}
