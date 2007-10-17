/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

/**
 * Thrown when a bacterium attempts a split that is not legal.
 */
public class InvalidSplitException extends Exception {
    /** The bacterium on which the move was attempted. */
    private final Bacterium bacterium;

    /** The position in which the spawned (split) bacterium was to be created. */
    private final Coordinate spawnPos;

    /**
     * Creates a new {@code InvalidSplitException} with {@code null} as its
     * detail message. 
     */
    public InvalidSplitException(Bacterium bacterium, Coordinate spawnPos) {
        this(bacterium, spawnPos, null);
    }

    /**
     * Creates a new {@code InvalidSplitException} with the specified detail
     * message.
    */
    public InvalidSplitException(Bacterium bacterium, Coordinate spawnPos,
        String message)
    {
        super(message);
        this.bacterium = bacterium;
        this.spawnPos = spawnPos;
    }

    /**
     * Returns the {@link Bacterium} that was the target of the original split
     * attempt.
     */
    public Bacterium getBacterium() {
        return bacterium;
    }

    /**
     * Returns the position of the original split attempt in which the spawned
     * (split) bacterium was to be created.
     */
    public Coordinate getSpawnPosition() {
        return spawnPos;
    }

    /**
     * Returns a descriptive string of the parameters that the exception was
     * created with.
     */
    public String paramString() {
        return "bacterium = " + bacterium + ", spawnPos = " + spawnPos;
    }
}
