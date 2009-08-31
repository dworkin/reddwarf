

package com.sun.sgs.tools.monitor;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 */
public class Monitor {

    public static void main(String args[]) throws Exception {
        /*JMXServiceURL url = new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://localhost:12345/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);

        ServerModel model = new ServerModel(jmxc.getMBeanServerConnection());

        System.out.println("Core Node?        : " + model.isCoreNode());
        System.out.println("Status            : " + model.getStatusInfo());
        System.out.println("App Nodes         : " + model.getAppNodes().length);
        System.out.println("Node Health       : " + model.getNodeHealth());
        System.out.println("Connected Clients : " + model.getNumClients());
        System.out.println("Maximum Clients   : " + model.getLoginHighWater());*/

        new ManagementConsole();
    }

}
