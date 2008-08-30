/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CreatureInfo.Creature;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.ItemInfo.Item;
import com.sun.sgs.example.hack.share.ItemInfo.ItemType;
import com.sun.sgs.example.hack.share.RoomInfo.FloorType;

public class ClientSideBoard implements Board {

    private final ClientBoardSpace[][] board;

    private final int width;
    
    private final int height;

    public ClientSideBoard(int width, int height) {
	this.width = width;
	this.height = height;
	this.board = new ClientBoardSpace[width][height];

	for (int i = 0; i < width; ++i) {
	    for (int j = 0; j < height; ++j) {
		board[i][j] = new ClientBoardSpace(i, j, FloorType.UNKNOWN);
	    }
	}
    }
    
    /**
     * {@inheritDoc}
     */
    public ClientBoardSpace getAt(int x, int y) {
	return board[x][y];
    }

    /**
     * {@inheritDoc}
     */
    public int getHeight() {
	return height;
    }

    /**
     * {@inheritDoc}
     */
    public int getWidth() {
	return width;
    }


    public boolean isActive(int x, int y) {
	return board[x][y].isActive();
    }

    public boolean isVisible(int x, int y) {
	return board[x][y].isVisible();
    }

    public void setAt(int x, int y, FloorType floorType) {
	ClientBoardSpace s = getAt(x, y);
	s.setFloor(floorType);
    }

    public void setAt(int x, int y, Item item) {
	ClientBoardSpace s = getAt(x, y);
	s.setItem(item);
    }

    public void setAt(int x, int y, Creature creature) {
	ClientBoardSpace s = getAt(x, y);
	s.setCreature(creature);
    }

    
    public final static class ClientBoardSpace extends BoardSpace {

	private boolean isActive;

	private boolean isVisible;

	public ClientBoardSpace(int x, int y, FloorType floorType) {
	    this(x, y, floorType, false, false);
	}

	public ClientBoardSpace(int x, int y, FloorType floorType,
				boolean isActive, boolean isVisible) {
	    super(x, y, floorType);
	    this.isActive = isActive;
	    this.isVisible = isVisible;
	}

	public void setFloor(FloorType floorType) {
	    this.floorType = floorType;
	}
	
	public boolean isActive() {
	    return isActive;
	}

	public boolean isVisible() {
	    return isVisible;
	}

	public void setActive(boolean isActive) {
	    this.isActive = isActive;
	}

	public void setVisible(boolean isVisible) {
	    this.isVisible = isVisible;
	}
	
    }

}