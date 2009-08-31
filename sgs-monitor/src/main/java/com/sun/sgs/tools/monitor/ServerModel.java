
package com.sun.sgs.tools.monitor;

import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.ChannelServiceMXBean;
import com.sun.sgs.management.Client;
import com.sun.sgs.management.ClientSessionServiceMXBean;
import com.sun.sgs.management.ConfigMXBean;
import com.sun.sgs.management.GroupCoordinatorMXBean;
import com.sun.sgs.management.NodeInfo;
import com.sun.sgs.management.NodesMXBean;
import com.sun.sgs.management.WatchdogServiceMXBean;
import com.sun.sgs.service.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.management.JMX;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanServerConnection;

/**
 * Provides access to JMX information from a running Project Darkstar server.
 */
public class ServerModel {

    private final NodeType nodeType;

    private final ChannelServiceMXBean channelBean;
    private final ClientSessionServiceMXBean sessionBean;
    private final ConfigMXBean configBean;
    private final GroupCoordinatorMXBean groupBean;
    private final NodesMXBean nodesBean;
    private final WatchdogServiceMXBean watchdogBean;

    public ServerModel(MBeanServerConnection server)  {
        this.configBean = JMX.newMXBeanProxy(
                server,
                getObjectName(ConfigMXBean.MXBEAN_NAME),
                ConfigMXBean.class);
        this.watchdogBean = JMX.newMXBeanProxy(
                server,
                getObjectName(WatchdogServiceMXBean.MXBEAN_NAME),
                WatchdogServiceMXBean.class);

        // configure the node info bean if this is a core node
        this.nodeType = configBean.getNodeType();
        switch (nodeType) {
            case singleNode:
            case coreServerNode:
                this.nodesBean = JMX.newMXBeanProxy(
                        server,
                        getObjectName(NodesMXBean.MXBEAN_NAME),
                        NodesMXBean.class);
                break;
            default:
                this.nodesBean = null;
        }

        // configure the channel, session, and group beans if an app node
        switch (nodeType) {
            case singleNode:
            case appNode:
                this.channelBean = JMX.newMXBeanProxy(
                        server,
                        getObjectName(ChannelServiceMXBean.MXBEAN_NAME),
                        ChannelServiceMXBean.class);
                this.sessionBean = JMX.newMXBeanProxy(
                        server,
                        getObjectName(ClientSessionServiceMXBean.MXBEAN_NAME),
                        ClientSessionServiceMXBean.class);
                this.groupBean = JMX.newMXBeanProxy(
                        server,
                        getObjectName(GroupCoordinatorMXBean.MXBEAN_NAME),
                        GroupCoordinatorMXBean.class);
                break;
            default:
                this.channelBean = null;
                this.sessionBean = null;
                this.groupBean = null;
        }
    }

    private ObjectName getObjectName(String name) {
        try {
            return ObjectName.getInstance(name);
        } catch (MalformedObjectNameException e) {
            //swallow this exception for now
            return null;
        }
    }

    /**
     * Returns whether or not the node represented by this model is a core node.
     *
     * @return {@code true} if this model represents a core node
     */
    boolean isCoreNode() {
        switch (nodeType) {
            case singleNode:
            case coreServerNode:
                return true;
            default:
                return false;
        }
    }

    NodeInfo getStatusInfo() {
        return watchdogBean.getStatusInfo();
    }

    /**
     * Returns a sorted list of {@code NodeInfo} objects representing the app
     * nodes in the system.
     *
     * @return app nodes in the system represented by this core node
     */
    List<NodeInfo> getAppNodes() {
        List<NodeInfo> appNodes = new ArrayList<NodeInfo>();
        if (nodeType.equals(NodeType.coreServerNode)) {
            NodeInfo thisNode = getStatusInfo();
            NodeInfo[] allNodes = nodesBean.getNodes();

            for (NodeInfo node : allNodes) {
                if(node.getId() != thisNode.getId()) {
                    appNodes.add(node);
                }
            }
            Collections.sort(appNodes, new Comparator<NodeInfo>() {
                public int compare(NodeInfo left, NodeInfo right) {
                    if (left.getId() < right.getId()) {
                        return -1;
                    } else if (left.getId() == right.getId()) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
        }

        return appNodes;
    }

    int getNumClients() {
        return sessionBean == null ? 0 : sessionBean.getNumClients();
    }

    int getLoginHighWater() {
        return sessionBean == null ? 0 : sessionBean.getLoginHighWater();
    }

    void setLoginHighWater(int value) {
        if (sessionBean != null) {
            sessionBean.setLoginHighWater(value);
        }
    }

    Node.Health getNodeHealth() {
        return watchdogBean.getNodeHealth();
    }

    Client[] getAllClients() {
        return channelBean == null ? null : channelBean.getClients();
    }
    
    public DataPacket getSnapshot() {
        return new DataPacket(this.nodeType,
                this.getStatusInfo(),
                this.getNumClients(),
                this.getLoginHighWater(),
                this.getNodeHealth(),
                this.getAllClients());
    }

    public static class DataPacket {
        public final NodeType nodeType;
        public final NodeInfo nodeInfo;
        public final int numClients;
        public final int loginHighWater;
        public final Node.Health nodeHealth;
        public final Client[] allClients;

        private DataPacket(NodeType nodeType,
                           NodeInfo nodeInfo,
                           int numClients,
                           int loginHighWater,
                           Node.Health nodeHealth,
                           Client[] allClients) {
            this.nodeType = nodeType;
            this.nodeInfo = nodeInfo;
            this.numClients = numClients;
            this.loginHighWater = loginHighWater;
            this.nodeHealth = nodeHealth;
            this.allClients = allClients;
        }
    }

}
