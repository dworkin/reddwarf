/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Allows buttons to specify a preferred size in situations where it would
 * otherwise be ignored (for example, in a GridLayout).
 */
public class ButtonWrapper extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Creates a new {@code ButtonWrapper}. */
    public ButtonWrapper(JButton button, int size) {
        this(button, size, size);
    }

    /** Creates a new {@code ButtonWrapper}. */
    public ButtonWrapper(JButton button, int width, int height) {
        setLayout(new GridBagLayout());
        button.setPreferredSize(new Dimension(width, height));
        GridBagConstraints cons = new java.awt.GridBagConstraints();
        add(button, cons);
    }
}
