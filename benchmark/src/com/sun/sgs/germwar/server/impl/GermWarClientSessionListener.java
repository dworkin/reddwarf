/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server.impl;

import java.io.Serializable;
import java.net.ProtocolException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.impl.sharedutil.HexDumper;

import com.sun.sgs.germwar.server.ClientSessionManager;
import com.sun.sgs.germwar.server.Player;
import com.sun.sgs.germwar.server.PlayerManager;
import com.sun.sgs.germwar.server.World;
import com.sun.sgs.germwar.server.WorldManager;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.GermWarConstants;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.InvalidSplitException;
import com.sun.sgs.germwar.shared.Location;
import com.sun.sgs.germwar.shared.message.AckResponse;
import com.sun.sgs.germwar.shared.message.AppMessage;
import com.sun.sgs.germwar.shared.message.ChatMessage;
import com.sun.sgs.germwar.shared.message.ChatRequest;
import com.sun.sgs.germwar.shared.message.InitializationData;
import com.sun.sgs.germwar.shared.message.LocationUpdate;
import com.sun.sgs.germwar.shared.message.MoveRequest;
import com.sun.sgs.germwar.shared.message.NackResponse;
import com.sun.sgs.germwar.shared.message.Protocol;

/**
 * A listener for client sessions.
 */
public class GermWarClientSessionListener implements ClientSessionListener, Serializable {
    /** The range around which a location to which updates are sent. */
    public static final int NOTIF_RANGE = 3;

    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link ClientSession} this listener receives events for. */
    private final ClientSession session;

    /** ID of the {@link Player} that created this session. */
    private final long playerId;

    /** A nicely formatted name/sessionId string. */
    private final String formattedName;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(GermWarClientSessionListener.class.getName());
    
    /**
     * Constructor.
     */
    public GermWarClientSessionListener(ClientSession session) {
        this.session = session;
        this.formattedName = session.getName() + " [" +
            HexDumper.toHexString(session.getSessionId().getBytes()) + "]";
        
        logger.log(Level.FINE, "User " + session.getSessionId() + " (" +
            session.getName() + ") logged in.");
        
        Player player = PlayerManager.getPlayer(session.getName());
        boolean newPlayer = false;
        
        if (player == null) {
            /** New player - create an Player object for them. */
            player = PlayerManager.createPlayer(session.getName());
            player.initialize();
            newPlayer = true;
        }

        playerId = player.getId();

        World world = WorldManager.getWorld();

        /** Add player to global chat channel. */
        Channel globalChannel =
            AppContext.getChannelManager().getChannel(GermWarConstants.GLOBAL_CHANNEL_NAME);

        globalChannel.join(session, null);

        InitializationData initMsg = new InitializationData(playerId);
        session.send(Protocol.write(initMsg));

        /**
         * Next we need to iterate all of the bacteria that belong to this
         * player so that location updates can be sent for the regions
         * surrounding each bacterium.  But this is pawned off onto a new task
         * in the interest of trying to keep this login task from getting too
         * long.
         */
        TaskManager tm = AppContext.getTaskManager();
        tm.scheduleTask(new PlayerUpdateTask(playerId));
    }

    /**
     * Returns the coordinates of locations that are on a client's "horizon" as
     * it moves from {@code src} to {@code dest}; that is, the locations that
     * are not visible from {@code src}, but are visible from {@code dest}.
     */
    private Set<Coordinate> getHorizonCoordinates(Coordinate src,
        Coordinate dest)
    {
        Set<Coordinate> destRegion = getVisibleRegion(dest);
        destRegion.removeAll(getVisibleRegion(src));
        return destRegion;
    }

    /**
     * Returns the {@link Coordinate}s that would be within visible range of a
     * bacterium at {@code center}.
     */
    private Set<Coordinate> getVisibleRegion(Coordinate center) {
        Set<Coordinate> coords = new HashSet<Coordinate>();

        World world = WorldManager.getWorld();
        int minX = Math.max(0, center.getX() - NOTIF_RANGE);
        int minY = Math.max(0, center.getY() - NOTIF_RANGE);
        int maxX = Math.min(world.getXDimension() - 1, center.getX() + NOTIF_RANGE);
        int maxY = Math.min(world.getYDimension() - 1, center.getY() + NOTIF_RANGE);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <=maxY; y++) {
                coords.add(new Coordinate(x, y));
            }
        }

        return coords;
    }

    /**
     * Sends an update message with the current state of {@code loc} to all
     * players with a bacterium within {@code NOTIF_RANGE} of {@code loc}.
     */
    private void sendRegionUpdate(Location loc) {
        sendRegionUpdate(loc, 0);
    }

    /**
     * Sends an update message with the current state of {@code loc} to {code
     * playerId}, or to all players with a bacterium within {@code NOTIF_RANGE} of
     * {@code loc} if {@code playerId} is 0.
     */
    private void sendRegionUpdate(Location loc, long playerId) {
        World world = WorldManager.getWorld();
        Set<Long> notifPlayers = new HashSet<Long>();

        for (Coordinate coord : getVisibleRegion(loc.getCoordinate())) {
            Bacterium occ = world.getLocation(coord).getOccupant();

            if ((occ != null) && (occ.getPlayerId() != 0) &&
                ((occ.getPlayerId() == playerId) || (playerId == 0)))
                notifPlayers.add(occ.getPlayerId());
        }

        for (long _playerId : notifPlayers) {
            sendUpdate(loc, _playerId);
        }
    }

    /**
     * Sends an update message with the current state of {@code loc} to the
     * specified player, if they are logged in currently.
     */
    private void sendUpdate(Location loc, long playerId) {
        Player player = PlayerManager.getPlayer(playerId);
        ClientSession playerSession =
            ClientSessionManager.get(player.getUsername());

        if (playerSession != null) {  /** Could be logged out. */
            playerSession.send(Protocol.write(new LocationUpdate(loc)));
        }
    }

    /**
     * If {@code bact} has enough health (semi-arbitrary threshold), try to
     * split it.  Fails, returning {@code null} if {@code bact} does not have
     * enough health, 
     */
    private Location tryToSplit(Bacterium bact) {
        /** Note: semi-arbitrary threshold here. */
        if (bact.getHealth() < 300) return null;

        /** Fat enough... find a place to spawn to. */
        World world = WorldManager.getWorld();
        Coordinate parentCoord = bact.getCoordinate();
        Coordinate spawnCoord = null;
        Location spawnLoc = null;
        Bacterium spawn;
        int randStart = (int)(4*Math.random());

        for (int i=0; i < 4; i++) {
            int index = (randStart + i) % 4;

            if (index == 0) spawnCoord = parentCoord.offsetBy(-1, 0);
            if (index == 1) spawnCoord = parentCoord.offsetBy(1, 0);
            if (index == 2) spawnCoord = parentCoord.offsetBy(0, -1);
            if (index == 3) spawnCoord = parentCoord.offsetBy(0, 1);

            if (world.validCoordinate(spawnCoord))
                spawnLoc = world.getLocation(spawnCoord);
            else
                spawnLoc = null;

            if ((spawnLoc != null) && (!spawnLoc.isOccupied())) {
                /** Found a free space, try to split into it. */
                try {
                    spawn = bact.doSplit(spawnCoord);
                } catch (InvalidSplitException ise) {
                    logger.log(Level.WARNING, "Failed split attempt: " +
                        ise.paramString() + ", " + ise.getMessage());
                    return null;
                }

                /** Split succeeded. */
                spawnLoc.setOccupant(spawn);

                logger.log(Level.FINE, "Successful split attempt by " +
                    bact + " to " + spawnLoc);

                return spawnLoc;
            }
        }

        /** Couldn't find a free location to spawn to. */
        return null;
    }

    // Implement ClientSessionListener

    /**
     * {@inheritDoc}
     */
    public void disconnected(boolean graceful) {
        ClientSessionManager.remove(session.getName());

        logger.log(Level.FINE, "User " + session.getSessionId() + " (" +
            session.getName() + ") disconnected " +
            (graceful ? "gracefully" : "ungracefully") + ".");
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(byte[] message) {
        AppMessage appMsg;

        try {
            appMsg = Protocol.read(message);
        } catch (ProtocolException e) {
            logger.log(Level.SEVERE,
                String.format("ProtocolException while processing client" +
                    " message: %s\nmessage: %s", e.toString(),
                    Arrays.toString(message)));
            return;
        }

        // ChatRequest
        if (appMsg instanceof ChatRequest) {
            ChatRequest chatRequest = (ChatRequest)appMsg;
            String recipient = chatRequest.getRecipient();
            String msg = chatRequest.getMessage();
            int msgId = chatRequest.getId();
            
            logger.log(Level.FINER, "Received chat request #" + msgId +
                " from " + formattedName + " to " + recipient + ": \"" +
                msg + "\"");

            ClientSession recipSession = ClientSessionManager.get(recipient);

            if (recipSession == null) {
                session.send(Protocol.write(new NackResponse(msgId)));
            } else {
                recipSession.send(Protocol.write(new
                                      ChatMessage(session.getName(), msg)));
                
                session.send(Protocol.write(new AckResponse(msgId)));
            }
        }
        // MoveRequest
        else if (appMsg instanceof MoveRequest) {
            MoveRequest moveRequest = (MoveRequest)appMsg;
            World world = WorldManager.getWorld();

            /**
             * TODO - if bacteria are moved back to being java references on
             * locations, then this logic can be changed to avoid a call to
             * player.getBacterium(); instead, we can just look at the occupant
             * of moveRequest.getSource() and confirm that its the bacterium
             * named (by player-id and bact-id).
             */

            Player player = PlayerManager.getPlayer(playerId);
            Bacterium bact = player.getBacterium(moveRequest.getBacteriumId());
            Location src = world.getLocation(moveRequest.getSource());
            Coordinate srcCoord = src.getCoordinate();
            Location dest = world.getLocation(moveRequest.getDestination());
            Coordinate destCoord = dest.getCoordinate();

            try {
                /** Confirm that the bacterium is located at the source. */
                if (!bact.getCoordinate().equals(srcCoord))
                    throw new InvalidMoveException(bact, src, dest, "Source" +
                        " does not match current position of bacterium.");

                /** Make sure the bacterium object is in sync with the world. */
                if ((src.getOccupant() == null) ||
                    !bact.equals(src.getOccupant()))
                    throw new InvalidMoveException(bact, src, dest,
                        "Inconsistent data between bacterium and source" +
                        " (occupant: " + src.getOccupant() + ").");

                /** Confirm that the destination is empty. */
                if (dest.isOccupied())
                    throw new InvalidMoveException(bact, src, dest,
                        "Destination is not empty.");

                /**
                 * Try to move the bacterium, which will perform additional
                 * (internal) checks of its own before the move is allowed.
                 */
                bact.doMove(destCoord);
            } catch (InvalidMoveException ime) {
                logger.log(Level.WARNING, "Failed move attempt by " +
                    formattedName + ": " + ime.paramString() + ", " +
                    ime.getMessage());
                return;
            }

            /** Move must have succeeded; update Locations as well. */
            dest.setOccupant(bact);
            src.setOccupant(null);

            /** Feed the bacterium all the food in this location. */
            bact.addHealth(dest.emptyFood());

            logger.log(Level.FINE, "Successful move attempt by " + bact +
                " from " + src + " to " + dest);

            /** Check if the bacterium is ready and able to split. */
            Location spawnLoc = tryToSplit(bact);

            /**
             * Send updates for the locations involved in the move (and split,
             * if one happened) to any clients near them.
             */
            sendRegionUpdate(src);
            sendRegionUpdate(dest);

            if ((spawnLoc != null) &&
                (spawnLoc.getCoordinate().equals(src.getCoordinate()) == false))
                sendRegionUpdate(spawnLoc);

            /**
             * Send updates for all of the locations that just "came into view"
             * for the moving bacterium.
             */
            // TODO - prune locs that happen to be near some other bacterium of
            // this player?
            for (Coordinate newLocCoord :
                     getHorizonCoordinates(srcCoord, destCoord))
            {
                sendUpdate(world.getLocation(newLocCoord), playerId);
            }

            logger.log(Level.FINE, formattedName + " successfully moved " +
                bact + " from " + src + " to " + dest);
        }
        else {
            logger.log(Level.SEVERE,
                String.format("Received unsupported message from %s: %s",
                    formattedName, appMsg.getClass().getName()));
        }
    }

    /**
     * Inner class: PlayerUpdateTask
     */
    private static class PlayerUpdateTask
        implements ManagedObject, Serializable, Task
    {
        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;
        
        /** The player to be updated. */
        private long playerId;

        /**
         * Creates a new {@code PlayerUpdateTask} for the specified player.
         */
        public PlayerUpdateTask(long playerId) {
            this.playerId = playerId;
        }

        // implement Task

        public void run() throws Exception {
            Player player = PlayerManager.getPlayer(playerId);
            ClientSession playerSession =
                ClientSessionManager.get(player.getUsername());
            if (playerSession == null) return;  /** not logged in */

            Set<Location> locations = new HashSet<Location>();

            /** Iterate all bacteria of player... */
            for (Bacterium bact : PlayerManager.getPlayer(playerId)) {
                locations.addAll(getRegion(bact.getCoordinate()));
            }

            for (Location loc : locations) {
                LocationUpdate updateMsg = new LocationUpdate(loc);
                playerSession.send(Protocol.write(updateMsg));
            }
        }

        /**
         * Returns a collection of all locations within {@code NOTIF_RANGE} of
         * {@code coord}.
         */
        private Collection<Location> getRegion(Coordinate coord) {
            Collection<Location> locs = new LinkedList<Location>();
            World world = WorldManager.getWorld();

            int minX = Math.max(0, coord.getX() - NOTIF_RANGE);
            int minY = Math.max(0, coord.getY() - NOTIF_RANGE);
            int maxX = Math.min(world.getXDimension() - 1, coord.getX() + NOTIF_RANGE);
            int maxY = Math.min(world.getYDimension() - 1, coord.getY() + NOTIF_RANGE);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <=maxY; y++) {
                    locs.add(world.getLocation(new Coordinate(x, y)));
                }
            }

            return locs;
        }
    }
}
