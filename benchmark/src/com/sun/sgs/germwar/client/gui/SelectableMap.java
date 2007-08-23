/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;

/**
 * An interface for GUI world maps that support selectable locations.
 */
public interface SelectableMap {
    /**
     * Returns the {@link Coordinate} of the {@link Location} represented by the
     * currently selected square, or {@code null} if no square is currently
     * selected.
     */
    Coordinate getSelected();

    /**
     * Assigns the focus of the map (typically by centering its view) to the
     * world location with {@link Coordinate} coord.
     */
    void setFocus(Coordinate coord);

    /**
     * Highlights the square that currently represents the {@link Location} at
     * {@link Coordinate} {@code coord} in the game world, first de-selecting
     * the currently selected square if one exists.  If {@code coord} is {@code
     * null}, then the currently selected square is de-selected and nothing is
     * selected.
     */
    void setSelected(Coordinate coord);

    /**
     * Updates a location of the map.
     */
    void update(Location loc);
}
