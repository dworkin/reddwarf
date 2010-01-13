/*
 * Copyright (c) 2009-2010, Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.tools.test;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.BlockJUnit4ClassRunner;

/**
 * This is a custom implementation of JUnit4's {@code BlockJUnit4ClassRunner}
 * that adds support for reporting the name of each test when it starts.
 * Additionally, it automatically filters which tests to run based on
 * a {@link TestFilter}.
 */
public class FilteredNameRunner extends BlockJUnit4ClassRunner {
    
    private boolean empty = false;

    /**
     * Constructs a {@code FilteredNameRunner} for running tests in the
     * given class.
     * 
     * @param c the class to run tests with this runner
     * @throws java.lang.Exception if an error occurs initializing the runner
     */
    public FilteredNameRunner(Class<?> c) throws Exception {
        super(c);
        
        //enable the filter
        try {
            filter(new TestFilter(c));
        } catch (NoTestsRemainException e) {
            empty = true;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * Skips running the tests if they have all been filtered out by a 
     * {@link IntegrationTest} annotations.
     */
    public void run(RunNotifier runNotifier) {
        if (empty) {
            return;
        }

        runNotifier.addListener(new RunListenerImpl());
        super.run(runNotifier);
    }

    /**
     * A custom {@code RunListener} that prints out the name of each
     * test to standard error when it is started.
     */
    private static class RunListenerImpl extends RunListener {
        public void testStarted(Description description) throws Exception {
            if (description.isTest()) {
                System.err.println("Testcase: " +
                                   description.getDisplayName());
            }
        }
    }

}
