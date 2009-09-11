

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
public class GroupServerPanel extends ServerPanel {

    public GroupServerPanel(ServerModel.DataPacket initialData) {
        super(initialData, true);
    }

    @Override
    protected BarPanel newBarPanel(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients) {
        return new GroupBarPanel(numClients, loginHighWater, nodeHealth, clients);
    }

    private class GroupBarPanel extends BarPanel {

        private static final String RED_ROOM = "RED";
        private static final String BLUE_ROOM = "BLUE";
        private static final String GREEN_ROOM = "GREEN";
        private static final String ORANGE_ROOM = "ORANGE";

        public GroupBarPanel(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients) {
            super(numClients, loginHighWater, nodeHealth, clients);
        }

        @Override
        protected void paintBar(Graphics2D g2, Dimension boxSize) {

            int redClients = 0;
            int blueClients = 0;
            int greenClients = 0;
            int orangeClients = 0;
            int grayClients = 0;

            for (Client c : clients) {
                if (c.getGroup().equals(RED_ROOM)) {
                    redClients++;
                } else if (c.getGroup().equals(BLUE_ROOM)) {
                    blueClients++;
                } else if (c.getGroup().equals(GREEN_ROOM)) {
                    greenClients++;
                } else if (c.getGroup().equals(ORANGE_ROOM)) {
                    orangeClients++;
                } else {
                    grayClients++;
                }
            }

            Dimension graySize = new Dimension(boxSize.width, boxSize.height * numClients / maxDisplayValue);
            Dimension redSize = new Dimension(boxSize.width, boxSize.height * redClients / maxDisplayValue);
            Dimension blueSize = new Dimension(boxSize.width, boxSize.height * blueClients / maxDisplayValue);
            Dimension greenSize = new Dimension(boxSize.width, boxSize.height * greenClients / maxDisplayValue);
            Dimension orangeSize = new Dimension(boxSize.width, boxSize.height * orangeClients / maxDisplayValue);

            g2.setColor(Color.WHITE);
            g2.fill(new Rectangle(new Point(0, boxSize.height - graySize.height), graySize));

            g2.setColor(Color.RED);
            g2.fill(new Rectangle(new Point(0, boxSize.height - redSize.height), redSize));

            g2.setColor(Color.BLUE);
            g2.fill(new Rectangle(new Point(0, boxSize.height - redSize.height - blueSize.height), blueSize));

            g2.setColor(Color.GREEN);
            g2.fill(new Rectangle(new Point(0, boxSize.height - redSize.height - blueSize.height - greenSize.height), greenSize));

            g2.setColor(Color.ORANGE);
            g2.fill(new Rectangle(new Point(0, boxSize.height - redSize.height - blueSize.height - greenSize.height - orangeSize.height), orangeSize));
        }

    }
}
