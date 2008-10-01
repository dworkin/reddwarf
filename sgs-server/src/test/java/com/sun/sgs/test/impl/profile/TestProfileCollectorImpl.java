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
import com.sun.sgs.service.ProfileService;
import com.sun.sgs.test.util.SgsTestNode;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Properties;
import junit.framework.TestCase;

public class TestProfileCollectorImpl extends TestCase {
    private final static String APP_NAME = "TestProfileCollectorImpl";
    private SgsTestNode serverNode;
    /** Any additional nodes, only used for selected tests */
    private SgsTestNode additionalNodes[];
    private ProfileCollector profileCollector;
    
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
                if (node != null) {
                    node.shutdown(false);
                }
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
        ProfileService service = 
            node.getProxy().getService(ProfileService.class);
        return service.getProfileCollector();
    }
        ////////     The tests     /////////
    public void testDefaultKernel() {
        // The profile collector must not be null and the level must be "min"
        assertNotNull(profileCollector);
        assertSame(ProfileLevel.MIN, profileCollector.getDefaultProfileLevel());
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
        assertSame(ProfileLevel.MIN, collector.getDefaultProfileLevel());

    }

    public void testKernelBadProfileLevel() throws Exception {
        // The kernel won't start if a bad profile level is provided.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "JUNKJUNK");
        try {
            addNodes(serviceProps, 1);
            fail("Excpected kernel to not start up");
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            assertEquals("Expected IllegalArgumentException",
                         IllegalArgumentException.class.getName(), 
                         t.getClass().getName());
        }
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
        assertSame(ProfileLevel.MEDIUM, collector.getDefaultProfileLevel());
    }
    
    // need tests to check that listeners added with boolean false (addListener)
    // are not shut down, and tests for consumer profile levels being 
    // independent and changable.
    
    public void testLocale() throws Exception {
        Locale.setDefault(Locale.JAPANESE);
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "medium");
        addNodes(serviceProps, 1);
        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MEDIUM, collector.getDefaultProfileLevel());
    }
}
