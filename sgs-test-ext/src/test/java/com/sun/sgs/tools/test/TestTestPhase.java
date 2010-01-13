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

import org.junit.Test;
import org.junit.Assert;

/**
 * Test the TestPhase enum
 */
public class TestTestPhase {
    
    @Test
    public void testShouldRunShortPhaseNoAnnotation() throws Exception {
        Util.setLongPhase(false);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(null));
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShouldRunShortPhaseShortAnnotation() throws Exception {
        Util.setLongPhase(false);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(TestPhase.SHORT));
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShouldRunShortPhaseLongAnnotation() throws Exception {
        Util.setLongPhase(false);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(TestPhase.LONG));
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShouldRunShortPhaseBothAnnotation() throws Exception {
        Util.setLongPhase(false);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(TestPhase.BOTH));
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShouldRunLongPhaseNoAnnotation() throws Exception {
        Util.setLongPhase(true);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(null));
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShouldRunLongPhaseShortAnnotation() throws Exception {
        Util.setLongPhase(true);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(TestPhase.SHORT));
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShouldRunLongPhaseLongAnnotation() throws Exception {
        Util.setLongPhase(true);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(TestPhase.LONG));
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShouldRunLongPhaseBothAnnotation() throws Exception {
        Util.setLongPhase(true);
        boolean result = TestPhase.shouldRun(Util.createAnnotation(TestPhase.BOTH));
        Assert.assertTrue(result);
    }

}
