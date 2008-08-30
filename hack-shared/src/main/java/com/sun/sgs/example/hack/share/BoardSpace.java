/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CreatureInfo.Creature;
import com.sun.sgs.example.hack.share.RoomInfo.FloorType;
import com.sun.sgs.example.hack.share.ItemInfo.ItemType;
import com.sun.sgs.example.hack.share.ItemInfo.Item;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;


/**
 * This represents a single space on a level. It encodes location and
 * identifiers at that location. It is used primarily for shipping data
 * between the client and server, and between specific elements of the
 * server.
 */
public class BoardSpace implements Serializable {

    private static final long serialVersionUID = 1;

    // the location
    protected final int x;
    protected final int y;

    protected FloorType floorType;

    protected Creature creature;

    protected Item item;

    /**
     * Creates an instance of <code>BoardSpace</code>.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param identifiers the identifier stack
     *
     * @throws IllegalArgumentException if {@code floorType} is {@code null}
     */
    public BoardSpace(int x, int y, FloorType floorType) {
	if (floorType == null)
	    throw new IllegalArgumentException("floor type cannot be null");
        this.x = x;
        this.y = y;
        this.floorType = floorType;
	creature = null;
	item = null;
    }

    /**
     * Returns the x-coordinate for this space.
     *
     * @return the x-coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the y-coordinate for this space.
     *
     * @return the y-coordinate
     */
    public int getY() {
        return y;
    }


    public FloorType getFloorType() {
	return floorType;
    }
    
    public void setItem(Item item) {
	this.item = item;
    }

    public Item getItem() {
	return item;
    }

    public Creature getCreature() {
	return creature;
    }
    
    public void setCreature(Creature creature) {
	this.creature = creature;
    }

    public String toString() {
	return String.format("(%d,%d)[%s] = <%s,%s>", x, y, floorType,
			     item, creature);
    }

}
