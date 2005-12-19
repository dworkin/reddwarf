/*
 * COPYRIGHT 1995 BY: MASSACHUSETTS INSTITUTE OF TECHNOLOGY (MIT), INRIA
 * 
 * This W3C software is being provided by the copyright holders under the
 * following license. By obtaining, using and/or copying this software, you
 * agree that you have read, understood, and will comply with the following
 * terms and conditions:
 * 
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose and without fee or royalty is hereby granted,
 * provided that the full text of this NOTICE appears on ALL copies of the
 * software and documentation or portions thereof, including modifications,
 * that you make.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS MAKE NO
 * REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT
 * NOT LIMITATION, COPYRIGHT HOLDERS MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE
 * SOFTWARE OR DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS,
 * COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. COPYRIGHT HOLDERS WILL BEAR NO
 * LIABILITY FOR ANY USE OF THIS SOFTWARE OR DOCUMENTATION.
 * 
 * The name and trademarks of copyright holders may NOT be used in advertising
 * or publicity pertaining to the software without specific, written prior
 * permission. Title to copyright in this software and any associated
 * documentation will at all times remain with copyright holders.
 */

/*
 * LrmpLossTable.java - table of loss packets.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 30 May 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;

// import inria.util.*;

/**
 * table of loss packets.
 */
final class LrmpLossTable {
    protected LrmpLossEvent[] tab;
    protected LrmpPacket packet = null;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @param initialSize
     *
     * @see
     */
    public LrmpLossTable(int initialSize) {
        tab = new LrmpLossEvent[initialSize];

        clear();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void clear() {
        for (int i = 0; i < tab.length; i++) {
            tab[i] = null;      /* mark free */
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int size() {
        int count = 0;

        for (int i = 0; i < tab.length; i++) {
            if (tab[i] != null) {
                count++;
            } 
        }

        return count;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    public void add(LrmpLossEvent ev) {
        int i = 0;

        for (; i < tab.length; i++) {
            if (tab[i] == null) {
                tab[i] = ev;

                return;
            }
        }

        tab = expand(tab);
        tab[i] = ev;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    public void remove(LrmpLossEvent ev) {
        for (int i = 0; i < tab.length; i++) {
            if (tab[i] == ev) {
                tab[i] = null;

                break;
            }
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @return
     *
     * @see
     */
    public boolean contains(LrmpLossEvent ev) {
        for (int i = 0; i < tab.length; i++) {
            if (tab[i] == ev) {
                return true;
            } 
        }

        return false;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param tab
     *
     * @return
     *
     * @see
     */
    private static final LrmpLossEvent[] expand(LrmpLossEvent[] tab) {
        LrmpLossEvent[] newtab;

        if (tab != null) {
            newtab = new LrmpLossEvent[tab.length + 4];

            for (int i = 0; i < tab.length; i++) {
                newtab[i] = tab[i];
            }
        } else {
            newtab = new LrmpLossEvent[4];
        }

        return newtab;
    }

}

