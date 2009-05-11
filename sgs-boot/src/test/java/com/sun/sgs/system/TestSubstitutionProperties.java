/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.system;

import java.util.Properties;
import org.junit.Test;
import org.junit.Assert;

/**
 * Test the SubstitutionProperties class
 */
public class TestSubstitutionProperties {
    
    @Test
    public void testCreationWithStandardProperties() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "value1");
        testProps.setProperty("name2", "value2");
        testProps.setProperty("name3", "value3");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        System.out.println(p.size());
        System.out.println(p.propertyNames().nextElement());
        System.out.println(p.getProperty("name1"));
        
        Assert.assertEquals(p.getProperty("name1"), "value1");
        Assert.assertEquals(p.getProperty("name2"), "value2");
        Assert.assertEquals(p.getProperty("name3"), "value3");
    }
    
    @Test
    public void testCreationWithSimpleInterpolations() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "value1");
        testProps.setProperty("name2", "${name1}");
        testProps.setProperty("name3", "${name1}${name2}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        
        Assert.assertEquals(p.getProperty("name1"), "value1");
        Assert.assertEquals(p.getProperty("name2"), "value1");
        Assert.assertEquals(p.getProperty("name3"), "value1value1");
    }
    
    @Test
    public void testCreationWithComplexInterpolations() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "value1");
        testProps.setProperty("name2", "${name1}/test/${name3}");
        testProps.setProperty("name3", "${name1}/value2");
        testProps.setProperty("name4", "${name2}/${name1}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        
        Assert.assertEquals(p.getProperty("name1"), "value1");
        Assert.assertEquals(p.getProperty("name2"), "value1/test/value1/value2");
        Assert.assertEquals(p.getProperty("name3"), "value1/value2");
        Assert.assertEquals(p.getProperty("name4"), "value1/test/value1/value2/value1");
    }
    
    @Test(expected=IllegalStateException.class)
    public void testCreationWithSimpleLoop() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "${name2}");
        testProps.setProperty("name2", "${name1}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
    }
    
    @Test(expected=IllegalStateException.class)
    public void testCreationWithComplexLoop() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "${name2}");
        testProps.setProperty("name2", "${name3}");
        testProps.setProperty("name3", "${name1}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
    }
    
    @Test
    public void testCreationWithEmptyVariable() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "${}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        
        Assert.assertEquals(p.getProperty("name1"), "");
    }
    
    @Test
    public void testCreationWithNonExistantVariable() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "${name2}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        
        Assert.assertEquals(p.getProperty("name1"), "");
        Assert.assertNull(p.getProperty("name2"));
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testCreationWithInvalidVariableFormat() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "${name2");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
    }
    
    @Test
    public void testCreationWithFunnyButValidVariableFormat() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name2", "value");
        testProps.setProperty("name1", "${name2}}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        
        Assert.assertEquals(p.getProperty("name1"), "value}");
    }
    
    @Test
    public void testCreationWithFunnyButValidVariableFormat2() {
        Properties testProps = new Properties();
        
        testProps.setProperty("${name2", "value");
        testProps.setProperty("name1", "${${name2}");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        
        Assert.assertEquals(p.getProperty("name1"), "value");
        Assert.assertEquals(p.getProperty("${name2"), "value");
    }
    
    @Test
    public void testSetPropertyWithInterpolation() {
        Properties testProps = new Properties();
        
        testProps.setProperty("name1", "value1");
        testProps.setProperty("name2", "${name1}/test/${name3}");
        testProps.setProperty("name3", "${name1}/value2");
        
        SubstitutionProperties p = new SubstitutionProperties(testProps);
        p.setProperty("name4", "${name2}/${name1}");
        
        Assert.assertEquals(p.getProperty("name1"), "value1");
        Assert.assertEquals(p.getProperty("name2"), "value1/test/value1/value2");
        Assert.assertEquals(p.getProperty("name3"), "value1/value2");
        Assert.assertEquals(p.getProperty("name4"), "value1/test/value1/value2/value1");
    }

}
