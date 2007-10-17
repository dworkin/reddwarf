/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

/**
 * Thrown when the client attempts a move that is not legal.  When the actual
 * locations (source and destination) of the move are known, as opposed to just
 * the coordinates, the exception should be created with the locations
 * themselves, but this is not always possible.
 */
public class InvalidMoveException extends Exception {
    /** The bacterium on which the move was attempted. */
    private final Bacterium bacterium;

    /** The source and destination of the original move attempt. */
    private final Location src, dest;
    private final Coordinate srcCoord, destCoord;

    /**
     * Creates a new {@code InvalidMoveException} from {@link Location}s, with
     * {@code null} as its detail message.
     */
    public InvalidMoveException(Bacterium bacterium, Location src,
        Location dest)
    {
        this(bacterium, src, dest, null);
    }

    /**
     * Creates a new {@code InvalidMoveException} from {@link Location}s, with
     * the specified detail message.
     */
    public InvalidMoveException(Bacterium bacterium, Location src,
        Location dest, String message)
    {
        super(message);
        this.bacterium = bacterium;
        this.src = src;
        this.dest = dest;
        this.srcCoord = src.getCoordinate();
        this.destCoord = dest.getCoordinate();
    }

    /**
     * Creates a new {@code InvalidMoveException} from {@link Coordinate}s, with
     * {@code null} as its detail message.
     */
    public InvalidMoveException(Bacterium bacterium, Coordinate srcCoord,
        Coordinate destCoord)
    {
        this(bacterium, srcCoord, destCoord, null);
    }

    /**
     * Creates a new {@code InvalidMoveException} from {@link Coordinate}s, with
     * the specified detail message.
     */
    public InvalidMoveException(Bacterium bacterium, Coordinate srcCoord,
        Coordinate destCoord, String message)
    {
        super(message);
        this.bacterium = bacterium;
        this.src = null;
        this.dest = null;
        this.srcCoord = srcCoord;
        this.destCoord = destCoord;
    }

    /**
     * Returns the {@link Bacterium} that was the target of the original move
     * attempt.
     */
    public Bacterium getBacterium() {
        return bacterium;
    }

    /**
     * Returns the destination {@link Location} of the original move attempt.
     */
    public Location getDest() {
        return dest;
    }

    /**
     * Returns the destination {@link Coordinate} of the original move attempt.
     */
    public Coordinate getDestCoord() {
        return destCoord;
    }

    /**
     * Returns the source {@link Location} of the original move attempt.
     */
    public Location getSrc() {
        return src;
    }

    /**
     * Returns the source {@link Coordinate} of the original move attempt.
     */
    public Coordinate getSrcCoord() {
        return srcCoord;
    }

    /**
     * Returns a descriptive string of the parameters that the exception was
     * created with.
     */
    public String paramString() {
        return "bacterium = " + bacterium +
            ", source = " + (src == null ? srcCoord : src) +
            ", destination = " + (dest == null ? destCoord : dest);
    }
}
