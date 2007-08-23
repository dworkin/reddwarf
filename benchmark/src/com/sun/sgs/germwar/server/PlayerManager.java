/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.util.PrefixHashMap;

import com.sun.sgs.germwar.server.ManagedLong;
import com.sun.sgs.germwar.server.Player;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.Location;
import com.sun.sgs.germwar.shared.impl.BacteriumImpl;

/**
 * Provides access to {@link Player} objects, which are stored in the data
 * store.
 */
public class PlayerManager {
    /** The prefix used in all data store bindings for {@link Player} objects. */
    private static final String DATA_BINDING_PREFIX = "player_";

    /** The data store binding for the map used to look up players by name. */
    private static final String MAP_DATA_BINDING = "playermap";

    /** Data binding for the master ID field. */
    private static final String MASTER_ID_BINDING = "PlayerManagerId";

    /**
     * Creates a new {@code PlayerManager}.
     */
    private PlayerManager() {
        // empty
    }

    /**
     * Creates a new player with the specified user name.
     *
     * @throws IllegalStateException if a player already exists for the
     *         specified user name
     * @return a {@link Player} object
     */
    public static Player createPlayer(String username) {
        DataManager dm = AppContext.getDataManager();
        Map<String,ManagedReference> userMap = getUserMap();

        if (userMap.containsKey(username)) {
            throw new IllegalStateException("Attempt to create player that" +
                " already exists: " + username);
        }

        long id = getNextId(dm);
        PlayerImpl player = new PlayerImpl(id, username);
        ManagedReference playerRef = dm.createReference(player);
        dm.setBinding(DATA_BINDING_PREFIX + id, player);
        userMap.put(username, playerRef);
        return player;
    }

    /**
     * Looks up and returns a player by ID.
     *
     * @return a {@code Player} object, or {@code null} if no such player exists
     */
    public static Player getPlayer(long id) {
        DataManager dm = AppContext.getDataManager();

        try {
            return dm.getBinding(DATA_BINDING_PREFIX + id, Player.class);
        } catch (NameNotBoundException nnbe) {
            return null;
        }
    }

    /**
     * Looks up and returns a player by user name.
     *
     * @return a {@code Player} object, or {@code null} if no such player exists
     */
    public static Player getPlayer(String username) {
        Map<String,ManagedReference> userMap = getUserMap();
        ManagedReference playerRef = userMap.get(username);

        if (playerRef == null) {
            return null;
        } else {
            return playerRef.get(Player.class);
        }
    }

    /**
     * Returns the username-to-ID map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String,ManagedReference> getUserMap() {
        DataManager dm = AppContext.getDataManager();
        try {
            return (Map<String,ManagedReference>)dm.getBinding(MAP_DATA_BINDING,
                Map.class);
        } catch (NameNotBoundException nnbe) {
            /** Must not exist yet - create it. */
            PrefixHashMap<String,ManagedReference> instance =
              new PrefixHashMap<String,ManagedReference>();
            dm.setBinding(MAP_DATA_BINDING, instance);
            return instance;
        }
    }

    /**
     * Returns the next player-ID to use.
     */
    private static long getNextId(DataManager dm) {
        ManagedLong nextPlayerId;
        try {
            nextPlayerId = dm.getBinding(MASTER_ID_BINDING,
                ManagedLong.class);
        } catch (NameNotBoundException nnbe) {
            nextPlayerId = new ManagedLong(1);
            dm.setBinding(MASTER_ID_BINDING, nextPlayerId);
        }

        dm.markForUpdate(nextPlayerId);

        long val = nextPlayerId.get();
        nextPlayerId.increment();
        return val;
    }

    /**
     * Returns an iterator over all players.
     */
    public static Iterator<Player> iterator() {
        return new Iterator<Player>() {
            private Iterator<ManagedReference> refIter =
                getUserMap().values().iterator();

            public boolean hasNext() {
                return refIter.hasNext();
            }

            public Player next() {
                ManagedReference ref = refIter.next();
                return ref.get(Player.class);
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Inner class: PlayerImpl
     * <p>
     * A simple implementation of {@link Player}.
     */
    private static class PlayerImpl implements ManagedObject, Player, Serializable {
        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;

        /** This player's unique id (which is constant across different sessions). */
        private long id;

        /** This player's login name. */
        private String username;

        /** The collection of all bacteria that belong to this player. */
        private ManagedReference bacteriaMapRef;

        /**
         * Creates a new {@code PlayerImpl}.
         */
        public PlayerImpl(long id, String username) {
            this.id = id;
            this.username = username;
            
            PrefixHashMap<Integer,ManagedReference> map =
                new PrefixHashMap<Integer,ManagedReference>();
            
            bacteriaMapRef = AppContext.getDataManager().createReference(map);
        }

        /**
         * Gets this player's {@link Map} of {@link Bacterium} objects out of
         * the data store.
         */
        @SuppressWarnings("unchecked")
        private Map<Integer,ManagedReference> getBacteriaMap() {
            return (Map<Integer,ManagedReference>)bacteriaMapRef.get(Map.class);
        }

        // implement Player

        /**
         * {@inheritDoc}
         */
        public int bacteriaCount() {
            return getBacteriaMap().size();
        }

        /**
         * {@inheritDoc}
         */
        public Bacterium createBacterium(Coordinate coord) {
            DataManager dm = AppContext.getDataManager();
            BacteriumImpl bact = new BacteriumRecord(id, coord);
            getBacteriaMap().put(bact.getId(), dm.createReference(bact));
            return bact;
        }

        /**
         * {@inheritDoc}
         */
        public Bacterium getBacterium(int id) {
            ManagedReference ref = getBacteriaMap().get(id);
            if (ref == null) return null;
            Bacterium bact = ref.get(Bacterium.class);
            bact.turnUpdate(TurnManager.getCurrentTurn());
            return bact;
        }

        /**
         * {@inheritDoc}
         */
        public long getId() { return id; }

        /**
         * {@inheritDoc}
         */
        public String getUsername() { return username; }

        /**
         * {@inheritDoc}
         */
        public void initialize() {
            /** Create their first bacterium in a random location. */
            World world = WorldManager.getWorld();
            Coordinate coord;

            do {
                int x = (int)Math.floor(Math.random()*world.getXDimension());
                int y = (int)Math.floor(Math.random()*world.getYDimension());
                coord = new Coordinate(x, y);
            } while (world.getLocation(coord).isOccupied());

            createBacterium(coord);
        }

        /**
         * {@inheritDoc}
         */
        public Iterator<Bacterium> iterator() {
            return new Iterator<Bacterium>() {
                private Map<Integer,ManagedReference> map = getBacteriaMap();
                private Iterator<Integer> mapIter = map.keySet().iterator();

                public boolean hasNext() {
                    return mapIter.hasNext();
                }

                public Bacterium next() {
                    ManagedReference ref = map.get(mapIter.next());
                    Bacterium bact = ref.get(Bacterium.class);
                    bact.turnUpdate(TurnManager.getCurrentTurn());
                    return bact;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        /**
         * {@inheritDoc}
         */
        public void removeBacterium(Bacterium bact) {
            Map<Integer,ManagedReference> map = getBacteriaMap();
            long key = bact.getId();

            if (map.containsKey(key)) {
                map.remove(key);
            } else {
                throw new IllegalArgumentException("Cannot remove bacterium " +
                    bact + ", not found for player: " + this);
            }
        }
    }

    /**
     * Inner class: BacteriumRecord
     * <p>
     * An implementation of {@link Bacterium} for use in the data store; it
     * exposes a constructor for creating new Bacterium entities in the data
     * data store, implements {@link ManagedObject}, and calls
     * {@link DataManager.markForUpdate} for itself when modified.
     */
    private static class BacteriumRecord extends BacteriumImpl
        implements ManagedObject, Serializable
    {
        /**
         * The prefix used in all data store bindings for players' bacterium ID
         * counters.
         */
        private static final String ID_BINDING_PREFIX = "pbact_";

        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;

        /**
         * Creates a new Bacterium in the data store and returns a {@code
         * BacteriumRecord} representing it.
         *
         * @throws IllegalArgumentException if the location at {@code startPos}
         *         is currently occupied
         */
        public BacteriumRecord(long playerId, Coordinate startPos) {
            super(getNextId(playerId).get(), playerId, startPos,
                TurnManager.getCurrentTurn());

            Location loc = WorldManager.getWorld().getLocation(startPos);

            if (loc.isOccupied()) {
                throw new IllegalArgumentException("Cannot create bacterium" +
                    " at " + startPos + "; loc is currently occupied by " +
                    loc.getOccupant());
            }

            loc.setOccupant(this);
            getNextId(playerId).increment();
        }

        /**
         * Creates a new Bacterium in the data store and returns a {@code
         * BacteriumRecord} representing it.
         *
         * @throws IllegalArgumentException if the location at {@code startPos}
         *         is currently occupied
         */
        public BacteriumRecord(long playerId, Coordinate startPos,
            float initialHealth)
        {
            super(getNextId(playerId).get(), playerId, startPos,
                TurnManager.getCurrentTurn(), initialHealth);

            Location loc = WorldManager.getWorld().getLocation(startPos);

            if (loc.isOccupied()) {
                throw new IllegalArgumentException("Cannot create bacterium" +
                    " at " + startPos + "; loc is currently occupied by " +
                    loc.getOccupant());
            }

            loc.setOccupant(this);
            getNextId(playerId).increment();
        }

        /**
         * Returns the next bacterium ID to use for a given player.
         */
        private static ManagedInteger getNextId(long playerId) {
            DataManager dm = AppContext.getDataManager();
            String binding = ID_BINDING_PREFIX + playerId;
            ManagedInteger nextBacteriumId;

            try {
                nextBacteriumId = dm.getBinding(binding, ManagedInteger.class);
                dm.markForUpdate(nextBacteriumId);
            } catch (NameNotBoundException nnbe) {
                nextBacteriumId = new ManagedInteger(1);
                dm.setBinding(binding, nextBacteriumId);
            }

            return nextBacteriumId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Bacterium createSpawn(long playerId, Coordinate pos,
            float initialHealth)
        {
            return new BacteriumRecord(playerId, pos, initialHealth);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void doMove(Coordinate newPos) throws InvalidMoveException {
            /** Call super.doMove first in case it throws an exception. */
            super.doMove(newPos);
            AppContext.getDataManager().markForUpdate(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bacterium splitUpdate(Coordinate spawnPos) {
            AppContext.getDataManager().markForUpdate(this);
            return super.splitUpdate(spawnPos);
        }

        /**
         * {@inheritDoc}
         */
        public void turnUpdate(long turnNo) {
            if (turnNo == getLastUpdated()) return;
            AppContext.getDataManager().markForUpdate(this);
            super.turnUpdate(turnNo);
        }
    }
}
