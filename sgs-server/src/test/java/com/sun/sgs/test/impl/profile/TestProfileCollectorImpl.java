/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.profile;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import java.lang.reflect.Field;
import java.util.Properties;
import junit.framework.TestCase;

public class TestProfileCollectorImpl extends TestCase {
    private final static String APP_NAME = "TestProfileCollectorImpl";
    private SgsTestNode serverNode;
    /** Any additional nodes, only used for selected tests */
    private SgsTestNode additionalNodes[];
    private ProfileCollector profileCollector;
    
    private Field profileCollectorField = 
            UtilReflection.getField(
                com.sun.sgs.impl.profile.ProfileRegistrarImpl.class, 
                "profileCollector");
    
    /** Constructs a test instance. */
    public TestProfileCollectorImpl(String name) throws Exception {
        super(name);
    }
    
    /** Test setup. */
    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());
        setUp(null);
    }

    protected void setUp(Properties props) throws Exception {
        serverNode = new SgsTestNode(APP_NAME, null, props);
        profileCollector = getCollector(serverNode);
        
    }
  
    /** Shut down the nodes. */
    protected void tearDown() throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(true);
    }
    
        /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param props properties for node creation, or {@code null} if default
     *     properties should be used
     * @parm num the number of nodes to add
     */
    private void addNodes(Properties props, int num) throws Exception {
        // Create the other nodes
        additionalNodes = new SgsTestNode[num];

        for (int i = 0; i < num; i++) {
            SgsTestNode node = new SgsTestNode(serverNode, null, props); 
            additionalNodes[i] = node;
        }
    }
    
    /** Returns the profile collector for a given node */
    private ProfileCollector getCollector(SgsTestNode node) throws Exception {
        ProfileRegistrar registrar = 
            node.getSystemRegistry().getComponent(
                com.sun.sgs.profile.ProfileRegistrar.class);
        return (ProfileCollector) profileCollectorField.get(registrar);
    }
        ////////     The tests     /////////
    public void testDefaultKernel() {
        // The profile collector must not be null and the level must be "off"
        assertNotNull(profileCollector);
        assertSame(ProfileLevel.MIN, profileCollector.getGlobalProfileLevel());
    }

    public void testKernelNoProfile() throws Exception {
        // Even if the user specifies no profiling at startup, the collector
        // must not be null.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "MIN");
        addNodes(serviceProps, 1);

        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MIN, collector.getGlobalProfileLevel());

    }

    public void testKernelBadProfileLevel() throws Exception {
        // Even if the user gives a nonsense profile level, the collector must
        // not be null.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "JUNKJUNK");
        addNodes(serviceProps, 1);
        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MIN, collector.getGlobalProfileLevel());
    }

    public void testKernelLowerCaseLevel() throws Exception {
        // The profiling level is case insensitive.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "medium");
        addNodes(serviceProps, 1);
        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MEDIUM, collector.getGlobalProfileLevel());
    }
    
    // setProfileLevel tests
    public void testEnableProfile() {
        profileCollector.setGlobalProfileLevel(ProfileLevel.MAX);
        assertSame(ProfileLevel.MAX, profileCollector.getGlobalProfileLevel());
        // Test the listener list has not changed, maybe through reflection?
        
        // TBD  How will we instantiate a new listener?  Needs access to
        //   the kernel component registry.  New method in the kernel?
        
        // Need to find a way to ensure profiling is being recorded;  will
        // also need a test to show that it is (mostly) stopped if we turn
        // off profiling.
    }
    
    public void testSetGlobalLevel() {
        ProfileRegistrar registrar = 
            serverNode.getSystemRegistry().getComponent(
                com.sun.sgs.profile.ProfileRegistrar.class);
        ProfileConsumer pc = registrar.registerProfileProducer("unique");
        assertEquals(ProfileLevel.MIN, 
                     profileCollector.getGlobalProfileLevel());
        assertEquals(pc.getProfileLevel(), 
                     profileCollector.getGlobalProfileLevel());
        
        pc.setProfileLevel(ProfileLevel.MEDIUM);
        ProfileLevel level = pc.getProfileLevel();
        assertEquals(ProfileLevel.MEDIUM, level);
        assertFalse(level.equals(profileCollector.getGlobalProfileLevel())); 
        // Setting the global level overrides the local level
        profileCollector.setGlobalProfileLevel(ProfileLevel.MAX);
        assertEquals(pc.getProfileLevel(), 
                     profileCollector.getGlobalProfileLevel());
    }
}
