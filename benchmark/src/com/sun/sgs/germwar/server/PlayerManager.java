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
import com.sun.sgs.app.util.ScalableHashMap;

import com.sun.sgs.germwar.server.ManagedLong;
import com.sun.sgs.germwar.server.Player;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.InvalidSplitException;
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
            ScalableHashMap<String,ManagedReference> instance =
              new ScalableHashMap<String,ManagedReference>();
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
     * Returns an iterator over ManagedReferences to all players.  This should
     * only be used over iterator() when the players will not be accessed
     * immediately but instead will be passed to another task in some way.
     */
    public static Iterator<ManagedReference> refIterator() {
        return new Iterator<ManagedReference>() {
            private Iterator<ManagedReference> refIter =
                getUserMap().values().iterator();

            public boolean hasNext() {
                return refIter.hasNext();
            }

            public ManagedReference next() {
                return refIter.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Returns the current number of players (this includes players with no
     * bacteria left because they have all died).
     */
    public static long playerCount() {
        try {
            ManagedLong nextBacteriumId = AppContext.getDataManager()
                .getBinding(MASTER_ID_BINDING, ManagedLong.class);
            return nextBacteriumId.get() - 1;
        } catch (NameNotBoundException nnbe) {
            return 0;
        }
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
         * Count of all bacteria belonging to this player (more efficient to
         * maintain this ourselves than to call size() on the bacteriaMapRef).
         */
        private int bacteriaCount;

        /**
         * Creates a new {@code PlayerImpl}.
         */
        public PlayerImpl(long id, String username) {
            this.id = id;
            this.username = username;
            
            ScalableHashMap<Integer,ManagedReference> map =
                new ScalableHashMap<Integer,ManagedReference>();

            bacteriaMapRef = AppContext.getDataManager().createReference(map);
            bacteriaCount = 0;
        }

        /**
         * Gets this player's {@link Map} of {@link Bacterium} objects out of
         * the data store.
         */
        @SuppressWarnings("unchecked")
        private Map<Integer,ManagedReference> getBacteriaMap() {
            return (Map<Integer,ManagedReference>)bacteriaMapRef.get(Map.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "PlayerImpl [id=" + id + ", username=" + username +
                ", bacteria=" + bacteriaCount() + "]";
        }

        // implement Player

        /**
         * {@inheritDoc}
         */
        public int bacteriaCount() {
            return bacteriaCount;
        }

        /**
         * {@inheritDoc}
         */
        public BacteriumRecord createBacterium(Coordinate coord) {
            DataManager dm = AppContext.getDataManager();
            BacteriumRecord bact = new BacteriumRecord(id, coord);
            getBacteriaMap().put(bact.getId(), dm.createReference(bact));
            bacteriaCount++;
            return bact;
        }

        /**
         * {@inheritDoc}
         */
        public BacteriumRecord createBacterium(Coordinate coord,
                                               float initialHealth) 
        {
            DataManager dm = AppContext.getDataManager();
            BacteriumRecord bact = new BacteriumRecord(id, coord, initialHealth);
            getBacteriaMap().put(bact.getId(), dm.createReference(bact));
            bacteriaCount++;
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
            Location loc;

            do {
                int x = (int)Math.floor(Math.random()*world.getXDimension());
                int y = (int)Math.floor(Math.random()*world.getYDimension());
                coord = new Coordinate(x, y);
                loc = world.getLocation(coord);
            } while (loc.isOccupied());

            BacteriumRecord bact = createBacterium(coord);

            /** Write this new bacterium into its location in the world. */
            loc.setOccupant(bact);
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
            int key = bact.getId();

            if (map.containsKey(key)) {
                map.remove(key);
                bacteriaCount--;
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
         */
        public BacteriumRecord(long playerId, Coordinate startPos) {
            super(getNextId(playerId), playerId, startPos,
                TurnManager.getCurrentTurn());
        }

        /**
         * Creates a new Bacterium in the data store and returns a {@code
         * BacteriumRecord} representing it.
         */
        public BacteriumRecord(long playerId, Coordinate startPos,
            float initialHealth)
        {
            super(getNextId(playerId), playerId, startPos,
                TurnManager.getCurrentTurn(), initialHealth);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addHealth(float mod) {
            if (mod != 0) AppContext.getDataManager().markForUpdate(this);
            super.addHealth(mod);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean doFight(Bacterium attacker) {
            if (super.doFight(attacker)) {
                AppContext.getDataManager().markForUpdate(this);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns the next bacterium ID to use for a given player.
         */
        private static int getNextId(long playerId) {
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

            int val = nextBacteriumId.get();
            nextBacteriumId.increment();
            return val;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Bacterium createSpawn(long playerId, Coordinate pos,
            float initialHealth)
        {
            return PlayerManager.getPlayer(playerId).createBacterium(pos,
                initialHealth);
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
        public Bacterium doSplit(Coordinate spawnPos)
            throws InvalidSplitException
        {
            /** Call super.doSplit first in case it throws an exception. */
            Bacterium ret = super.doSplit(spawnPos);
            AppContext.getDataManager().markForUpdate(this);
            return ret;
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
