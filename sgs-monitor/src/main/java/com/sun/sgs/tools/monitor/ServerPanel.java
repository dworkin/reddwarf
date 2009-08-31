
package com.sun.sgs.tools.monitor;

import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.Client;
import com.sun.sgs.service.Node;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.SwingPropertyChangeSupport;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 */
public abstract class ServerPanel extends JPanel implements PropertyChangeListener,
                                                            ChangeListener {

    protected static int maxDisplayValue = 0;
    protected static SwingPropertyChangeSupport maxDisplayValueSupport = new SwingPropertyChangeSupport(ServerPanel.class, true);

    private JLabel nodeLabel;
    private JLabel hostLabel;

    private BarPanel barPanel;
    private JLabel centerLabel;
    private JSlider highWaterSlider;
    private int loginHighWater;
    private PropertyChangeSupport highWaterChangeSupport = new PropertyChangeSupport(this.loginHighWater);


    public ServerPanel(ServerModel.DataPacket initialData) {
        super();
        this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.setLayout(new BorderLayout(5, 5));

        if (initialData.nodeType != NodeType.coreServerNode) {
            ServerPanel.setMaxDisplayValue(initialData.loginHighWater * 10 / 9);

            barPanel = newBarPanel(initialData.numClients,
                                   initialData.loginHighWater,
                                   initialData.nodeHealth,
                                   initialData.allClients);
            barPanel.setLayout(new BorderLayout());
            centerLabel = new JLabel(String.valueOf(initialData.numClients),
                                     JLabel.CENTER);
            barPanel.add(centerLabel, BorderLayout.CENTER);
            highWaterSlider = new JSlider(JSlider.VERTICAL, 0, maxDisplayValue, initialData.loginHighWater);
            highWaterSlider.addChangeListener(this);
            this.loginHighWater = initialData.loginHighWater;

            this.add(barPanel, BorderLayout.CENTER);
            this.add(highWaterSlider, BorderLayout.WEST);
            maxDisplayValueSupport.addPropertyChangeListener(this);
        } else {
            centerLabel = new JLabel("Core Node", JLabel.CENTER);
            centerLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
            this.add(centerLabel, BorderLayout.CENTER);
        }

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridLayout(2, 1));
        nodeLabel = new JLabel("Node ID : " + initialData.nodeInfo.getId(), JLabel.CENTER);
        nodeLabel.setFont(nodeLabel.getFont().deriveFont(Font.BOLD));
        hostLabel = new JLabel(initialData.nodeInfo.getHost() + ":" + initialData.nodeInfo.getJmxPort(), JLabel.CENTER);
        infoPanel.add(nodeLabel);
        infoPanel.add(hostLabel);

        this.add(infoPanel, BorderLayout.SOUTH);
    }

    public void update(ServerModel.DataPacket data) {
        if (data == null) {
            barPanel.setValues(0, 0, Node.Health.RED, null);
            centerLabel.setText("0");

            barPanel.setEnabled(false);
            centerLabel.setEnabled(false);
            highWaterSlider.setEnabled(false);
            nodeLabel.setEnabled(false);
            hostLabel.setEnabled(false);

        } else {
            if (data.nodeType != NodeType.coreServerNode) {
                barPanel.setValues(data.numClients, data.loginHighWater, data.nodeHealth, data.allClients);
                centerLabel.setText(String.valueOf(data.numClients));
            }
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        highWaterChangeSupport.addPropertyChangeListener(listener);
    }

    public void stateChanged(ChangeEvent e) {
        this.highWaterChangeSupport.firePropertyChange("loginHighWater", this.loginHighWater, highWaterSlider.getValue());
        this.loginHighWater = highWaterSlider.getValue();
    }

    public void propertyChange(PropertyChangeEvent e) {
        highWaterSlider.setMaximum(ServerPanel.getMaxDisplayValue());
        barPanel.repaint();
    }

    private static int getMaxDisplayValue() {
        return maxDisplayValue;
    }

    private static void setMaxDisplayValue(int newValue) {
        if (newValue > maxDisplayValue) {
            int oldValue = maxDisplayValue;
            maxDisplayValue = newValue;
            maxDisplayValueSupport.firePropertyChange(
                    new PropertyChangeEvent(ServerPanel.class,
                                            "maxDisplayValue",
                                            oldValue, newValue));
        }
    }

    protected abstract BarPanel newBarPanel(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients);

    protected abstract static class BarPanel extends JComponent {

        protected static final Stroke dashes = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5}, 0f);
        
        protected int numClients;
        protected int loginHighWater;
        protected Node.Health nodeHealth;
        protected Client[] clients;

        public BarPanel(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients) {
            setValues(numClients, loginHighWater, nodeHealth, clients);
        }

        void setValues(int numClients, int loginHighWater, Node.Health nodeHealth, Client[] clients) {
            this.numClients = numClients;
            this.loginHighWater = loginHighWater;
            this.nodeHealth = nodeHealth;
            this.clients = clients;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Dimension componentSize = this.getSize();
            if (maxDisplayValue == 0) {
                return;
            }

            Dimension boxSize = new Dimension(componentSize.width - 2, componentSize.height - 1);
            Dimension highWaterMark = new Dimension(boxSize.width, 0);
            int highWaterHeight = boxSize.height - boxSize.height * loginHighWater / maxDisplayValue;

            paintBar(g2, boxSize);

            Stroke stroke = g2.getStroke();
            g2.setColor(Color.LIGHT_GRAY);
            g2.setStroke(dashes);
            g2.draw(new Rectangle(new Point(0, highWaterHeight), highWaterMark));

            g2.setColor(Color.BLACK);
            g2.setStroke(stroke);
            g2.draw(new Rectangle(new Point(0, 0), boxSize));
        }

        protected abstract void paintBar(Graphics2D g2, Dimension boxSize);
    }

}
