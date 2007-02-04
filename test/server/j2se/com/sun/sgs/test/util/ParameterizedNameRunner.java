
package com.sun.sgs.test.util;

import org.junit.internal.runners.InitializationError; 

import org.junit.runner.Description;

import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import org.junit.runners.Parameterized;


/**
 * This is a custom implementation of JUunit4's <code>TestClassRunner</code>
 * that adds support for reporting the name of each test when it starts. It
 * subclasses off of <code>Parameterized</code> so that you can have name
 * reporting in a parameterized test.
 */
public class ParameterizedNameRunner extends Parameterized {

    private static String testName;

    public ParameterizedNameRunner(Class<?> c) throws InitializationError,
                                                      Exception {
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
