/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import javax.swing.ImageIcon;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * Implementation of a JButton with an arrow as the icon (implemented because of
 * problems with sizing objects of type javax.swing.plaf.basic.BasicArrowButton)
 */
public class ArrowButton extends JButton {
    private static final long serialVersionUID = 1L;

    public final static String IMG_DIR = MainFrame.IMAGE_ROOT;

    private final static Icon upArrow =
        new ImageIcon(IMG_DIR + "/arrow_up.gif");

    private final static Icon downArrow =
        new ImageIcon(IMG_DIR + "/arrow_down.gif");

    private final static Icon leftArrow =
        new ImageIcon(IMG_DIR + "/arrow_left.gif");

    private final static Icon rightArrow =
        new ImageIcon(IMG_DIR + "/arrow_right.gif");

    /* Creates a new {@code ArrowButton} with a text arrow. */
    private ArrowButton(String s) {
        super(s);
    }

    /* Creates a new {@code ArrowButton} with an icon arrow. */
    private ArrowButton(Icon icon) {
        super(icon);
    }

    /** Returns a new {@code ArrowButton}. */
    public static ArrowButton newInstance(int direction) {
        Icon icon;
        String altString;
        
        switch (direction) {
        case NORTH:
            icon = upArrow;
            altString = "^";
            break;
        case SOUTH:
            icon = downArrow;
            altString = "v";
            break;
        case EAST:
            icon = rightArrow;
            altString = ">";
            break;
        case WEST:
            icon = leftArrow;
            altString = "<";
            break;
        default:
            throw new IllegalArgumentException("Bad direction: " + direction);
        }
        
        if (icon != null) {
            return new ArrowButton(icon);
        } else {
            return new ArrowButton(altString);
        }
    }
}
