/*
 * Copyright (c) 2009, Sun Microsystems, Inc.
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

package com.sun.sgs.tools.test;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

/**
 * A test filter that filters tests based on the {@link IntegrationTest}
 * annotation.  This filter uses the {@link TestPhase#isLongPhase()} method
 * to determine whether it is currently the long or short testing phase
 * (integration or unit respectively).
 * <p>
 * Additionally, if the system property test.method is specified, this
 * filter will only execute the single test specified by that method (if
 * it passes the IntegrationTest filter).
 */
public class TestFilter extends Filter {
    
    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");
    
    /**
     * Describes this filter.
     * 
     * @return a short description of the filter
     */
    public String describe() {
        return "filters tests based on the IntegrationTest annotation";
    }

    /**
     * Returns whether or not the test given by the {@code description}
     * argument passes the filter.  Tests pass the filter according to the
     * following conditions:
     * <ul>
     * <li>If the current test phase is {@link TestPhase#LONG},
     * a test passes the filter if it has the {@code IntegrationTest}
     * annotation <em>and</em> the annotation specifies either the
     * {@link TestPhase#LONG} or {@link TestPhase#BOTH} phase.</li>
     * <li>If the current test phase is {@link TestPhase#SHORT},
     * a test passes the filter if it does not have the
     * {@code IntegrationTest} annotation <em>or</em> it has the
     * {@code IntegrationTest} annotation and specifies either the
     * {@link TestPhase#SHORT} or {@link TestPhase#BOTH} phase.</li>
     * <li>If the system property test.method is set, only a test with
     * the name given by this property will pass the filter.</li>
     * </ul>
     * 
     * @param description the test to run through the filter
     * @return {@code true} if the test passed the filter
     */
    public boolean shouldRun(Description description) {
        
        if (description.isTest()) {
            
            if (testMethod == null || 
                description.getDisplayName().startsWith(testMethod)) {
                
                IntegrationTest annotation = 
                        description.getAnnotation(IntegrationTest.class);

                if (TestPhase.isLongPhase()) {
                    if (annotation == null) {
                        return false;
                    } else if (annotation.value() == TestPhase.LONG ||
                            annotation.value() == TestPhase.BOTH) {
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    if (annotation == null) {
                        return true;
                    } else if (annotation.value() == TestPhase.SHORT ||
                            annotation.value() == TestPhase.BOTH) {
                        return true;
                    } else {
                        return false;
                    }
                }
            } else {
                return false;
            }
        }
        
        return true;
    }

}
