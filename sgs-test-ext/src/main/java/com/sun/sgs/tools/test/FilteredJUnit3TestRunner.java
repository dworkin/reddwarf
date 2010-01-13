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

import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.notification.RunNotifier;

/**
 * This is a custom implementation of JUnit's {@code JUnit38ClassRunner}
 * that adds support for filtering entire classes of tests based on
 * the {@link IntegrationTest} annotation.  This class is introduced only
 * for backwards compatibility with old JUnit3 tests.  Filtering at the
 * test method level with the {@code IntegrationTest} annotation is not
 * supported and neither is filtering using the "test.method" system
 * property.  It is strongly encouraged that any JUnit3 tests be ported
 * to use JUnit4 and either the {@link FilteredNameRunner} or 
 * {@link ParameterizedFilteredNameRunner} instead.
 */
public class FilteredJUnit3TestRunner extends JUnit38ClassRunner {
    
    private boolean empty = false;
    
    /**
     * Constructs a new {@code FilteredJUnit3TestRunner} for the
     * specified class.
     * 
     * @param c the class to run with this runner
     */
    public FilteredJUnit3TestRunner(Class<?> c) {
        super(c);
        
        IntegrationTest annotation = 
                c.getAnnotation(IntegrationTest.class);
        if (!TestPhase.shouldRun(annotation)) { 
            empty = true;
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * Skips running the tests if they have been filtered out by a 
     * class level {@link IntegrationTest} annotation.
     */
    public void run(RunNotifier runNotifier) {
        if (empty) {
            return;
        }
        
        super.run(runNotifier);
    }
    

}
