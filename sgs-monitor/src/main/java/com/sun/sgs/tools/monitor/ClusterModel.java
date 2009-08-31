
package com.sun.sgs.tools.monitor;

import com.sun.sgs.management.NodeInfo;
import java.util.List;
import java.util.ArrayList;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 */
public class ClusterModel {

    // list of active connections to darkstar nodes
    private List<JMXConnector> activeConnections;
    private List<ServerModel> activeModels;

    private boolean connected = false;

    public ClusterModel() {
        activeConnections = new ArrayList<JMXConnector>();
        activeModels = new ArrayList<ServerModel>();
    }


    public boolean isConnected() {
        return connected;
    }

    public void connect(String coreServer) throws Exception {
        try {
            JMXServiceURL url = new JMXServiceURL(
                    "service:jmx:rmi:///jndi/rmi://" + coreServer + "/jmxrmi");
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
            ServerModel core = new ServerModel(jmxc.getMBeanServerConnection());
            activeConnections.add(jmxc);
            activeModels.add(core);
            if (!core.isCoreNode()) {
                throw new IllegalStateException("Server is not a core node!");
            }


            for (NodeInfo app : core.getAppNodes()) {
                JMXServiceURL u = new JMXServiceURL(
                        "service:jmx:rmi:///jndi/rmi://" +
                        app.getHost() + ":" + app.getJmxPort() +
                        "/jmxrmi");
                JMXConnector appJmxc = JMXConnectorFactory.connect(u, null);
                activeConnections.add(appJmxc);
                activeModels.add(new ServerModel(
                        appJmxc.getMBeanServerConnection()));
            }
        } catch (Exception catchAll) {
            disconnect();
            throw catchAll;
        }

        connected = true;
    }

    public void disconnect() {

        for (JMXConnector c : activeConnections) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }

        activeModels.clear();
        activeConnections.clear();
        connected = false;
    }

    public List<ServerModel.DataPacket> getInitialSnapshot() {
        List<ServerModel.DataPacket> packets = 
                new ArrayList<ServerModel.DataPacket>(activeModels.size());
        for (ServerModel model : activeModels) {
            packets.add(model.getSnapshot());
        }

        return packets;
    }

    public ServerModel getModel(int index) {
        return activeModels.get(index);
    }

}
