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
import org.junit.Test;
import org.junit.Assert;

/**
 * Test the TestFilter class
 */
public class TestTestFilter {
    
    @Test
    public void testShortPhaseNoAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseNoAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseNoAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShortPhaseNoAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseShortAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseShortAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseShortAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShortPhaseShortAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseLongAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShortPhaseLongAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseLongAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShortPhaseLongAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseBothAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseBothAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testShortPhaseBothAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testShortPhaseBothAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(false);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    
    
    @Test
    public void testLongPhaseNoAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testLongPhaseNoAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testLongPhaseNoAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseNoAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(NoAnnotationClass.class);
        Description test = Util.createDescription(NoAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseShortAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testLongPhaseShortAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testLongPhaseShortAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseShortAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(ShortAnnotationClass.class);
        Description test = Util.createDescription(ShortAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseLongAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseLongAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testLongPhaseLongAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseLongAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(LongAnnotationClass.class);
        Description test = Util.createDescription(LongAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseBothAnnotationClassNoAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, null);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseBothAnnotationClassShortAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, TestPhase.SHORT);
        
        boolean result = filter.shouldRun(test);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testLongPhaseBothAnnotationClassLongAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, TestPhase.LONG);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testLongPhaseBothAnnotationClassBothAnnotationMethod()
            throws Exception {
        Util.setLongPhase(true);
        TestFilter filter = new TestFilter(BothAnnotationClass.class);
        Description test = Util.createDescription(BothAnnotationClass.class, TestPhase.BOTH);
        
        boolean result = filter.shouldRun(test);
        Assert.assertTrue(result);
    }
    
    private class BaseClass {
        @Test
        public void noAnnotationMethod() {}
        
        @IntegrationTest(TestPhase.SHORT)
        @Test
        public void shortAnnotationMethod() {}
        
        @IntegrationTest(TestPhase.LONG)
        @Test
        public void longAnnotationMethod() {}
        
        @IntegrationTest(TestPhase.BOTH)
        @Test
        public void bothAnnotationMethod() {}
    }
    
    private class NoAnnotationClass extends BaseClass {}
    
    @IntegrationTest(TestPhase.SHORT)
    private class ShortAnnotationClass extends BaseClass {}
    
    @IntegrationTest(TestPhase.LONG)
    private class LongAnnotationClass extends BaseClass {}
    
    @IntegrationTest(TestPhase.BOTH)
    private class BothAnnotationClass extends BaseClass {}

}
