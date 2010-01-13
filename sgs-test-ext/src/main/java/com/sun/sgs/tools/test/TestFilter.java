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
import org.junit.runner.manipulation.Filter;

/**
 * A test filter that filters tests based on the {@link IntegrationTest}
 * annotation.  This filter uses the {@link TestPhase#isLongPhase()} method
 * to determine whether it is currently the long or short testing phase
 * (integration or unit respectively).
 * <p>
 * Additionally, if the system property test.method is specified, this
 * filter will only execute the single test specified by that method.
 */
public class TestFilter extends Filter {
    
    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");
    
    private final boolean defaultShouldRun;
    
    /**
     * Constructs a test filter to filter tests in the given class.
     * 
     * @param testClass the class to filter
     */
    public TestFilter(Class<?> testClass) {
        IntegrationTest annotation = 
                testClass.getAnnotation(IntegrationTest.class);
        this.defaultShouldRun = TestPhase.shouldRun(annotation);
    }
    
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
     * <ol>
     * <li>If the system property test.method is set, only a test with
     * the name given by this property will pass the filter.  Otherwise, all
     * tests make it to the next step.</li>
     * <li>If there is an {@link IntegrationTest} annotation on the test,
     * it will pass the filter if the
     * {@link TestPhase#shouldRun(com.sun.sgs.tools.test.IntegrationTest)}
     * method returns {@code true}.</li>
     * <li>If there is no {@code IntegrationTest} annotation on the test,
     * it defers to the enclosing class.  It will behave according to the
     * return value of the 
     * {@link TestPhase#shouldRun(com.sun.sgs.tools.test.IntegrationTest)}
     * method on the {@code IntegrationTest} annotation of the enclosing
     * class.</li>
     * </ol>
     * 
     * @param description the test to run through the filter
     * @return {@code true} if the test passed the filter
     * @see TestPhase#shouldRun(com.sun.sgs.tools.test.IntegrationTest) 
     */
    public boolean shouldRun(Description description) {
        if (description.isTest()) {

            if (testMethod == null ||
                    description.getDisplayName().startsWith(testMethod)) {

                IntegrationTest annotation =
                        description.getAnnotation(IntegrationTest.class);
                if (annotation == null) {
                    return defaultShouldRun;
                } else {
                    return TestPhase.shouldRun(annotation);
                }
            } else {
                return false;
            }
        }

        return true;
    }

}
