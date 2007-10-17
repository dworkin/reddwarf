/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.germwar.server.TurnManager;
import com.sun.sgs.germwar.server.World;
import com.sun.sgs.germwar.server.WorldManager;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;
import com.sun.sgs.germwar.shared.impl.LocationImpl;

/**
 * A World implementation that has generally typical (non-extreme) values.
 */
public class NormalWorld implements Serializable, World {
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** Default world size dimensions. */
    public static final int DEFAULT_X_SIZE = 1000;
    public static final int DEFAULT_Y_SIZE = 1000;
    
    /** The length of an edge of the squares used to sub-divide the world. */
    protected static final int DB_REGION_SIZE = 3;

    /** The size of the world. */
    protected int worldSize_x, worldSize_y;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(NormalWorld.class.getName());
    
    /**
     * Creates a World without modifying any variables.
     */
    public NormalWorld() {
        // empty
    }

    /*
     * Creates all of the locations for the world and puts them into the data
     * store.
     */
    protected void createLocations() {
        /**
         * Divide the world into squares of locations; larger squares mean fewer
         * data store accesses but greater chance of contention between
         * different entities.
         */
        DataManager dm = AppContext.getDataManager();

        for (int x=0; x < worldSize_x; x += DB_REGION_SIZE) {
            for (int y=0; y < worldSize_y; y += DB_REGION_SIZE) {
                SquareRegion region = new SquareRegion(new Coordinate(x, y),
                        DB_REGION_SIZE);
                
                dm.setBinding(getSquareRegionBinding(new Coordinate(x, y)),
                    region);
            }
        }
    }

    /**
     * Fetches the {@code SquareRegion} responsible for 
     */
    protected SquareRegion getSquareRegion(Coordinate coord) {
        DataManager dm = AppContext.getDataManager();
        String binding = getSquareRegionBinding(coord);
        SquareRegion region;

        try {
            region = dm.getBinding(binding, SquareRegion.class);
        } catch (NameNotBoundException nnbe) {
            throw new IllegalStateException("NormalWorld.getSquareRegion()" +
                " called before initialization; SquareRegion object" + binding +
                " was not found in data store.", nnbe);
        }

        return region;
    }

    /**
     * Creates and returns an object binding for the SquareRegion responsible
     * for the {@link Location} at {@link Coordinate} {@code coord}.
     */
    protected String getSquareRegionBinding(Coordinate coord) {
        int x = coord.getX();
        int y = coord.getY();

        if (!validCoordinate(coord)) {
            throw new IllegalArgumentException(coord + " is outside the" +
                " boundaries of the game world (dimensions: " + worldSize_x +
                ","  + worldSize_y + ").");
        }

        // TODO - change these to managedReferences?

        StringBuilder sb = new StringBuilder();
        sb.append("loc_").append(x / DB_REGION_SIZE).append("_");
        sb.append(y / DB_REGION_SIZE);
        return sb.toString();
    }

    // implement World interface

    /**
     * @{inheritDoc}
     */
    public void initialize(Properties props) {
        String xArg = props.getProperty("com.sun.sgs.germwar.world.x_dim");
        String yArg = props.getProperty("com.sun.sgs.germwar.world.y_dim");
        
        if (xArg == null) {
            logger.log(Level.WARNING, "Property not specified: " +
                "com.sun.sgs.germwar.world.x_dim, reverting to default (" +
                DEFAULT_X_SIZE + ").");
            
            worldSize_x = DEFAULT_X_SIZE;
        } else {
            worldSize_x = Integer.valueOf(xArg);
        }
        
        if (yArg == null) {
            logger.log(Level.WARNING, "Property not specified: " +
                "com.sun.sgs.germwar.world.y_dim, reverting to default (" +
                DEFAULT_Y_SIZE + ").");
            
            worldSize_y = DEFAULT_Y_SIZE;
        } else {
            worldSize_y = Integer.valueOf(yArg);
        }

        createLocations();

        logger.log(Level.CONFIG, "Initialized world to: " + worldSize_x + "," +
            worldSize_y);
    }

    /**
     * @{inheritDoc}
     */
    public Location getLocation(Coordinate coordinate) {
        SquareRegion region = getSquareRegion(coordinate);

        logger.log(Level.FINER, "Fetched region {0} for coordinate {1}.",
            new Object[] { region, coordinate });
        
        return region.getLocation(coordinate);
    }

    /**
     * @{inheritDoc}
     */
    public int getXDimension() {
        return worldSize_x;
    }
    
    /**
     * @{inheritDoc}
     */
    public int getYDimension() {
        return worldSize_y;
    }

    /**
     * {@inheritDoc}
     */
    public void markForUpdate(Location loc) {
        SquareRegion region = getSquareRegion(loc.getCoordinate());
        AppContext.getDataManager().markForUpdate(region);
    }

    /**
     * {@inheritDoc}
     */
    public boolean validCoordinate(Coordinate coordinate) {
        int x = coordinate.getX();
        int y = coordinate.getY();
        return (x >= 0) && (x < worldSize_x) && (y >= 0) && (y < worldSize_y);
    }

    /**
     * Inner class: SquareRegion
     * <p>
     * Manages a contiguous square region of locations (can be more efficient
     * than storing each Location in the data store individuall).
     */
    private static class SquareRegion implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * Coordinate of the upper-left corner of the region (this is always the
         * coordinate with the smallest coordiate amongst all coordinates that
         * lie within this region).
         */
        private Coordinate anchor;
    
        /**
         * The length of each side of this region.
         */
        private int size;
    
        /**
         * The locations inside this region, indexed by offsets from this
         * region's corner coordinate.
         */
        private Location[][] locations;
    
        /*
         * Creates a new SquareRegion.
         */
        public SquareRegion(Coordinate anchor, int size) {
            this.anchor = anchor;
            this.size = size;
            this.locations = new Location[size][size];
        
            for (int x=0; x < size; x++) {
                for (int y=0; y < size; y++) {
                    locations[x][y] =
                        new LocationRecord(new Coordinate(x + anchor.getX(),
                                               y + anchor.getY()));
                }
            }
        }
    
        /**
         * Returns the specified location, or throws an IllegalArgumentException
         * if {@code coord} does not lie within this region.
         */
        public Location getLocation(Coordinate coord) {
            if ((coord.getX() < anchor.getX()) ||
                (coord.getX() >= (anchor.getX() + size)) ||
                (coord.getY() < anchor.getY()) ||
                (coord.getY() >= (anchor.getY() + size))) {
                throw new IllegalArgumentException("SquareRegion.getLocation(" +
                    coord + ") called on " + this);
            }

            Location loc = locations[coord.getX() - anchor.getX()]
                [coord.getY() - anchor.getY()];

            /**
             * Whenever a location is pulled from the database, it must be given
             * a chance to update in case its internal state is stale.
             */
            loc.update(TurnManager.getCurrentTurn());
            return loc;
        }
    
        /** Returns the coordinate of this square's upper-left corner. */
        public Coordinate getAnchor() { return anchor; }
    
        /**
         * @{inheritDoc}
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SquareRegion ").append(anchor);
            sb.append(" {size=").append(size).append("}");
            return sb.toString();
        }
    }

    /**
     * Inner class: LocationRecord
     * <p>
     * An implementation of {@link Location} for use in the data store; it uses
     * a {@link ManagedReference} to its occupant instead of a Java reference,
     * as in {@link com.sun.sgs.germwar.shared.impl.LocationImpl}.
     */
    protected static class LocationRecord extends LocationImpl
        implements Serializable
    {
        /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;

        /**
         * {@link ManagedReference} to the bacterium currently occupying this
         * location, if any ({@code null} if there is no occupant).
         */
        private ManagedReference occupantRef = null;

        /*
         * Creates a new location from the specified coordinates.
         */
        public LocationRecord(Coordinate coord) {
            super(coord, genInitialFood(), genFoodGrowthRate());
        }

        /** Generates a random value for the initial food in a location. */
        private static float genInitialFood() {
            return 20*(float)Math.random();
        }

        /** Generates a random value for the food growth rate in a location. */
        private static float genFoodGrowthRate() {
            double gauss = (new Random()).nextGaussian();
            float val = (float)(gauss + 1);
            if (val < 0)
                return 0f;  /** This will be ~13.6% of the cases. */
            else
                return val;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bacterium getOccupant() {
            if (occupantRef == null) {
                return null;
            } else {
                Bacterium occ = occupantRef.get(Bacterium.class);
                occ.turnUpdate(TurnManager.getCurrentTurn());
                return occ;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isOccupied() {
            return (occupantRef != null);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bacterium setOccupant(Bacterium bacterium) {
            Bacterium old = getOccupant();

            DataManager dm = AppContext.getDataManager();
            WorldManager.getWorld().markForUpdate(this);

            if (bacterium == null)
                occupantRef = null;
            else
                occupantRef = dm.createReference(bacterium);

            return old;
        }
    }
}
