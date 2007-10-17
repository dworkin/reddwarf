/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;

import com.sun.sgs.germwar.client.GameGui;
import com.sun.sgs.germwar.client.GameLogic;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.GermWarConstants;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.Location;

/**
 * A simple GUI for the GermWar client application.  It abstracts all of the
 * Swing operations from the actual application logic.  This class should have
 * no knowledge of SGS classes.
 * <p>
 * Visually, this class presents a zoomed-in view of a portion of the map along
 * with a small mini-map to orient the user to where they are looking currently
 * within the context of the entire game world.  There are also a few buttons
 * and such for users to control their bacteria.
 */
public class MainFrame extends JFrame implements GameGui {
    /** Directory where all image files for all GUI components are kept. */
    public static final String IMAGE_ROOT = "benchmark/GermWarData/images";

    /** GUI components places in/on this frame. */
    private final ChatPanel chatPanel;
    private final ControlsPanel controlsPanel;
    private final InfoBox infoArea;
    private final JLabel headerMessage;
    private final JLabel statusMessage;
    private final MainMapPanel mainMapPanel;
    private final MiniMapPanel miniMapPanel;

    /**
     * Whether the user is currently in the middle of performing a "move"
     * action, meaning that a specific bacterium was select and then some kind of
     * GUI event occurred (e.g. key press) to indicate that a move action is
     * requested, but the user has not yet chosen the destination.
     */
    private boolean amPerformingMove = false;

    /** The {@code GameLogic} instance to interface with. */
    private GameLogic gameLogic;

    /** List of all Bacteria known to exist for this user. */
    private Map<Integer,Bacterium> userBacteria =
        new HashMap<Integer,Bacterium>();
    private List<Integer> userBacteriaIds = new ArrayList<Integer>();

    /** Lock for userBacteria and userBacteriaIds data structures. */
    private Object userBacteriaLock = new Object();

    /** Displays a countdown during turns. */
    private final ClockTimer turnTimer = new ClockTimer();

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(MainFrame.class.getName());
    
    // Constructor

    /**
     * Creates a new {@code MainFrame}.
     */
    public MainFrame(final GameLogic gameLogic) {
        super();
        this.gameLogic = gameLogic;

        setTitle("GermWar Client");
        setFocusable(true);  // todo - needed?
        addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    gameLogic.quit();
                }
            });

        ActionListener actionListener = new MainActionListener();

        chatPanel = new ChatPanel();
        chatPanel.addActionListener(actionListener);
        chatPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        controlsPanel = new ControlsPanel();
        controlsPanel.addActionListener(actionListener);

        infoArea = new InfoBox();

        mainMapPanel = new MainMapPanel(gameLogic, turnTimer);
        mainMapPanel.addItemListener(new MapSelectListener());
        mainMapPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        miniMapPanel = new MiniMapPanel();

        headerMessage = new JLabel("(header message)");
        headerMessage.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        statusMessage = new JLabel();
        statusMessage.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setStatusMessage("Disconnected");

        setupKeyBindings();
        setupLayout();
        pack();
    }

    /**
     * If the occupant of {@code loc} (if any) belongs to this player, it is
     * returned.  Otherwise, {@code null} is returned.
     */
    private Bacterium getBacteriumFromLoc(Location loc) {
        Bacterium bact = loc.getOccupant();

        if ((bact != null) && (bact.getPlayerId() == gameLogic.getPlayerId())) {
            return bact;
        } else {
            return null;
        }
    }

    /**
     * Called when the user is starting or stopping a movement command (after
     * the entity to move is chosen, but before the target space is chosen).
     */
    private void enableMove(boolean enable) {
        amPerformingMove = enable;

        if (enable)
            mainMapPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        else
            mainMapPanel.setCursor(null);
    }

    /**
     * Moves selection & focus to the next (or previous) bacterium.
     * @param forward iterates forward if {@code true}, backwards if {@code false}
     */
    private void iterateBacteria(boolean forward) {
        Coordinate coord = mainMapPanel.getSelected();
        int targetId = -1;

        synchronized (userBacteriaLock) {
            if (userBacteria.size() == 0) return;

            if (coord != null) {
                Bacterium selected =
                    getBacteriumFromLoc(gameLogic.getLocation(coord));

                if (selected != null) {
                    /** Need to add userBacteria.size() to make mod positive. */
                    int index = userBacteriaIds.indexOf(selected.getId()) +
                        (forward ? 1 : -1) + userBacteria.size();

                    index = index % userBacteria.size();  /** wrap */
                    targetId = userBacteriaIds.get(index);
                }
            }

            /** default if the above didn't work out... */
            if (targetId == -1) targetId = userBacteriaIds.get(0);

            Bacterium target = userBacteria.get(targetId);
            coord = target.getCoordinate();

            /** Check that bacterium actually exists where we think it does. */
            Location loc = gameLogic.getLocation(coord);
            Bacterium occ = loc.getOccupant();

            if ((occ != null) && (target.equals(occ))) {
                /** Ok - checks out. */
                mainMapPanel.setSelected(coord);
                mainMapPanel.setFocus(coord);
                infoArea.display(loc);
            } else {
                /**
                 * Error - this bacterium thinks that its at a location where it
                 * isn't.  We assume that this is because this bacterium has
                 * died (and thus we recieved a location update - with no
                 * occupant - for its location).  Note that this isn't very
                 * robust - an explicit "bacterium has died" message from the
                 * server would be better.
                 */
                userBacteria.remove(targetId);

                /**
                * Have to explicitly make this an Integer because remove(int)
                * and remove(Object) are different methods on List interface.
                */
                userBacteriaIds.remove(new Integer(targetId));

                newChatMessage(null, "Bacterium #" + targetId + " at " + coord +
                    " appears to have died...");

                /** Recurse until we find a valid (alive) bacterium. */
                iterateBacteria(forward);
            }
        }
    }

    /**
     * Sets the contents/color of the header message.
     */
    private void setHeaderMessage(String message, Color color) {
        headerMessage.setText(message);
        headerMessage.setForeground(color);
    }

    private void setupKeyBindings() {
        JComponent rootPane = getRootPane();

        InputMap keyMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        keyMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc key");
        keyMap.put(KeyStroke.getKeyStroke('c'), "c key");
        keyMap.put(KeyStroke.getKeyStroke('d'), "d key");
        keyMap.put(KeyStroke.getKeyStroke('m'), "m key");
        keyMap.put(KeyStroke.getKeyStroke('w'), "w key");

        ActionMap actionMap = rootPane.getActionMap();
        actionMap.put("esc key", new AbstractAction() {
                public void actionPerformed(ActionEvent ignored) {
                    mainMapPanel.setSelected(null);
                }});

        actionMap.put("c key", new AbstractAction() {
                public void actionPerformed(ActionEvent ignored) {
                    Coordinate selected = mainMapPanel.getSelected();
                    if (selected != null)
                        mainMapPanel.setFocus(selected);
                    else
                        Toolkit.getDefaultToolkit().beep();
                }});

        actionMap.put("d key", new AbstractAction() {
                public void actionPerformed(ActionEvent ignored) {
                    // todo - "done"
                }});

        actionMap.put("m key", new AbstractAction() {
                public void actionPerformed(ActionEvent ignored) {
                    if (mainMapPanel.getSelected() != null)
                        enableMove(true);
                    else
                        Toolkit.getDefaultToolkit().beep();
                }});

        actionMap.put("w key", new AbstractAction() {
                public void actionPerformed(ActionEvent ignored) {
                    // todo - "wait"
                }});
    }

    private void setupLayout() {
        Container cpane = getContentPane();

        // right-hand panel
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));

        // tabbed-pane (info area & mini-map)
        JTabbedPane jTabbedPane1 = new JTabbedPane();
        jTabbedPane1.setPreferredSize(new Dimension(400, 300));
        jTabbedPane1.addTab("Details", new JScrollPane(infoArea));
        jTabbedPane1.addTab("MiniMap", miniMapPanel);

        rightPanel.add(jTabbedPane1);
        rightPanel.add(Box.createRigidArea(new Dimension(0,10)));
        rightPanel.add(controlsPanel);
        rightPanel.add(Box.createVerticalGlue());

        // middle section (main map + right-hand panel)
        JPanel middlePanel = new JPanel();
        rightPanel.setAlignmentY(java.awt.Component.TOP_ALIGNMENT);
        mainMapPanel.setAlignmentY(java.awt.Component.TOP_ALIGNMENT);

        middlePanel.setLayout(new FlowLayout());
        mainMapPanel.setPreferredSize(new Dimension(500, 500));
        middlePanel.add(mainMapPanel);
        middlePanel.add(Box.createRigidArea(new Dimension(10, 0)));
        middlePanel.add(rightPanel);

        headerMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        middlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        chatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusMessage.setAlignmentX(Component.LEFT_ALIGNMENT);

        cpane.setLayout(new BoxLayout(cpane, BoxLayout.Y_AXIS));
        cpane.add(headerMessage);
        cpane.add(new JSeparator(JSeparator.HORIZONTAL));
        cpane.add(middlePanel);
        cpane.add(new JSeparator(JSeparator.HORIZONTAL));
        cpane.add(chatPanel);
        cpane.add(new JSeparator(JSeparator.HORIZONTAL));
        cpane.add(statusMessage);
    }

    // implement GameGui

    /**
     * {@inheritDoc}
     */
    public void mapUpdate(Location loc) {
        Bacterium bact = getBacteriumFromLoc(loc);

        if (bact != null) {
            synchronized (userBacteriaLock) {
                if (userBacteria.put(bact.getId(), bact) == null) {
                    /** new entry */
                    if (userBacteriaIds.size() == 0) {
                        mainMapPanel.setFocus(loc.getCoordinate());
                    }
                    
                    userBacteriaIds.add(bact.getId());
                }
            }
        }
        
        mainMapPanel.update(loc);
        infoArea.updateIf(loc);
    }

    /**
     * {@inheritDoc}
     */
    public void newChatMessage(String sender, String message) {
        /** Just pass through to the chat panel. */
        chatPanel.addRecord(message, sender);
    }

    /**
     * {@inheritDoc}
     */
    public void newTurn() {
        infoArea.refresh();
        mainMapPanel.refresh();
        turnTimer.start(GermWarConstants.TURN_DURATION);
    }

    /**
     * {@inheritDoc}
     */
    public void popup(String message, int messageType) {
        JOptionPane.showMessageDialog(this,
            message, "Alert", messageType);
    }

    /**
     * {@inheritDoc}
     */
    public PasswordAuthentication promptForLogin() {
        Future<PasswordAuthentication> future =
            new LoginDialog(this).requestLogin();
        
        try {
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setLoginStatus(boolean loggedIn) {
        chatPanel.setEnabled(loggedIn);
        infoArea.setEnabled(loggedIn);
        mainMapPanel.setEnabled(loggedIn);
        miniMapPanel.setEnabled(loggedIn);
        turnTimer.clear();

        /** Special command for controls panel. */
        controlsPanel.setLoginState(loggedIn);
    }

    /**
     * {@inheritDoc}
     */
    public void setStatusMessage(String message) {
        statusMessage.setText("Status: " + message);
    }

    /**
     * Note - GameGui.setVisible() is already implemented by the superclass.
     */

    /**
     * Inner class: MainActionListener
     */
    final class MainActionListener implements ActionListener {
        /** Generic constructor */
        public MainActionListener() {
            // empty
        }
        
        // Implement ActionListener
        
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent action) {
            String command = action.getActionCommand();
            
            if (command.equals(ChatPanel.CHAT_ACTION)) {
                String recip = chatPanel.getRecipient();
                String msg = chatPanel.getMessage();
                
                if (recip.length() == 0) {
                    Toolkit.getDefaultToolkit().beep();
                    popup("Error: No recipient specified!",
                        JOptionPane.ERROR_MESSAGE);
                } else if (msg.length() == 0) {
                    // ignore
                } else {
                    gameLogic.sendChatMessage(recip, msg);
                    chatPanel.setMessage("");  /** clear message field. */
                }
            }
            else if (command.equals(ControlsPanel.FINISH_TURN_ACTION)) {
                // todo
            }
            else if (command.equals(ControlsPanel.LOGOUT_ACTION)) {
                gameLogic.logout(false);  /* non-force */
            }
            else if (command.equals(ControlsPanel.LOGIN_ACTION)) {
                gameLogic.login();
            }
            else if (command.equals(ControlsPanel.ITERATE_NEXT_ACTION)) {
                iterateBacteria(true);
            }
            else if (command.equals(ControlsPanel.ITERATE_PREV_ACTION)) {
                iterateBacteria(false);
            }
            else {
                logger.log(Level.SEVERE, String.format("Unknown GUI action" +
                               " received (%s)\n", command));
                logger.log(Level.SEVERE, "action event = " + action);
                logger.log(Level.SEVERE, "action event = " + action.paramString());
            }
        }
    }

    /**
     * Inner class: MapSelectListener
     */
    final class MapSelectListener implements ItemListener {
        /** Generic constructor */
        public MapSelectListener() {
            // empty
        }

        // Implement ItemListener

        /**
         * {@inheritDoc}
         */
        public void itemStateChanged(ItemEvent e) {
            setHeaderMessage(" ", null);

            if (e.getStateChange() == ItemEvent.DESELECTED) {
                mainMapPanel.setSelected(null);
                infoArea.display(null);
                enableMove(false);
            } else {
                Location target = (Location)e.getItem();

                if (target == null) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                if (amPerformingMove) {
                    /** This is where the user is trying to move from. */
                    Location start = gameLogic.getLocation(mainMapPanel.getSelected());

                    try {
                        gameLogic.doMove(start, target);
                        infoArea.display(target);
                        mainMapPanel.setSelected(target.getCoordinate());
                    } catch (InvalidMoveException ime) {
                        /** Move is not legal. */
                        if (ime.getMessage() != null) {
                            setHeaderMessage("Invalid move: " + ime.getMessage(),
                                Color.RED);
                        } else if (ime.getCause() != null) {
                            setHeaderMessage("Cannot move: " +
                                ime.getCause().toString(), Color.RED);
                        } else {
                            setHeaderMessage("Invalid move", Color.RED);
                        }
                        Toolkit.getDefaultToolkit().beep();
                    }

                    enableMove(false);
                } else {
                    mainMapPanel.setSelected(target.getCoordinate());
                    
                    /** Populate infoArea with info on this location. */
                    infoArea.display(target);
                }
            }
        }
    }
}
