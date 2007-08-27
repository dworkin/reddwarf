/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import com.sun.sgs.germwar.client.gui.MainFrame;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.Location;

/**
 * An AI "layer" that can be used as an implementation of {@code GameGui} to
 * provide AI of a GermWarClient without a GUI.  Or another instance of {@code
 * GameGui} can be layered on top in order to provide a graphical interface to
 * the AI controller, except that all game-controlling actions (e.g. moving
 * bacteria) are disallowed (i.e. you can see stuff, but you can't control
 * stuff).
 */
public class AIController extends NullGui implements GameLogic {
    /** The {@code GameLogic} instance layered underneath us. */
    private GameLogic gameLogic = null;

    /** The {@code GameGui} instance layered above us. */
    private GameGui gui = new NullGui("default", "pwd");

    /** Cached to pass on to any GUIs that are assigned with {@code setGui}. */
    private boolean loginStatus = false;

    /** List of all Bacteria known to exist for this user. */
    private Map<Integer,Bacterium> userBacteria =
        new HashMap<Integer,Bacterium>();

    /**
     * List of locations to which we have tried to move this turn.  This is
     * meant to prevent the following situation:  An AI player has 2 bacteria
     * both within movement range of 1 square.  A request is sent to the server
     * to move one of the bacteria to that square, and the server processed it.
     * But before a response is sent back to the client, the client sends
     * another request to move the second bacteria to the same square (which
     * still appears empty to the client).  When the server gets this message,
     * the move request fails because the destination is not empty.
     */
    private Set<Coordinate> movedLocs = new HashSet<Coordinate>();

    /** Handles the AI actions. */
    private ActionThread actionThread = new ActionThread();

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(AIController.class.getName());
    
    // Constructor

    /**
     * Creates a new {@code AIController}.
     */
    public AIController(GameLogic gameLogic, String login, String password) {
        super(login, password);
        this.gameLogic = gameLogic;

        actionThread.start();
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

    // Main

    /**
     * Runs a new {@code GermWarClient} application with an instance of {@link
     * AIController} as the GUI.
     * 
     * @param args the commandline arguments (not used)
     */
    public static void main(String[] args) {
        int count = 1;
        boolean showGui = false;

        if (args.length > 0) {
            try {
                count = Integer.valueOf(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.println("Usage: com.sun.sgs.germwar.client." +
                    "AIController [bot-count] [-g]  (-g shows GUI)");
                return;
            }

            showGui = (args.length >= 2) && (args[1].equals("-g"));
        }

        Random rand = new Random();

        for (int i=0; i < count; i++) {
            GermWarClient gameClient = new GermWarClient();
            AIController controller = new AIController(gameClient,
                "AI:" + rand.nextInt(), "pwd");

            gameClient.setGui(controller);
            if (showGui) controller.setGui(new MainFrame(controller));

            controller.setVisible(true);
            controller.login();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) { }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mapUpdate(Location loc) {
        Bacterium bact = getBacteriumFromLoc(loc);

        if (bact != null) {
            synchronized (userBacteria) {
                userBacteria.put(bact.getId(), bact);
            }
        }

        // todo - some way to remove bacteria?  (if the game ever has this)

        gui.mapUpdate(loc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newChatMessage(String sender, String message) {
        gui.newChatMessage(sender, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newTurn() {
        gui.newTurn();
        actionThread.poke();

        /** Clear list of locations moved to. */
        synchronized (movedLocs) {
            movedLocs.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void popup(String message, int messageType) {
        gui.popup(message, messageType);
    }

    /** Sets the {@code GameGui} that this object should interface with. */
    public void setGui(GameGui gui) {
        this.gui = gui;
        gui.setLoginStatus(loginStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLoginStatus(boolean loggedIn) {
        loginStatus = loggedIn;
        gui.setLoginStatus(loggedIn);
        actionThread.pause(!loggedIn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatusMessage(String message) {
        gui.setStatusMessage(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVisible(boolean b) {
        gui.setVisible(b);
    }

    // implement GameLogic

    /**
     * {@inheritDoc}
     */
    public void doMove(Location src, Location dest) throws InvalidMoveException {
        gui.popup("Not supported; AI controller is active.",
            JOptionPane.ERROR_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    public Location getLocation(Coordinate coord) {
        return gameLogic.getLocation(coord);
    }

    /**
     * {@inheritDoc}
     */
    public long getPlayerId() {
        return gameLogic.getPlayerId();
    }

    /**
     * {@inheritDoc}
     */
    public void login() {
        gameLogic.login();
    }

    /**
     * {@inheritDoc}
     */
    public void logout(boolean force) {
        gameLogic.logout(force);
    }

    /**
     * {@inheritDoc}
     */
    public void quit() {
        gameLogic.quit();
    }

    /**
     * {@inheritDoc}
     */
    public void sendChatMessage(String recipient, String message) {
        gameLogic.sendChatMessage(recipient, message);
    }

    /**
     * Inner class: ActionThread
     */
    private class ActionThread extends Thread {
        /** Controls thread execution. */
        private boolean enabled = false;
        private Object lock = new Object();

        /**
         * Creates a new {@code ActionThread}.
         */
        public ActionThread() {
            super();

            /**
             * Its important to let the SGS IO thread have higher priority over
             * us so that we don't stall message handling.
             */
            setPriority(Thread.MIN_PRIORITY);
        }

        /**
         * Sets/clears a variable that (eventually) starts/stops the thread's
         * execution.
         */
        public void pause(boolean pause) {
            synchronized (lock) {
                enabled = (!pause);
                lock.notifyAll();
            }
        }

        /**
         * Wakes up the thread if it is sleeping.
         */
        public void poke() {
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            while (true) {
                /** Sleep as long as thread is not enabled. */
                do {
                    if (enabled) break;

                    try {
                        synchronized (lock) {
                            lock.wait();
                        }
                    } catch (InterruptedException ignore) { }
                } while (true);

                /** Iterate bacteria, looking for one that can move. */
                synchronized (userBacteria) {
                    for (Bacterium bact : userBacteria.values()) {
                        /** Good to recheck this every iteration. */
                        if (!enabled) break;

                        if (bact.getCurrentMovementPoints() == 0) continue;

                        Coordinate coord = bact.getCoordinate();
                        Location curLoc = gameLogic.getLocation(coord);
                        float bestFood = 0;
                        Location bestFoodLoc = null;

                        /**
                         * Check that bacterium is still alive. (see comments
                         * in iterateBacteria() in client/gui/MainFrame.java for
                         * more info and a better alternative to this handling.
                         */
                        Bacterium occ = curLoc.getOccupant();
                        if ((occ == null) || (!bact.equals(occ))) {
                            /**
                             * Error - bacterium is out of sync with the world
                             * state; assume its dead.
                             */
                            userBacteria.remove(bact.getId());
                            continue;
                        }

                        synchronized (movedLocs) {
                            for (int i=0; i < 4; i++) {
                                Coordinate dest = null;

                                if (i == 0) dest = coord.offsetBy(-1, 0);
                                if (i == 1) dest = coord.offsetBy(1, 0);
                                if (i == 2) dest = coord.offsetBy(0, -1);
                                if (i == 3) dest = coord.offsetBy(0, 1);

                                if (movedLocs.contains(dest)) continue;

                                Location loc = gameLogic.getLocation(dest);

                                if ((loc != null) && (!loc.isOccupied()) &&
                                    (loc.getFood() > bestFood)) {
                                    bestFood = loc.getFood();
                                    bestFoodLoc = loc;
                                }
                            }
                        }

                        /** Nowhere to go... */
                        if (bestFoodLoc == null) continue;

                        try {
                            gameLogic.doMove(gameLogic.getLocation(coord), bestFoodLoc);
                        } catch (InvalidMoveException ime) {
                            logger.log(Level.FINE, bact + ": " + ime);
                            continue;
                        }

                        synchronized (movedLocs) {
                            movedLocs.add(bestFoodLoc.getCoordinate());
                        }
                    }
                }

                /** Sleep until we are poked - ok if we wake up early. */
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignore) { }
                }
            }
        }
    }
}
