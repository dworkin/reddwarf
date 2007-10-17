/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.Map;

import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;
import com.sun.sgs.germwar.shared.impl.BacteriumImpl;
import com.sun.sgs.germwar.shared.impl.LocationImpl;

/**
 * Sent by the server to update a client on a {@link Location}.
 */
public class LocationUpdate implements AppMessage {
    private Location loc = null;

    /**
     * Creates a new {@code LocationUpdate} for {@link Location} {@code loc}.
     */
    public LocationUpdate(Location loc) {
        this.loc = loc;

        Bacterium occ = loc.getOccupant();
        if ((occ != null) && (occ.getLastUpdated() != loc.getLastUpdated()))
            throw new IllegalArgumentException("Location (" +
                loc.getLastUpdated() + ") and occupant (" +
                occ.getLastUpdated() + ") are not in sync w.r.t." +
                " getLastUpdated()");
    }

    /**
     * Creates a new {@code LocationUpdate} by reading fields out of {@code
     * buf}.
     */
    public static LocationUpdate fromBytes(ByteBuffer buf) throws ProtocolException {
        Coordinate coord = new Coordinate(buf.getInt(), buf.getInt());
        float foodAmount = buf.getFloat();
        float foodGrowthRate = buf.getFloat();
        long lastUpdated = buf.getLong();

        Location loc = new LocationImpl(coord, foodAmount, foodGrowthRate,
            lastUpdated);

        int bacteriaId = buf.getInt();

        if (bacteriaId == -1) {
            // occupant is null
        } else {
            long playerId = buf.getLong();
            float health = buf.getFloat();
            int movementPoints = buf.getInt();
            loc.setOccupant(new BacteriumImpl(bacteriaId, playerId, coord.copy(),
                                lastUpdated, health, movementPoints));
        }

        return new LocationUpdate(loc);
    }

    /**
     * Returns the {@link Location} that this message is an update for.
     */
    public Location getLocation() {
        return loc;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.LOCATION_UPDATE;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        Coordinate coord = loc.getCoordinate();
        buf.putInt(coord.getX());
        buf.putInt(coord.getY());
        buf.putFloat(loc.getFood());
        buf.putFloat(loc.getFoodGrowthRate());
        buf.putLong(loc.getLastUpdated());

        Bacterium bact = loc.getOccupant();

        if (bact == null) {
            buf.putInt(-1);
        } else {
            buf.putInt(bact.getId());
            buf.putLong(bact.getPlayerId());
            buf.putFloat(bact.getHealth());
            buf.putInt(bact.getCurrentMovementPoints());
        }
        
        return buf;
    }
}
