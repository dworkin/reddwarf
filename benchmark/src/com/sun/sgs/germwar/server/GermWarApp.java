/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.germwar.shared.GermWarConstants;
import com.sun.sgs.germwar.shared.message.Protocol;
import com.sun.sgs.germwar.shared.message.TurnUpdate;
import com.sun.sgs.germwar.server.WorldManager;
import com.sun.sgs.germwar.server.ClientSessionManager;
import com.sun.sgs.germwar.server.impl.GermWarClientSessionListener;

/**
 * The top-level class for the GermWar application.  Handles initialization of
 * the game world.
 */
public class GermWarApp implements Serializable, AppListener {
    /** Property name for the class to use for the {@link World}. */
    public static final String WORLD_CLASS_PROPERTY =
        "com.sun.sgs.germwar.world.class";
    
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;
    
    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(GermWarApp.class.getName());
    
    /**
     * Default constructor.
     */
    public GermWarApp() {
        logger.log(Level.INFO, "Starting up.");
    }
    
    // Implement AppListener
    
    /**
     * {@inheritDoc}
     * <p>
     * {@code ChatApp} creates its {@linkplain com.sun.sgs.app.Channel
     * Channels} as they are needed, so it has no initialization to perform
     * on startup.
     */
    public void initialize(Properties props) {
        logger.log(Level.CONFIG, "GermWarApp starting up.");

        String worldClassName = props.getProperty(WORLD_CLASS_PROPERTY);

        if (worldClassName == null)
            throw new IllegalArgumentException("Property not specified: " +
                WORLD_CLASS_PROPERTY);

        logger.log(Level.CONFIG, "World instance class = " + worldClassName);

        /**
         * Create new World of the specified class and load it into the
         * WorldManager.
         */
        try {
            Class<?> worldClass = Class.forName(worldClassName);
            Object world = worldClass.newInstance();
            World worldInstance = World.class.cast(world);
            worldInstance.initialize(props);
            WorldManager.initialize(worldInstance);
        } catch (ClassNotFoundException cnfe) {
            logger.log(Level.SEVERE, "World class could not be" +
                " found: " + cnfe.toString());
            
            throw new RuntimeException(cnfe);
        } catch (IllegalAccessException iae) {
            logger.log(Level.SEVERE, "World class could not be" +
                " instantiated: " + iae.toString());
            
            throw new RuntimeException(iae);
        } catch (InstantiationException ie) {
            logger.log(Level.SEVERE, "World class could not be" +
                " instantiated: " + ie.toString());
            
            throw new RuntimeException(ie);
        }

        /** Creates the global channel that all players are added to. */
        ChannelManager cm = AppContext.getChannelManager();
        Channel globalChannel =
            cm.createChannel(GermWarConstants.GLOBAL_CHANNEL_NAME, null,
                Delivery.RELIABLE);

        /** Schedule a rarely-running task to print player stats. */
        TaskManager tm = AppContext.getTaskManager();
        tm.schedulePeriodicTask(new ParentStatsTask(), 60*1000, 60*1000);

        /** Start the turn task (note that returned handle is thrown away). */
        tm.schedulePeriodicTask(new TurnTask(), GermWarConstants.TURN_DURATION,
            GermWarConstants.TURN_DURATION);

        logger.log(Level.CONFIG, "TurnTask started with turn duration = " +
            GermWarConstants.TURN_DURATION);

        logger.log(Level.INFO, "Server initialization complete.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code ChatApp} returns a new {@link ChatClientSessionListener}
     * for the given {@code session}.
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        /** Add this user to the ClientSessionManager. */
        ClientSessionManager.add(session.getName(), session);
        logger.log(Level.FINE, "ClientSession joined: {0}", session);
        return new GermWarClientSessionListener(session);
    }

    /**
     * Inner class: TurnTask
     */
    private static class TurnTask implements ManagedObject, Serializable, Task {
        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;

        /**
         * Creates a new {@code TurnTask}.
         */
        public TurnTask() {
            // empty
        }

        // implement Task

        public void run() throws Exception {
            long turn = TurnManager.incrementTurn();

            Channel globalChannel =
                AppContext.getChannelManager().getChannel(GermWarConstants.GLOBAL_CHANNEL_NAME);

            globalChannel.send(Protocol.write(new TurnUpdate(turn)));
        }
    }

    /**
     * Inner class: ParentStatsTask
     */
    private static class ParentStatsTask
        implements ManagedObject, Serializable, Task
    {
        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;

        /**
         * Creates a new {@code ParentStatsTask}.
         */
        public ParentStatsTask() {
            // empty
        }

        // implement Task

        /**
         * We spawn new tasks for each individual player's stats because if we
         * tried to do them all in one task it would probably be way too long.
         */
        public void run() throws Exception {
            TaskManager tm = AppContext.getTaskManager();
            Iterator<ManagedReference> iter = PlayerManager.refIterator();

            logger.log(Level.INFO, System.currentTimeMillis() +
                "\tStarting StatsTask.  players=" + PlayerManager.playerCount());

            while (iter.hasNext()) {
                tm.scheduleTask(new StatsTask(iter.next()));
            }
        }
    }

    /**
     * Inner class: StatsTask
     */
    private static class StatsTask implements ManagedObject, Serializable, Task {
        /** A ManagedReference to the player on which to report stats. */
        private ManagedReference playerRef;

        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;

        /**
         * Creates a new {@code StatsTask}.
         */
        public StatsTask(ManagedReference playerRef) {
            this.playerRef = playerRef;
        }

        // implement Task

        public void run() throws Exception {
            Player player = playerRef.get(Player.class);
            ClientSession playerSession =
                ClientSessionManager.get(player.getUsername());

            StringBuffer sb = new StringBuffer();
            sb.append(System.currentTimeMillis()).append("\tPlayer #");
            sb.append(player.getId()).append(", ").append(player.getUsername());
            sb.append("\tlogged in? ");
            sb.append(playerSession == null ? "n" : "y");
            sb.append("\tcount: ").append(player.bacteriaCount());
            logger.log(Level.INFO, sb.toString());
        }
    }
}
