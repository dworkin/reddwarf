/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;

/**
 * A GUI element for displaying information on {@link Location}s and {@link
 * Bacterium Bacteria}
 */
public class InfoBox extends JLabel {
    /** Used to print floating point numbers in the gui. */
    private final static NumberFormat decFormatter = new DecimalFormat(".##");

    /** The last location that was displayed. */
    private Location lastLocation = null;

    // Constructor

    /**
     * Creates a new {@code InfoBox}.
     */
    public InfoBox() {
        super("");
        setVerticalAlignment(SwingConstants.TOP);
    }

    /**
     * Displays info on {@code loc} and its occupant (if any), or clears the
     * display if {@code loc} is {@code null}.
     */
    public synchronized void display(Location loc) {
        lastLocation = loc;

        if (loc == null) {
            setText("");
        } else {
            String occupantDesc;

            if (loc.isOccupied()) {
                Bacterium occ = loc.getOccupant();

                occupantDesc = "yes<br>" +
                    "&nbsp;&nbsp;&nbsp;&nbsp;Player: " +
                    occ.getPlayerId() + "<br>" +
                    "&nbsp;&nbsp;&nbsp;&nbsp;Health: " +
                    decFormatter.format(occ.getHealth()) + "<br>" +
                    "&nbsp;&nbsp;&nbsp;&nbsp;Movement Points: " +
                    occ.getCurrentMovementPoints()  + "/" +
                    occ.getMaxMovementPoints() +
                    "&nbsp;&nbsp;&nbsp;(current/max)<br>";
            } else {
                occupantDesc = "no<br>";
            }

            String infoStr = "<html><u>Location Info</><br><br>" +
                "Coordinate: " + loc.getCoordinate() + "<br>" +
                "Food: " + decFormatter.format(loc.getFood()) + "<br>" +
                "Food Growth Rate: " +
                decFormatter.format(loc.getFoodGrowthRate()) + "<br>" +
                "Occupied? " + occupantDesc + "</html>";

            setText(infoStr);
        }
    }

    /**
     * Refreshes the info for the currently displayed Location.
     */
    public synchronized void refresh() {
        display(lastLocation);
    }

    /**
     * Refreshes the info for the currently displayed Location IFF that location
     * is at position {@code coord}.
     */
    public synchronized void updateIf(Location loc) {
        if ((lastLocation != null) &&
            (lastLocation.getCoordinate().equals(loc.getCoordinate())))
            display(loc);
    }

    /**
     * {@inheritDoc}
     */
    public void setEnabled(boolean enabled) {
        if (!enabled) setText("");
    }
}
