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

/**
 * The {@code TestPhase} enumeration type specifies the possible
 * phases for tests marked with the {@link IntegrationTest} annotation.
 */
public enum TestPhase {

    /**
     * Tests marked with the {@code SHORT} phase should only be executed
     * during the unit test phase of testing.
     */
    SHORT,
    /**
     * Tests marked with the {@code LONG} phase should only be executed
     * during the integration test phase of testing.
     */
    LONG,
    /**
     * Tests marked with the {@code BOTH} phase should be executed
     * during both the unit test and integration test phases of testing.
     */
    BOTH;
    
    
    /**
     * This method will return true if and only if the current testing
     * phase is the unit test phase.  This is established with the system
     * property {@code test.phase}.  A value of {@code long} for this
     * property means the current testing phase is the integration test
     * phase.  Any other value means the current testing phase is the
     * unit test phase.
     * 
     * @return {@code true} if it is the unit test phase
     */
    public static boolean isShortPhase() {
        return !isLongPhase();
    }
    
    /**
     * This method will return true if and only if the current testing
     * phase is the integration test phase.  This is established with the system
     * property {@code test.phase}.  A value of {@code long} for this
     * property means the current testing phase is the integration test
     * phase.  Any other value means the current testing phase is the
     * unit test phase.
     * 
     * @return {@code true} if it is the unit test phase
     * @see TestProfile#
     */
    public static boolean isLongPhase() {
        return "long".equals(System.getProperty("test.phase"));
    }
    
    /**
     * Given an {@code IntegrationTest} annotation, this method determines
     * whether or not the class or method attached to this annotation should
     * be run.  A test should be run according to the following conditions:
     * <ul>
     * <li>If the current test phase is {@link TestPhase#LONG},
     * a test should be run if it has the {@code IntegrationTest}
     * annotation <em>and</em> the annotation specifies either the
     * {@link TestPhase#LONG} or {@link TestPhase#BOTH} phase.</li>
     * <li>If the current test phase is {@link TestPhase#SHORT},
     * a test should be run if it does not have the
     * {@code IntegrationTest} annotation <em>or</em> it has the
     * {@code IntegrationTest} annotation and specifies either the
     * {@link TestPhase#SHORT} or {@link TestPhase#BOTH} phase.</li>
     * </ul>
     * 
     * @param annotation the {@code IntegrationTest} to check
     * @return {@code true} if the class or method attached to the annotation
     *         should be run, {@code false} otherwise.
     */
    static boolean shouldRun(IntegrationTest annotation) {
        if (annotation == null || annotation.value() == TestPhase.SHORT) {
            return !TestPhase.isLongPhase();
        } else if (annotation.value() == TestPhase.LONG) {
            return TestPhase.isLongPhase();
        } else {
            return true;
        }
    }
}
