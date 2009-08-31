
package com.sun.sgs.tools.monitor;

import com.sun.sgs.management.Client;
import com.sun.sgs.service.Node;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

/**
 *
 */
public class HealthServerPanel extends ServerPanel {

    public HealthServerPanel(ServerModel.DataPacket initialData) {
        super(initialData);
    }

    @Override
    protected BarPanel newBarPanel(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients) {
        return new HealthBarPanel(numClients, loginHighWater, nodeHealth, clients);
    }

    private class HealthBarPanel extends BarPanel {

        public HealthBarPanel(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients) {
            super(numClients, loginHighWater, nodeHealth, clients);
        }

        @Override
        protected void paintBar(Graphics2D g2, Dimension boxSize) {
            Dimension barSize = new Dimension(boxSize.width, boxSize.height * numClients / maxDisplayValue);
            switch (this.nodeHealth) {
                case GREEN:
                    g2.setColor(Color.GREEN);
                    break;
                case YELLOW:
                    g2.setColor(Color.YELLOW);
                    break;
                case ORANGE:
                    g2.setColor(Color.ORANGE);
                    break;
                case RED:
                    g2.setColor(Color.RED);
                    break;
            }
            g2.fill(new Rectangle(new Point(0, boxSize.height - barSize.height), barSize));
        }

    }

}
