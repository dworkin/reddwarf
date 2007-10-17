/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Other than the package, this code is copied directly from:
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4222316
 */

package com.sun.sgs.germwar.client.gui;

import java.awt.*;

/** Patch to java.awt.FlowLayout so that it includes component.getAlignmentY()
    in position calculation.  (Relevant change is the yCoord calculation
    between BEGIN PATCHED LINES and END PATCHED LINES.  Note this patch
    is not in package java.awt, so it uses methods where the original code
    accesses fields.  */
public class FlowLayout extends java.awt.FlowLayout {
    private static final long serialVersionUID = 1L;

    public FlowLayout() { super(); }
    public FlowLayout(int align) { super(align); }
    public FlowLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    private void moveComponents(Container target, int x, int y, int width, int height,
        int rowStart, int rowEnd, boolean ltr) {
        synchronized (target.getTreeLock()) {
            switch (getAlignment()) {
            case LEFT:
                x += ltr ? 0 : width;
                break;
            case CENTER:
                x += width / 2;
                break;
            case RIGHT:
                x += ltr ? width : 0;
                break;
            case LEADING:
                break;
            case TRAILING:
                x += width;
                break;
            }
            for (int i = rowStart ; i < rowEnd ; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    // BEGIN PATCHED LINES
                    int yCoord = y + (int) ((height - m.getHeight()) * m.getAlignmentY());
                    if (ltr) {
                        m.setLocation(x, yCoord);
                    } else {
                        m.setLocation(target.getWidth() - x - m.getWidth(), yCoord);
                    }
                    // END PATCHED LINES
                    x += m.getWidth() + getHgap();
                }
            }
        }
    }

    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxwidth = target.getWidth() - (insets.left + insets.right + getHgap()*2);
            int nmembers = target.getComponentCount();
            int x = 0, y = insets.top + getVgap();
            int rowh = 0, start = 0;

            boolean ltr = target.getComponentOrientation().isLeftToRight();

            for (int i = 0 ; i < nmembers ; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    m.setSize(d.width, d.height);

                    if ((x == 0) || ((x + d.width) <= maxwidth)) {
                        if (x > 0) {
                            x += getHgap();
                        }
                        x += d.width;
                        rowh = Math.max(rowh, d.height);
                    } else {
                        moveComponents(target, insets.left + getHgap(), y, maxwidth - x, rowh, start, i, ltr);
                        x = d.width;
                        y += getVgap() + rowh;
                        rowh = d.height;
                        start = i;
                    }
                }
            }
            moveComponents(target, insets.left + getHgap(), y, maxwidth - x, rowh, start, nmembers, ltr);
        }
    }
}
