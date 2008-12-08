/*
 * Copyright 2008 Sun Microsystems, Inc.
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

import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.management.ChannelServiceMXBean;
import com.sun.sgs.management.ClientSessionServiceMXBean;
import com.sun.sgs.management.ConfigMXBean;
import com.sun.sgs.management.DataServiceMXBean;
import com.sun.sgs.management.DataStoreStatsMXBean;
import com.sun.sgs.management.NodeInfo;
import com.sun.sgs.management.NodeMappingServiceMXBean;
import com.sun.sgs.management.NodesMXBean;
import com.sun.sgs.management.ProfileControllerMXBean;
import com.sun.sgs.management.TaskServiceMXBean;
import com.sun.sgs.management.WatchdogServiceMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Properties;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for management beans.
 */
@RunWith(NameRunner.class)
public class TestMBeans {
    private final static String APP_NAME = "TestMBeans";
    
    /** A test server node */
    private SgsTestNode serverNode;  
    /** The profile collector associated with the test server node */
    private ProfileCollector profileCollector;
    /** The system registry */
    private ComponentRegistry systemRegistry;
    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;
    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** JMX connection server */
    private static JMXConnectorServer cs;
    /** JMX connector */
    private static JMXConnector cc;
    /** MBean server connection */
    private static MBeanServerConnection mbsc;
    
    /** Any additional nodes, only used for selected tests */
    private SgsTestNode additionalNodes[];
    
    /** Test setup. */
    @BeforeClass
    public static void first() throws Exception {      
        // Set up MBean Server, making sure we force all operations
        // to go through RMI (simulating a remote connection).
        // We hope to catch errors like non-serializable objects in our MBeans.
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://");
        cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
        cs.start();
        cc = JMXConnectorFactory.connect(cs.getAddress());
        mbsc = cc.getMBeanServerConnection();
    }
    
    @Before
    public void setUp() throws Exception {
        Properties props = 
                SgsTestNode.getDefaultProperties(APP_NAME, null, null);
        serverNode = new SgsTestNode(APP_NAME, null, props);
        profileCollector = getCollector(serverNode);
        systemRegistry = serverNode.getSystemRegistry();
        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
    }
  
    /** Shut down the nodes and shut down JMX. */
    @After
    public void tearDown() throws Exception {
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
    
    @AfterClass
    public static void last() throws Exception {     
        if (cc != null) {
            cc.close();
        }
        cs.stop();
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
        return node.getSystemRegistry().getComponent(ProfileCollector.class);
    }

     ////////     The tests     /////////

    // For each MBean in our manager directory, make sure it has been
    // registered properly during system startup (can we find it in our
    // profile collector?), retrieve it through our remote connection,
    // and be sure we can create a proxy for it.
    //
    // Note that most management console applications would choose to create
    // proxies for the objects.  Internal Darkstar services would choose
    // to look up the object through the profile collector, to avoid the
    // reflection overhead of the proxy.
    @Test
    public void testNodesMXBean() throws Exception {
        ObjectName name = new ObjectName(NodesMXBean.NODES_MXBEAN_NAME);
        
        // Ensure that the object name has been registered during 
        // kernel startup.
        NodesMXBean bean = (NodesMXBean)
            profileCollector.getRegisteredMBean(NodesMXBean.NODES_MXBEAN_NAME);
        assertNotNull(bean);
        
        CompositeData[] nodesData = 
            (CompositeData[])  mbsc.getAttribute(name, "Nodes");
        
        assertEquals(1, nodesData.length);
        
        // Create the proxy for the object
        NodesMXBean proxy = (NodesMXBean)
            JMX.newMXBeanProxy(mbsc, name, NodesMXBean.class);
        NodeInfo[] nodes = proxy.getNodes();
        for (NodeInfo n : nodes) {
            System.out.println("found node: " + n);
            assertTrue(n.isLive());
        }
        
        assertEquals(1, bean.getNodes().length);
        assertEquals(1, nodes.length);
        
        // add a couple more nodes
        addNodes(null, 2);
        nodes = proxy.getNodes();
        assertEquals(3, nodes.length);
        
        for (NodeInfo n : nodes) {
            System.out.println("found node: " + n);
            assertTrue(n.isLive());
        }
    }
    
    @Test
    public void testConfigMXBean() throws Exception {
        ObjectName name = new ObjectName(ConfigMXBean.CONFIG_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        ConfigMXBean bean = 
            (ConfigMXBean) profileCollector.getRegisteredMBean(
                                            ConfigMXBean.CONFIG_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        String appListener = (String) mbsc.getAttribute(name, "AppListener");
        String appName = (String) mbsc.getAttribute(name, "AppName");
        String hostName = (String) mbsc.getAttribute(name, "HostName");
        int appPort = (Integer) mbsc.getAttribute(name, "AppPort");
        String appRoot = (String) mbsc.getAttribute(name, "AppRoot");
        int jmxPort = (Integer) mbsc.getAttribute(name, "JMXPort");
        String type = (String) mbsc.getAttribute(name, "NodeType");
        String serverHost = (String) mbsc.getAttribute(name, "ServerHostName");
        long timeout = (Long) mbsc.getAttribute(name, "TxnTimeout");
        
        System.out.println("This node's data:");
        System.out.println("  node type: " + type);
        System.out.println("  app listener: " + appListener);
        System.out.println("  app name: " + appName);
        System.out.println("  app root: " + appRoot);
        System.out.println("  txn timeout:" + timeout);
        
        System.out.println("  host name: " + hostName);
        System.out.println("  port: " + appPort);
        System.out.println("  jmx port: " + jmxPort);
        System.out.println("  server host:" + serverHost);
        
        // Create the proxy for the object
        ConfigMXBean proxy = (ConfigMXBean)
            JMX.newMXBeanProxy(mbsc, name, ConfigMXBean.class);
        assertEquals(appListener, proxy.getAppListener());
        assertEquals(appName, proxy.getAppName());
        assertEquals(hostName, proxy.getHostName());
        assertEquals(appPort, proxy.getAppPort());
        assertEquals(appRoot, proxy.getAppRoot());
        assertEquals(jmxPort, proxy.getJMXPort());
        assertEquals(type, proxy.getNodeType());
        assertEquals(serverHost, proxy.getServerHostName());
        assertEquals(timeout, proxy.getTxnTimeout());
        
        assertEquals(appListener, bean.getAppListener());
        assertEquals(appName, bean.getAppName());
        assertEquals(hostName, bean.getHostName());
        assertEquals(appPort, bean.getAppPort());
        assertEquals(appRoot, bean.getAppRoot());
        assertEquals(jmxPort, bean.getJMXPort());
        assertEquals(type, bean.getNodeType());
        assertEquals(serverHost, bean.getServerHostName());
        assertEquals(timeout, bean.getTxnTimeout());
    }
    
    @Test
    public void testDataStoreStatsMXBean() throws Exception {
        // Turn on profiling for the store
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + "DataStore");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = 
            new ObjectName(DataStoreStatsMXBean.DATA_STORE_STATS_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        DataStoreStatsMXBean bean = (DataStoreStatsMXBean) 
            profileCollector.getRegisteredMBean(
                          DataStoreStatsMXBean.DATA_STORE_STATS_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields, operations
        long createObject = (Long) mbsc.getAttribute(name, "CreateObjectCalls");
        long getBinding = (Long) mbsc.getAttribute(name, "GetBindingCalls");
        long getClassId = (Long) mbsc.getAttribute(name, "GetClassIdCalls");
        long getClassInfo = (Long) mbsc.getAttribute(name, "GetClassInfoCalls");
        long getObject = (Long) mbsc.getAttribute(name, "GetObjectCalls");
        long getObjectForUpdateCalls = 
                (Long) mbsc.getAttribute(name, "GetObjectForUpdateCalls");
        long markForUpdate = 
                (Long) mbsc.getAttribute(name, "MarkForUpdateCalls");
        long nextBoundName = 
                (Long) mbsc.getAttribute(name, "NextBoundNameCalls");
        long nextObjectId = (Long) mbsc.getAttribute(name, "NextObjectIdCalls");
        long removeBinding = 
                (Long) mbsc.getAttribute(name, "RemoveBindingCalls");
        long removeObject = (Long) mbsc.getAttribute(name, "RemoveObjectCalls");
        long setBinding = (Long) mbsc.getAttribute(name, "SetBindingCalls");
        long setObject = (Long) mbsc.getAttribute(name, "SetObjectCalls");
        long setObjects = (Long) mbsc.getAttribute(name, "SetObjectsCalls");
        
        // samples and counters
        double avgRead = (Double) mbsc.getAttribute(name, "AvgReadBytesSample");
        long minRead = (Long) mbsc.getAttribute(name, "MinReadBytesSample");
        long maxRead = (Long) mbsc.getAttribute(name, "MaxReadBytesSample");
        long readBytes = (Long) mbsc.getAttribute(name, "ReadBytesCount");
        long readObjs = (Long) mbsc.getAttribute(name, "ReadObjectsCount");
        
        double avgWritten = 
                (Double)mbsc.getAttribute(name, "AvgWrittenBytesSample");
        long minWritten = 
                (Long) mbsc.getAttribute(name, "MinWrittenBytesSample");
        long maxWritten = 
                (Long) mbsc.getAttribute(name, "MaxWrittenBytesSample");
        long writtenBytes = 
                (Long) mbsc.getAttribute(name, "WrittenBytesCount");
        long writtenObjs = 
                (Long) mbsc.getAttribute(name, "WrittenObjectsCount");
        
        // Create the proxy for the object
        DataStoreStatsMXBean proxy = (DataStoreStatsMXBean)
            JMX.newMXBeanProxy(mbsc, name, DataStoreStatsMXBean.class);
        assertTrue(createObject <= proxy.getCreateObjectCalls());
        assertTrue(getBinding <= proxy.getGetBindingCalls());
        assertTrue(getClassId <= proxy.getGetClassIdCalls());
        assertTrue(getClassInfo <= proxy.getGetClassInfoCalls());
        assertTrue(getObject <= proxy.getGetObjectCalls());
        assertTrue(getObjectForUpdateCalls <= proxy.getGetObjectForUpdateCalls());
        assertTrue(markForUpdate <= proxy.getMarkForUpdateCalls());
        assertTrue(nextBoundName <= proxy.getNextBoundNameCalls());
        assertTrue(nextObjectId <= proxy.getNextObjectIdCalls());
        assertTrue(removeBinding <= proxy.getRemoveBindingCalls());
        assertTrue(removeObject <= proxy.getRemoveObjectCalls());
        assertTrue(setBinding <= proxy.getSetBindingCalls());
        assertTrue(setObject <= proxy.getSetObjectCalls());
        assertTrue(setObjects <= proxy.getSetObjectsCalls());

        // Test one of the APIs by calling through the data service
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
                    ManagedObject dummy = new DummyManagedObject();
                    serverNode.getDataService().setBinding("dummy", dummy);
		}}, taskOwner);
        // Should certainly be greater number, not greater or equal
        assertTrue(createObject < proxy.getCreateObjectCalls());
        assertTrue(writtenBytes < proxy.getWrittenBytesCount());
        assertTrue(writtenObjs < proxy.getWrittenObjectsCount());
    }
    
    
    @Test
    public void testDataServiceMXBean() throws Exception {
        // Turn on profiling for the service
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + "DataService");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = 
            new ObjectName(DataServiceMXBean.DATA_SERVICE_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        DataServiceMXBean bean = (DataServiceMXBean) 
            profileCollector.getRegisteredMBean(
                          DataServiceMXBean.DATA_SERVICE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        long createRef = (Long) mbsc.getAttribute(name, "CreateReferenceCalls");
        long createRefForId = 
                (Long) mbsc.getAttribute(name, "CreateReferenceForIdCalls");
        long getBinding = (Long) mbsc.getAttribute(name, "GetBindingCalls");
        long getServiceBinding = 
                (Long) mbsc.getAttribute(name, "GetServiceBindingCalls");
        long markForUpdate = 
                (Long) mbsc.getAttribute(name, "MarkForUpdateCalls");
        long nextBoundName = 
                (Long) mbsc.getAttribute(name, "NextBoundNameCalls");
        long nextObjectId = (Long) mbsc.getAttribute(name, "NextObjectIdCalls");
        long nextServiceBoundName = 
                (Long) mbsc.getAttribute(name, "NextServiceBoundNameCalls");
        long removeBinding = 
                (Long) mbsc.getAttribute(name, "RemoveBindingCalls");
        long removeObject = (Long) mbsc.getAttribute(name, "RemoveObjectCalls");
        long removeServiceBinding = 
                (Long) mbsc.getAttribute(name, "RemoveServiceBindingCalls");
        long setBinding = (Long) mbsc.getAttribute(name, "SetBindingCalls");
        long setServiceBinding = 
                (Long) mbsc.getAttribute(name, "SetServiceBindingCalls");
        
        
        // Create the proxy for the object
        DataServiceMXBean proxy = (DataServiceMXBean)
            JMX.newMXBeanProxy(mbsc, name, DataServiceMXBean.class);
        
        // We might have had some service calls in between getting the
        // proxy and objects.
        assertTrue(createRef <= proxy.getCreateReferenceCalls());
        assertTrue(createRefForId <= proxy.getCreateReferenceForIdCalls());
        assertTrue(getBinding <= proxy.getGetBindingCalls());
        assertTrue(getServiceBinding <= proxy.getGetServiceBindingCalls());
        assertTrue(markForUpdate <= proxy.getMarkForUpdateCalls());
        assertTrue(nextBoundName <= proxy.getNextBoundNameCalls());
        assertTrue(nextObjectId <= proxy.getNextObjectIdCalls());
        assertTrue(nextServiceBoundName <= 
                    proxy.getNextServiceBoundNameCalls());
        assertTrue(removeBinding <= proxy.getRemoveBindingCalls());
        assertTrue(removeObject <= proxy.getRemoveObjectCalls());
        assertTrue(removeServiceBinding <= 
                    proxy.getRemoveServiceBindingCalls());
        assertTrue(setBinding <= proxy.getSetBindingCalls());
        assertTrue(setServiceBinding <= proxy.getSetServiceBindingCalls());
        

        assertTrue(createRef <= bean.getCreateReferenceCalls());
        assertTrue(createRefForId <= bean.getCreateReferenceForIdCalls());
        assertTrue(getBinding <= bean.getGetBindingCalls());
        assertTrue(getServiceBinding <= bean.getGetServiceBindingCalls());
        assertTrue(markForUpdate <= bean.getMarkForUpdateCalls());
        assertTrue(nextBoundName <= bean.getNextBoundNameCalls());
        assertTrue(nextObjectId <= bean.getNextObjectIdCalls());
        assertTrue(nextServiceBoundName <= bean.getNextServiceBoundNameCalls());
        assertTrue(removeBinding <= bean.getRemoveBindingCalls());
        assertTrue(removeObject <= bean.getRemoveObjectCalls());
        assertTrue(removeServiceBinding <= bean.getRemoveServiceBindingCalls());
        assertTrue(setBinding <= bean.getSetBindingCalls());
        assertTrue(setServiceBinding <= proxy.getSetServiceBindingCalls());
        
        // Test one of the APIs
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
                    ManagedObject dummy = new DummyManagedObject();
                    serverNode.getDataService().setBinding("dummy", dummy);
		}}, taskOwner);
        // Should certainly be greater number, not greater or equal
        assertTrue(setBinding < proxy.getSetBindingCalls());
        assertTrue(setBinding < bean.getSetBindingCalls());
    }
    
    @Test
    public void testWatchdogServiceMXBean() throws Exception {
        // Turn on profiling for the service
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + "WatchdogService");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = 
            new ObjectName(WatchdogServiceMXBean.WATCHDOG_SERVICE_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        WatchdogServiceMXBean bean = (WatchdogServiceMXBean) 
            profileCollector.getRegisteredMBean(
                          WatchdogServiceMXBean.WATCHDOG_SERVICE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        long addNodeListener = 
                (Long) mbsc.getAttribute(name, "AddNodeListenerCalls");
        long addRecoveryListener = 
                (Long) mbsc.getAttribute(name, "AddRecoveryListenerCalls");
        long getBackup = 
                (Long) mbsc.getAttribute(name, "GetBackupCalls");
        long getLocalNodeId = 
                (Long) mbsc.getAttribute(name, "GetLocalNodeIdCalls");
        long getNode = (Long) mbsc.getAttribute(name, "GetNodeCalls");
        long getNodes = (Long) mbsc.getAttribute(name, "GetNodesCalls");
        long isLocalNodeAlive = 
                (Long) mbsc.getAttribute(name, "IsLocalNodeAliveCalls");
        long isLocalNodeAliveNonTransactional = 
                (Long) mbsc.getAttribute(name, 
                                "IsLocalNodeAliveNonTransactionalCalls");
        
        // Create the proxy for the object
        WatchdogServiceMXBean proxy = (WatchdogServiceMXBean)
            JMX.newMXBeanProxy(mbsc, name, WatchdogServiceMXBean.class);
        
        // We might have had some service calls in between getting the
        // proxy and objects.
        assertTrue(addNodeListener <= proxy.getAddNodeListenerCalls());
        assertTrue(addRecoveryListener <= proxy.getAddRecoveryListenerCalls());
        assertTrue(getBackup <= proxy.getGetBackupCalls());
        assertTrue(getLocalNodeId <= proxy.getGetLocalNodeIdCalls());
        assertTrue(getNode <= proxy.getGetNodeCalls());
        assertTrue(getNodes <= proxy.getGetNodesCalls());
        assertTrue(isLocalNodeAlive <= proxy.getIsLocalNodeAliveCalls());
        assertTrue(isLocalNodeAliveNonTransactional <= 
                    proxy.getIsLocalNodeAliveNonTransactionalCalls());

        assertTrue(addNodeListener <= bean.getAddNodeListenerCalls());
        assertTrue(addRecoveryListener <= bean.getAddRecoveryListenerCalls());
        assertTrue(getBackup <= bean.getGetBackupCalls());
        assertTrue(getLocalNodeId <= bean.getGetLocalNodeIdCalls());
        assertTrue(getNode <= bean.getGetNodeCalls());
        assertTrue(getNodes <= bean.getGetNodesCalls());
        assertTrue(isLocalNodeAlive <= bean.getIsLocalNodeAliveCalls());
        assertTrue(isLocalNodeAliveNonTransactional <= 
                    bean.getIsLocalNodeAliveNonTransactionalCalls());
        
        // Test one of the APIs
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
                    serverNode.getWatchdogService().getLocalNodeId();
		}}, taskOwner);
        // Should certainly be greater number, not greater or equal
        long newValue = proxy.getGetLocalNodeIdCalls();
        assertTrue(getLocalNodeId < newValue);
        assertTrue(getLocalNodeId < bean.getGetLocalNodeIdCalls());
        // and try outside a transaction
        serverNode.getWatchdogService().getLocalNodeId();
        assertTrue(newValue < proxy.getGetLocalNodeIdCalls());
        assertTrue(newValue < bean.getGetLocalNodeIdCalls());
    }
    
    @Test
    public void tesNodeMapServiceMXBean() throws Exception {
        // Turn on profiling for the service
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                "NodeMappingService");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = new ObjectName(
                NodeMappingServiceMXBean.NODEMAP_SERVICE_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        NodeMappingServiceMXBean bean = (NodeMappingServiceMXBean) 
            profileCollector.getRegisteredMBean(
                          NodeMappingServiceMXBean.NODEMAP_SERVICE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        long addNodeMapListener = 
                (Long) mbsc.getAttribute(name, "AddNodeMappingListenerCalls");
        long assignNode = (Long) mbsc.getAttribute(name, "AssignNodeCalls");
        long getIds = (Long) mbsc.getAttribute(name, "GetIdentitiesCalls");
        long getNode = (Long) mbsc.getAttribute(name, "GetNodeCalls");
        long setStatus = (Long) mbsc.getAttribute(name, "SetStatusCalls");
        
        // Create the proxy for the object
        NodeMappingServiceMXBean proxy = (NodeMappingServiceMXBean)
            JMX.newMXBeanProxy(mbsc, name, NodeMappingServiceMXBean.class);
        
        assertTrue(addNodeMapListener <= 
                        proxy.getAddNodeMappingListenerCalls());
        assertTrue(assignNode <= proxy.getAssignNodeCalls());
        assertTrue(getIds <= proxy.getGetIdentitiesCalls());
        assertTrue(getNode <= proxy.getGetNodeCalls());
        assertTrue(setStatus <= proxy.getSetStatusCalls());
        
        // Test an API
        serverNode.getNodeMappingService().
                assignNode(NodeMappingService.class, new IdentityImpl("first"));
        assertTrue(assignNode < proxy.getAssignNodeCalls());
        assertTrue(assignNode < bean.getAssignNodeCalls());     
    }
    
    @Test
    public void testTaskServiceMXBean() throws Exception {
        // Turn on profiling for the service
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                "TaskService");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = 
                new ObjectName(TaskServiceMXBean.TASK_SERVICE_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        TaskServiceMXBean bean = (TaskServiceMXBean) 
            profileCollector.getRegisteredMBean(
                          TaskServiceMXBean.TASK_SERVICE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        long delayed = 
                (Long) mbsc.getAttribute(name, "ScheduleDelayedTaskCalls");
        long nondurable = 
                (Long) mbsc.getAttribute(name, "ScheduleNonDurableTaskCalls");
        long nondurableDelayed = 
                (Long) mbsc.getAttribute(name, 
                                         "ScheduleNonDurableTaskDelayedCalls");
        long periodic = 
                (Long) mbsc.getAttribute(name, "SchedulePeriodicTaskCalls");
        long task = (Long) mbsc.getAttribute(name, "ScheduleTaskCalls");
        
        // Create the proxy for the object
        TaskServiceMXBean proxy = (TaskServiceMXBean)
            JMX.newMXBeanProxy(mbsc, name, TaskServiceMXBean.class);
        
        assertTrue(delayed <= proxy.getScheduleDelayedTaskCalls());
        assertTrue(nondurable <= proxy.getScheduleNonDurableTaskCalls());
        assertTrue(nondurableDelayed <= 
                proxy.getScheduleNonDurableTaskDelayedCalls());
        assertTrue(periodic <= proxy.getSchedulePeriodicTaskCalls());
        assertTrue(task <= proxy.getScheduleTaskCalls());
        
        // Test an API
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    serverNode.getTaskService().scheduleNonDurableTask(
                        new TestAbstractKernelRunnable() {
                            public void run() { }},
                        false);
                }
            }, taskOwner);
        
        assertTrue(nondurable < proxy.getScheduleNonDurableTaskCalls());
        assertTrue(nondurable < bean.getScheduleNonDurableTaskCalls());
    }
 
    @Test
    public void testSessionServiceMXBean() throws Exception {
        // Turn on profiling for the service
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                "ClientSessionService");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = new ObjectName(
                ClientSessionServiceMXBean.SESSION_SERVICE_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        ClientSessionServiceMXBean bean = (ClientSessionServiceMXBean) 
            profileCollector.getRegisteredMBean(
                      ClientSessionServiceMXBean.SESSION_SERVICE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        long reg = (Long) mbsc.getAttribute(name, 
                                "RegisterSessionDisconnectListenerCalls");
        long send = (Long) mbsc.getAttribute(name, 
                                "SendProtocolMessageNonTransactionalCalls");
        
        // Create the proxy for the object
        ClientSessionServiceMXBean proxy = (ClientSessionServiceMXBean)
            JMX.newMXBeanProxy(mbsc, name, ClientSessionServiceMXBean.class);
        
        assertTrue(reg <= proxy.getRegisterSessionDisconnectListenerCalls());
        assertTrue(send <= proxy.getSendProtocolMessageNonTransactionalCalls());
        
        serverNode.getClientSessionService().
            sendProtocolMessageNonTransactional(new BigInteger("555"),
                ByteBuffer.wrap("Message".getBytes()), Delivery.RELIABLE);
        assertTrue(send < proxy.getSendProtocolMessageNonTransactionalCalls());
        assertTrue(send < bean.getSendProtocolMessageNonTransactionalCalls());
    }
     
    @Test
    public void testChannelServiceMXBean() throws Exception {
        // Turn on profiling for the service
        ProfileConsumer cons = 
            getCollector(serverNode).getConsumer(
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                "ChannelService");
        cons.setProfileLevel(ProfileLevel.MAX);
        
        ObjectName name = new ObjectName(
                ChannelServiceMXBean.CHANNEL_SERVICE_MXBEAN_NAME);
        
        // Ensure the object was registered at startup
        ChannelServiceMXBean bean = (ChannelServiceMXBean) 
            profileCollector.getRegisteredMBean(
                      ChannelServiceMXBean.CHANNEL_SERVICE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Get individual fields
        long create = (Long) mbsc.getAttribute(name, "CreateChannelCalls");
        long get = (Long) mbsc.getAttribute(name,  "GetChannelCalls");
        
        // Create the proxy for the object
        ChannelServiceMXBean proxy = (ChannelServiceMXBean)
            JMX.newMXBeanProxy(mbsc, name, ChannelServiceMXBean.class);
        
        assertTrue(create <= proxy.getCreateChannelCalls());
        assertTrue(get <= proxy.getGetChannelCalls());
        
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    try {
                        serverNode.getChannelService().getChannel("foo");
                    } catch (NameNotBoundException nnb) {
                        System.out.println("Got expected exception " + nnb);
                    }
                }
            }, taskOwner);
       
        assertTrue(get < proxy.getGetChannelCalls());
        assertTrue(get < bean.getGetChannelCalls());
    }
    
    @Test
    public void testProfileControllerMXBean() throws Exception {
        // Ensure the object was registered at startup
        ProfileControllerMXBean bean = 
            (ProfileControllerMXBean) profileCollector.getRegisteredMBean(
                                ProfileControllerMXBean.PROFILE_MXBEAN_NAME);
        assertNotNull(bean);
        
        // Create a proxy
        ProfileControllerMXBean proxy = (ProfileControllerMXBean)
            JMX.newMXBeanProxy(mbsc, 
                new ObjectName(ProfileControllerMXBean.PROFILE_MXBEAN_NAME), 
                ProfileControllerMXBean.class);
        String[] consumers = proxy.getProfileConsumers();
        for (String con : consumers) {
            System.out.println("Found consumer " + con);
        }
        
        // Default profile level is min
        assertEquals(ProfileLevel.MIN, proxy.getDefaultProfileLevel());
        // Can set the default profile level
        proxy.setDefaultProfileLevel(ProfileLevel.MEDIUM);
        assertEquals(ProfileLevel.MEDIUM, proxy.getDefaultProfileLevel());
        
        String consName = 
                ProfileCollectorImpl.CORE_CONSUMER_PREFIX + "DataService";
        ProfileLevel level = proxy.getConsumerLevel(consName);
        assertEquals(ProfileLevel.MIN, level);
        
        DataServiceMXBean dataProxy = (DataServiceMXBean)
            JMX.newMXBeanProxy(mbsc, 
                new ObjectName(DataServiceMXBean.DATA_SERVICE_MXBEAN_NAME), 
                DataServiceMXBean.class);
        
        // Test that consumer level can be changed
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
                    ManagedObject dummy = new DummyManagedObject();
                    serverNode.getDataService().setBinding("dummy", dummy);
		}}, taskOwner);
        assertEquals(0, dataProxy.getSetBindingCalls());
        
        proxy.setConsumerLevel(consName, ProfileLevel.MAX);
        assertEquals(ProfileLevel.MAX, proxy.getConsumerLevel(consName));
        
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
                    ManagedObject dummy = new DummyManagedObject();
                    serverNode.getDataService().setBinding("dummy", dummy);
		}}, taskOwner);
        assertTrue(dataProxy.getSetBindingCalls() > 0);
    }
}
