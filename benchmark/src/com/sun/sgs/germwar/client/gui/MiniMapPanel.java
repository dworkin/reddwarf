/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A GermWar client GUI component that shows a small, zoomed-out view of the
 * entire game world.
 *
 * TODO - currently unimplemented
 */
public class MiniMapPanel extends JPanel {
    // Constructor

    /**
     * Creates a new {@code MiniMapPanel}.
     */
    public MiniMapPanel() {
        super();
        add(new JLabel("This is the MiniMap!  (todo)"));  // placeholder
    }

    /**
     * {@inheritDoc}
     * <p>
     * En/disable all widgets on this panel when its en/disabled.
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        
        // todo - grey out map or something?
    }

}
