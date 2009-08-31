
package com.sun.sgs.tools.monitor;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

/**
 *
 */
public class ManagementConsole implements ActionListener, ChangeListener {

    private final static MouseAdapter mouseAdapter = new MouseAdapter() {};
    private final static Cursor WAIT = new Cursor(Cursor.WAIT_CURSOR);
    private final static Cursor DEFAULT = new Cursor(Cursor.DEFAULT_CURSOR);
    
    private JFrame mainWindow;

    private JMenuBar menuBar = new JMenuBar();
    private JMenu fileMenu = new JMenu("File");
    private JMenuItem connect = new JMenuItem("Connect...");
    private JMenuItem disconnect = new JMenuItem("Disconnect");
    private JMenuItem exit = new JMenuItem("Exit");

    private JTabbedPane tabPanel = new JTabbedPane(JTabbedPane.BOTTOM);
    private ClusterPanel healthPanel = new ClusterPanel();
    private ClusterPanel groupPanel = new ClusterPanel();
    private ClusterModel model = new ClusterModel();
    private List<Timer> timers;

    public ManagementConsole() {
        this.mainWindow = new JFrame("Project Darkstar Management Console");

        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setSize(600,300);
        mainWindow.setLocationRelativeTo(null);

        this.build();

        mainWindow.setVisible(true);
    }

    private void build() {
        // setup menu
        this.connect.addActionListener(this);
        this.disconnect.addActionListener(this);
        this.disconnect.setEnabled(false);
        this.exit.addActionListener(this);
        this.fileMenu.add(connect);
        this.fileMenu.add(disconnect);
        this.fileMenu.add(new JSeparator());
        this.fileMenu.add(exit);
        this.menuBar.add(fileMenu);
        this.mainWindow.setJMenuBar(menuBar);

        // setup main panels
        this.healthPanel.setLayout(new BorderLayout(5, 5));
        this.groupPanel.setLayout(new BorderLayout(5, 5));
        this.tabPanel.add("Node Health", healthPanel);
        this.tabPanel.add("Affinity Groups", groupPanel);
        this.tabPanel.addChangeListener(this);

        this.mainWindow.add(tabPanel);
    }

    //--- Implement ActionListener ---//

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == this.connect) {
            String server = JOptionPane.showInputDialog(
                    mainWindow,
                    "Enter the host:port of the core server node",
                    "Connect Dialog",
                    JOptionPane.QUESTION_MESSAGE);
            new ConnectWorker(server).userExecute();
        } else if (event.getSource() == this.disconnect) {
            new DisconnectWorker().userExecute();
        } else if (event.getSource() == this.exit) {
            System.exit(0);
        }
    }

    //--- Implement ChangeListener ---//

    /**
     * {@inheritDoc}
     */
    public void stateChanged(ChangeEvent event) {
        if (timers != null) {
            for (Timer t : timers) {
                t.cancel();
            }

            ClusterPanel selectedPanel = (ClusterPanel) tabPanel.getSelectedComponent();
            // re-initiate the update timers
            timers = new ArrayList<Timer>(selectedPanel.serverPanels());
            for (int i = 0; i < selectedPanel.serverPanels(); i++) {
                Timer timer = new Timer();
                timers.add(timer);
                timer.schedule(new UpdateWorker(i, selectedPanel), 100, 100);
            }
        }
    }

    /**
     * Sets the cursor for the entire component to a wait cursor.
     *
     * @param component
     */
    private static void setWaitCursor(JComponent component)
    {
        RootPaneContainer root =
                          (RootPaneContainer) component.getTopLevelAncestor();
        root.getGlassPane().setCursor(WAIT);
        root.getGlassPane().addMouseListener(mouseAdapter);
        root.getGlassPane().setVisible(true);
    }

    /**
     * Sets the cursor for the entire component to the default cursor.
     *
     * @param component
     */
    private static void setDefaultCursor(JComponent component)
    {
        RootPaneContainer root =
                          (RootPaneContainer) component.getTopLevelAncestor();
        root.getGlassPane().setCursor(DEFAULT);
        root.getGlassPane().removeMouseListener(mouseAdapter);
        root.getGlassPane().setVisible(false);
    }


    private class ConnectWorker 
            extends SwingWorker<List<ServerModel.DataPacket>, Void> {
        
        private String server;
        
        public ConnectWorker(String server) {
            this.server = server;
        }

        public void userExecute() {
            setWaitCursor(mainWindow.getRootPane());
            this.execute();
        }
        
        protected List<ServerModel.DataPacket> doInBackground()
                throws Exception {
            model.connect(server);
            return model.getInitialSnapshot();
        }

        public void done() {
            setDefaultCursor(mainWindow.getRootPane());
            try {
                List<ServerModel.DataPacket> models = get();
                
                // setup server panels
                healthPanel.setLayout(new GridLayout(1, models.size()));
                for (ServerModel.DataPacket packet : models) {
                    healthPanel.addServerPanel(new HealthServerPanel(packet));
                }

                // setup server panels
                groupPanel.setLayout(new GridLayout(1, models.size()));
                for (ServerModel.DataPacket packet : models) {
                    groupPanel.addServerPanel(new GroupServerPanel(packet));
                }

                // initiate the update timers
                timers = new ArrayList<Timer>(models.size());
                for (int i = 0; i < models.size(); i++) {
                    Timer timer = new Timer();
                    timers.add(timer);
                    timer.schedule(new UpdateWorker(i, (ClusterPanel) tabPanel.getSelectedComponent()), 100, 100);
                }

                // initiate the high water listeners
                for (int i = 0; i < models.size(); i++) {
                    HighWaterListener l = new HighWaterListener(i);
                    healthPanel.getServerPanel(i).addPropertyChangeListener(l);
                    groupPanel.getServerPanel(i).addPropertyChangeListener(l);
                }

                connect.setEnabled(false);
                disconnect.setEnabled(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        mainWindow,
                        "Unable to connect to " + server +
                        " : \n" + e.getMessage(),
                        "Error connecting",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class HighWaterListener implements PropertyChangeListener {

        private ServerModel model;

        public HighWaterListener(int index) {
            this.model = ManagementConsole.this.model.getModel(index);
        }

        public synchronized void propertyChange(PropertyChangeEvent e) {
            new Thread(new HighWaterUpdater(model, (Integer) e.getNewValue())).start();
        }
    }

    private static class HighWaterUpdater implements Runnable {
        private ServerModel model;
        private int value;
        public HighWaterUpdater(ServerModel model, int value) {
            this.model = model;
            this.value = value;
        }
        public void run() {
            this.model.setLoginHighWater(value);
        }
    }

    private class UpdateWorker extends TimerTask {

        private Timer timer;
        private ServerModel model;
        private ServerPanel panel;

        public UpdateWorker(int index, ClusterPanel selectedTab) {
            this.model = ManagementConsole.this.model.getModel(index);
            this.panel = selectedTab.getServerPanel(index);
            this.timer = ManagementConsole.this.timers.get(index);
        }

        public void run() {
            try {
                ServerModel.DataPacket packet = model.getSnapshot();
                EventQueue.invokeLater(new PanelUpdater(panel, packet));
            } catch (Exception e) {
                e.printStackTrace();
                timer.cancel();
                EventQueue.invokeLater(new PanelUpdater(panel, null));
            }
        }
    }

    private static class PanelUpdater implements Runnable {
        private ServerPanel panel;
        private ServerModel.DataPacket packet;
        public PanelUpdater(ServerPanel panel, ServerModel.DataPacket packet) {
            this.panel = panel;
            this.packet = packet;
        }
        public void run() {
            panel.update(packet);
        }
    }

    private class DisconnectWorker extends SwingWorker<Void, Void> {

        public void userExecute() {
            setWaitCursor(mainWindow.getRootPane());
            this.execute();
        }

        protected Void doInBackground() throws Exception {
            model.disconnect();
            for (Timer t : timers) {
                t.cancel();
            }
            timers = null;
            return null;
        }

        public void done() {
            setDefaultCursor(mainWindow.getRootPane());
            
            // cleanup server panels
            healthPanel.clear();
            groupPanel.clear();

            connect.setEnabled(true);
            disconnect.setEnabled(false);
        }
    }


    private static class ClusterPanel extends JPanel {

        private List<ServerPanel> activePanels = new ArrayList<ServerPanel>();

        public void addServerPanel(ServerPanel p) {
            activePanels.add(p);
            super.add(p);
        }

        public ServerPanel getServerPanel(int index) {
            return activePanels.get(index);
        }

        public int serverPanels() {
            return activePanels.size();
        }

        public void clear() {
            activePanels.clear();
            super.removeAll();
        }
    }

}
