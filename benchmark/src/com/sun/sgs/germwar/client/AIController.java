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
        gameLogic.login();

        /** Ugly hack to keep JVM alive long enough for IO threads to start. */
        (new Thread() {
                @Override
                public void run() {
                    synchronized (this) {
                        while (true) {
                            try {
                                this.wait();
                            } catch (InterruptedException ignore) { }
                        }
                    }
                }
            }).start();
    }

    /**
     * Iterates all bacteria and decides on something for them to do.
     */
    private void executeAI() {
        Set<Bacterium> remaining = new HashSet<Bacterium>(userBacteria.values());
        Iterator<Bacterium> iter = remaining.iterator();

        while (remaining.size() > 0) {
            boolean moved = false;

            while (iter.hasNext()) {
                Bacterium bact = iter.next();

                if (bact.getCurrentMovementPoints() == 0) {
                    iter.remove();
                    break;
                }

                Coordinate coord = bact.getCoordinate();
                float bestFood = 0;
                Location bestFoodLoc = null;

                for (int i=0; i < 4; i++) {
                    Coordinate dest = null;

                    if (i == 0) dest = coord.offsetBy(-1, 0);
                    if (i == 1) dest = coord.offsetBy(1, 0);
                    if (i == 2) dest = coord.offsetBy(0, -1);
                    if (i == 3) dest = coord.offsetBy(0, 1);

                    Location loc = gameLogic.getLocation(dest);

                    if ((loc != null) && (!loc.isOccupied()) &&
                        (loc.getFood() > bestFood)) {
                        bestFood = loc.getFood();
                        bestFoodLoc = loc;
                    }
                }

                if (bestFoodLoc == null) {
                    logger.log(Level.FINE, bact + ": nowhere to go.");
                    break;
                }

                try {
                    gameLogic.doMove(gameLogic.getLocation(coord), bestFoodLoc);
                } catch (InvalidMoveException ime) {
                    logger.log(Level.FINE, bact + ": " + ime);
                    break;  /** Give up on this bacterium for this turn. */
                }

                /**
                 * There can always be synchronization problems with the server, but to
                 * try to mitigate them, we pause slightly between moves to give the
                 * server a chance to respond with any updates.
                 */
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignore) { }

                moved = true;
            }

            if (!moved) {
                /**
                 * Nobody could move; probably not worth trying again
                 * (technically, they could be blocked on another player which will
                 * move later this turn, but we ignore this case).
                 */
                return;
            }
        }
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
        GermWarClient gameClient = new GermWarClient();
        Random rand = new Random();
        AIController controller =
            new AIController(gameClient, "AI:" + rand.nextInt(), "pwd");

        gameClient.setGui(controller);

        if ((args.length > 0) && (args[0].equals("-g"))) {
            GameGui swingGui = new MainFrame(controller);
            controller.setGui(swingGui);
        }

        controller.setVisible(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mapUpdate(Location loc) {
        Bacterium bact = getBacteriumFromLoc(loc);
        if (bact != null) userBacteria.put(bact.getId(), bact);

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
        executeAI();
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
        gui.popup("Not supported; AI controller is active.",
            JOptionPane.ERROR_MESSAGE);
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
}
