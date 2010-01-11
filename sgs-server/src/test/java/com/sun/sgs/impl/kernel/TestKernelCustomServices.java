/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.impl.kernel.StandardProperties.ServiceNodeTypes;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.security.auth.login.LoginException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test booting the {@code Kernel} with various configurations of custom
 * services, managers, and node types.
 */
@RunWith(FilteredNameRunner.class)
@IntegrationTest
public class TestKernelCustomServices {

    /** Set of services that have been started up during a test. */
    private static Set<String> runningServices = new HashSet<String>();
    private static Set<String> runningManagers = new HashSet<String>();

    /** List of authenticators that have been created. */
    private static List<String> availableAuthenticators =
                                new ArrayList<String>();
    /** List of profile listeners that have been created. */
    private static List<String> availableProfileListeners =
                                new ArrayList<String>();

    /** The main test node. */
    private SgsTestNode serverNode;
    /** An additional node for tests needing an app node */
    private SgsTestNode additionalNode;

    @Before
    public void setup() {

    }

    @After
    public void tearDown() throws Exception {
        if (additionalNode != null) {
            additionalNode.shutdown(true);
        }
        if (serverNode != null) {
            serverNode.shutdown(true);
        }

        runningServices.clear();
        runningManagers.clear();
        availableAuthenticators.clear();
        availableProfileListeners.clear();

        Thread.sleep(100);
    }

    /** Utility methods. */

    private Properties getSingleNodeProperties() throws Exception {
        return SgsTestNode.getDefaultProperties(
                "TestKernelCustomServices", null, null);
    }

    private Properties getCoreNodeProperties() throws Exception {
        Properties props = SgsTestNode.getDefaultProperties(
                "TestKernelCustomServices", null, null);
        props.setProperty(StandardProperties.NODE_TYPE,
                          NodeType.coreServerNode.toString());
        return props;
    }

    private Properties getAppNodeProperties() throws Exception {
        return SgsTestNode.getDefaultProperties(
                "TestKernelCustomServices", serverNode, null);
    }

    private void startCoreNode(Properties props) throws Exception {
        serverNode = new SgsTestNode("TestKernelCustomServices", null, props);
    }

    private void startAppNode(Properties props) throws Exception {
        additionalNode = new SgsTestNode(serverNode, null, props);
    }

    private void setServiceProperties(Properties props,
                                      String services,
                                      String managers,
                                      String nodeTypes) {
        if (services != null)
            props.setProperty(StandardProperties.SERVICES, services);
        if (managers != null)
            props.setProperty(StandardProperties.MANAGERS, managers);
        if (nodeTypes != null)
            props.setProperty(StandardProperties.SERVICE_NODE_TYPES, nodeTypes);
    }

    private void setExtServiceProperties(Properties props,
                                         String services,
                                         String managers,
                                         String nodeTypes) {
        if (services != null)
            props.setProperty(BootProperties.EXTENSION_SERVICES_PROPERTY,
                              services);
        if (managers != null)
            props.setProperty(BootProperties.EXTENSION_MANAGERS_PROPERTY,
                              managers);
        if (nodeTypes != null)
            props.setProperty(BootProperties.EXTENSION_SERVICE_NODE_TYPES_PROPERTY,
                              nodeTypes);
    }

    /** The tests. */

    @Test
    public void noServices() throws Exception {
        Properties props = getSingleNodeProperties();
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
    }

    @Test(expected=Exception.class)
    public void invalidService() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             InvalidService.class.getName(),
                             InvalidManager.class.getName(),
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
    }

    @Test
    public void serviceOnCoreNodeWithCoreNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.CORE.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnSingleNodeWithCoreNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.CORE.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnAppNodeWithCoreNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.CORE.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnCoreNodeWithAppNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.APP.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnSingleNodeWithAppNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.APP.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnAppNodeWithAppNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.APP.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnCoreNodeWithSingleNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnSingleNodeWithSingleNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnAppNodeWithSingleNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnCoreNodeWithSingleOrCoreNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE_OR_CORE.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnSingleNodeWithSingleOrCoreNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE_OR_CORE.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnAppNodeWithSingleOrCoreNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE_OR_CORE.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnCoreNodeWithSingleOrAppNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE_OR_APP.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnSingleNodeWithSingleOrAppNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE_OR_APP.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnAppNodeWithSingleOrAppNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.SINGLE_OR_APP.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnCoreNodeWithCoreOrAppNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.CORE_OR_APP.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnSingleNodeWithCoreOrAppNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.CORE_OR_APP.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.isEmpty());
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test
    public void serviceOnAppNodeWithCoreOrAppNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.CORE_OR_APP.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnCoreNodeWithAllNodeType() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnSingleNodeWithAllNodeType() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void serviceOnAppNodeWithAllNodeType() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.ALL.toString());
        startAppNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void singleServiceNoManager() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             null,
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test(expected=Exception.class)
    public void multiServicesNoManager() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             null,
                             ServiceNodeTypes.ALL.toString() + ":" +
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
    }

    @Test
    public void multiServicesSomeManagers() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             Manager1.class.getName() + ":",
                             ServiceNodeTypes.ALL.toString() + ":" +
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertFalse(runningManagers.contains(Manager2.class.getName()));
    }

    @Test
    public void multiServicesAllManagers() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             Manager1.class.getName() + ":" +
                             Manager2.class.getName(),
                             ServiceNodeTypes.ALL.toString() + ":" +
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager2.class.getName()));
    }

    @Test
    public void multiServicesNoNodeTypesOnCoreNode() throws Exception {
        Properties props = getCoreNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             ":",
                             null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
    }

    @Test
    public void multiServicesNoNodeTypesOnSingleNode() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             ":",
                             null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
    }

    @Test
    public void multiServicesNoNodeTypesOnAppNode() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             ":",
                             null);
        startAppNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
    }

    @Test(expected=Exception.class)
    public void multiServicesMismatchedNodeTypes() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             ":",
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
    }

    @Test
    public void multiServicesDifferentNodeTypes() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName() + ":" +
                             Service3.class.getName(),
                             Manager1.class.getName() + ":" +
                             Manager2.class.getName() + ":" +
                             Manager3.class.getName(),
                             ServiceNodeTypes.SINGLE.toString() + ":" +
                             ServiceNodeTypes.CORE_OR_APP.toString() + ":" +
                             ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertFalse(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningServices.contains(Service3.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertFalse(runningManagers.contains(Manager2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager3.class.getName()));
    }
    
    @Test
    public void singleExtService() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName(),
                                Manager1.class.getName(),
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void singleExtServiceNoManager() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName(),
                                null,
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningManagers.isEmpty());
    }

    @Test(expected=Exception.class)
    public void multiExtServicesNoManager() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                null,
                                ServiceNodeTypes.ALL.toString() + ":" +
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
    }

    @Test
    public void multiExtServicesSomeManagers() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                Manager1.class.getName() + ":",
                                ServiceNodeTypes.ALL.toString() + ":" +
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertFalse(runningManagers.contains(Manager2.class.getName()));
    }

    @Test
    public void multiExtServicesAllManagers() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                Manager1.class.getName() + ":" +
                                Manager2.class.getName(),
                                ServiceNodeTypes.ALL.toString() + ":" +
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager2.class.getName()));
    }

    @Test
    public void multiExtServicesNoNodeTypesOnCoreNode() throws Exception {
        Properties props = getCoreNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                ":",
                                null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
    }

    @Test
    public void multiExtServicesNoNodeTypesOnSingleNode() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                ":",
                                null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
    }

    @Test
    public void multiExtServicesNoNodeTypesOnAppNode() throws Exception {
        startCoreNode(null);

        Properties props = getAppNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                ":",
                                null);
        startAppNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
    }

    @Test(expected=Exception.class)
    public void multiExtServicesMismatchedNodeTypes() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                ":",
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
    }

    @Test
    public void multiExtServicesDifferentNodeTypes() throws Exception {
        Properties props = getSingleNodeProperties();
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName() + ":" +
                                Service3.class.getName(),
                                Manager1.class.getName() + ":" +
                                Manager2.class.getName() + ":" +
                                Manager3.class.getName(),
                                ServiceNodeTypes.SINGLE.toString() + ":" +
                                ServiceNodeTypes.CORE_OR_APP.toString() + ":" +
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertFalse(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningServices.contains(Service3.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertFalse(runningManagers.contains(Manager2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager3.class.getName()));
    }

    @Test
    public void combinedSingleService() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName(),
                             Manager1.class.getName(),
                             ServiceNodeTypes.ALL.toString());
        setExtServiceProperties(props,
                                Service2.class.getName(),
                                Manager2.class.getName(),
                                ServiceNodeTypes.ALL.toString());
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager2.class.getName()));
    }

    @Test
    public void combinedMultiServices() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             Manager1.class.getName() + ":" +
                             Manager2.class.getName(),
                             null);
        setExtServiceProperties(props,
                                Service3.class.getName() + ":" +
                                Service4.class.getName(),
                                Manager3.class.getName() + ":" +
                                Manager4.class.getName(),
                                null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningServices.contains(Service3.class.getName()));
        Assert.assertTrue(runningServices.contains(Service4.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager2.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager3.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager4.class.getName()));
    }

    @Test
    public void combinedMultiServicesSingleAndMultiManagers() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             Service1.class.getName() + ":" +
                             Service2.class.getName(),
                             Manager1.class.getName() + ":" +
                             Manager2.class.getName(),
                             null);
        setExtServiceProperties(props,
                                Service3.class.getName(),
                                null,
                                null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningServices.contains(Service3.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager2.class.getName()));
        Assert.assertFalse(runningManagers.contains(Manager3.class.getName()));
    }

    @Test
    public void testExtServicesStartBeforeServices() throws Exception {
        Properties props = getSingleNodeProperties();
        setServiceProperties(props,
                             DependentService.class.getName(),
                             null,
                             null);
        setExtServiceProperties(props,
                                Service1.class.getName() + ":" +
                                Service2.class.getName(),
                                Manager1.class.getName() + ":",
                                null);
        startCoreNode(props);
        Assert.assertTrue(runningServices.contains(Service1.class.getName()));
        Assert.assertTrue(runningServices.contains(Service2.class.getName()));
        Assert.assertTrue(runningServices.contains(DependentService.class.getName()));
        Assert.assertTrue(runningManagers.contains(Manager1.class.getName()));
    }

    @Test
    public void noAuthenticators() throws Exception {
        Properties props = getSingleNodeProperties();
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.isEmpty());
    }

    @Test
    public void invalidAuthenticator() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(StandardProperties.AUTHENTICATORS,
                          InvalidAuthenticator.class.getName());
        try {
            startCoreNode(props);
            Assert.fail("Startup should fail due to invalid authenticator");
        } catch (Exception e) {
            Assert.assertTrue(availableAuthenticators.isEmpty());
        }
    }

    @Test
    public void singleAuthenticator() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(StandardProperties.AUTHENTICATORS,
                          Authenticator1.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator1.class.getName()));
    }

    @Test
    public void multiAuthenticators() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(StandardProperties.AUTHENTICATORS,
                          Authenticator1.class.getName() + ":" +
                          Authenticator2.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator1.class.getName()));
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator2.class.getName()));
    }

    @Test
    public void singleExtAuthenticator() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(BootProperties.EXTENSION_AUTHENTICATORS_PROPERTY,
                          Authenticator1.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator1.class.getName()));
    }

    @Test
    public void multiExtAuthenticators() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(BootProperties.EXTENSION_AUTHENTICATORS_PROPERTY,
                          Authenticator1.class.getName() + ":" +
                          Authenticator2.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator1.class.getName()));
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator2.class.getName()));
    }

    @Test
    public void combinedSingleAuthenticators() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(StandardProperties.AUTHENTICATORS,
                          Authenticator1.class.getName());
        props.setProperty(BootProperties.EXTENSION_AUTHENTICATORS_PROPERTY,
                          Authenticator2.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator1.class.getName()));
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator2.class.getName()));
        Assert.assertTrue(
                availableAuthenticators.indexOf(
                Authenticator2.class.getName()) <
                availableAuthenticators.indexOf(
                Authenticator1.class.getName()));
    }

    @Test
    public void combinedMultiAuthenticators() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(StandardProperties.AUTHENTICATORS,
                          Authenticator1.class.getName() + ":" +
                          Authenticator2.class.getName());
        props.setProperty(BootProperties.EXTENSION_AUTHENTICATORS_PROPERTY,
                          Authenticator3.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator1.class.getName()));
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator2.class.getName()));
        Assert.assertTrue(availableAuthenticators.contains(
                Authenticator3.class.getName()));
        Assert.assertTrue(
                availableAuthenticators.indexOf(
                Authenticator3.class.getName()) <
                availableAuthenticators.indexOf(
                Authenticator1.class.getName()));
        Assert.assertTrue(
                availableAuthenticators.indexOf(
                Authenticator1.class.getName()) <
                availableAuthenticators.indexOf(
                Authenticator2.class.getName()));
    }

    @Test
    public void noProfileListeners() throws Exception {
        Properties props = getSingleNodeProperties();
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.isEmpty());
    }

    @Test
    public void invalidProfileListener() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(Kernel.PROFILE_LISTENERS,
                          InvalidProfileListener.class.getName());

        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.isEmpty());
    }

    @Test
    public void singleProfileListener() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(Kernel.PROFILE_LISTENERS,
                          ProfileListener1.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener1.class.getName()));
    }

    @Test
    public void multiProfileListeners() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(Kernel.PROFILE_LISTENERS,
                          ProfileListener1.class.getName() + ":" +
                          ProfileListener2.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener1.class.getName()));
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener2.class.getName()));
    }

    @Test
    public void singleExtProfileListener() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(BootProperties.EXTENSION_PROFILE_LISTENERS_PROPERTY,
                          ProfileListener1.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener1.class.getName()));
    }

    @Test
    public void multiExtProfileListeners() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(BootProperties.EXTENSION_PROFILE_LISTENERS_PROPERTY,
                          ProfileListener1.class.getName() + ":" +
                          ProfileListener2.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener1.class.getName()));
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener2.class.getName()));
    }

    @Test
    public void combinedSingleProfileListeners() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(Kernel.PROFILE_LISTENERS,
                          ProfileListener1.class.getName());
        props.setProperty(BootProperties.EXTENSION_PROFILE_LISTENERS_PROPERTY,
                          ProfileListener2.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener1.class.getName()));
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener2.class.getName()));
        Assert.assertTrue(
                availableProfileListeners.indexOf(
                ProfileListener2.class.getName()) <
                availableProfileListeners.indexOf(
                ProfileListener1.class.getName()));
    }

    @Test
    public void combinedMultiProfileListeners() throws Exception {
        Properties props = getSingleNodeProperties();
        props.setProperty(Kernel.PROFILE_LISTENERS,
                          ProfileListener1.class.getName() + ":" +
                          ProfileListener2.class.getName());
        props.setProperty(BootProperties.EXTENSION_PROFILE_LISTENERS_PROPERTY,
                          ProfileListener3.class.getName());
        startCoreNode(props);
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener1.class.getName()));
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener2.class.getName()));
        Assert.assertTrue(availableProfileListeners.contains(
                ProfileListener3.class.getName()));
        Assert.assertTrue(
                availableProfileListeners.indexOf(
                ProfileListener3.class.getName()) <
                availableProfileListeners.indexOf(
                ProfileListener1.class.getName()));
        Assert.assertTrue(
                availableProfileListeners.indexOf(
                ProfileListener1.class.getName()) <
                availableProfileListeners.indexOf(
                ProfileListener2.class.getName()));
    }


    /** Dummy Services and Managers for use in the tests. */

    public static abstract class TestAbstractService implements Service {
        public String getName() {
            return this.getClass().getName();
        }
        public void ready() throws Exception {
            runningServices.add(this.getClass().getName());
        }
        public void shutdown() {
            runningServices.remove(this.getClass().getName());
        }
    }

    public static abstract class TestAbstractManager {
        public TestAbstractManager() {
            runningManagers.add(this.getClass().getName());
        }
    }

    public static class Service1 extends TestAbstractService {
        public Service1(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager1 extends TestAbstractManager {
        public Manager1(Service1 s) { super(); }
    }

    public static class Service2 extends TestAbstractService {
        public Service2(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager2 extends TestAbstractManager {
        public Manager2(Service2 s) { super(); }
    }

    public static class Service3 extends TestAbstractService {
        public Service3(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager3 extends TestAbstractManager {
        public Manager3(Service3 s) { super(); }
    }

    public static class Service4 extends TestAbstractService {
        public Service4(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager4 extends TestAbstractManager {
        public Manager4(Service4 s) { super(); }
    }

    public static class DependentService extends TestAbstractService {
        public DependentService(Properties p,
                                ComponentRegistry c,
                                TransactionProxy t) {
            // assert that the other services were started before this one
            t.getService(Service1.class);
            t.getService(Service2.class);
        }
    }

    public static class InvalidService extends TestAbstractService {
        public InvalidService() {}
    }

    public static class InvalidManager extends TestAbstractManager {
        public InvalidManager() {}
    }

    public static abstract class AbstractAuthenticator
            implements IdentityAuthenticator {

        public AbstractAuthenticator(Properties p) {
            availableAuthenticators.add(this.getClass().getName());
        }

        public Identity authenticateIdentity(IdentityCredentials credentials)
                throws LoginException {
            return null;
        }

        public String[] getSupportedCredentialTypes() {
            return new String[0];
        }

    }

    public static class Authenticator1 extends AbstractAuthenticator {
        public Authenticator1(Properties p) { super(p); }
    }

    public static class Authenticator2 extends AbstractAuthenticator {
        public Authenticator2(Properties p) { super(p); }
    }

    public static class Authenticator3 extends AbstractAuthenticator {
        public Authenticator3(Properties p) { super(p); }
    }

    public static class InvalidAuthenticator extends AbstractAuthenticator {
        public InvalidAuthenticator() { super(null); }
    }

    public static abstract class AbstractProfileListener
            implements ProfileListener {

        public AbstractProfileListener(Properties p,
                                       Identity i,
                                       ComponentRegistry c) {
            availableProfileListeners.add(this.getClass().getName());
        }

        public void propertyChange(PropertyChangeEvent event) {

        }

        public void report(ProfileReport profileReport) {

        }

        public void shutdown() {

        }
    }

    public static class ProfileListener1 extends AbstractProfileListener {
        public ProfileListener1(Properties p, Identity i, ComponentRegistry c) {
            super(p, i, c);
        }
    }

    public static class ProfileListener2 extends AbstractProfileListener {
        public ProfileListener2(Properties p, Identity i, ComponentRegistry c) {
            super(p, i, c);
        }
    }

    public static class ProfileListener3 extends AbstractProfileListener {
        public ProfileListener3(Properties p, Identity i, ComponentRegistry c) {
            super(p, i, c);
        }
    }

    public static class InvalidProfileListener extends AbstractProfileListener {
        public InvalidProfileListener() { super(null, null, null); }
    }

}
