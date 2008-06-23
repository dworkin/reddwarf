/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;

import java.io.Serializable;

import java.util.Map;


/**
 * This represents a single sprite map. It's here mostly because we
 * can't put a {@code HashMap} (or similar) into a {@code
 * ManagedObject} directly, so we need some intermediate structure. It
 * also, as a utility, provides the side of the sprites (which are of
 * uniform height and width).
 */
public class SpriteMap implements ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * The standard namespace prefix.
     */
    public static final String NAME_PREFIX = "spritemap:";

    // the dimension of all the sprites in this map
    private int spriteSize;

    // the actual map from sprite id to image bytes
    private Map<Integer,byte[]> spriteMap;

    /**
     * Creates an instance of <code>SpriteMap</code>.
     *
     * @param spriteSize the dimension of each sprite
     * @param spriteMap the actual map from sprite id to sprite bytes
     */
    public SpriteMap(int spriteSize, Map<Integer,byte[]> spriteMap) {
        this.spriteSize = spriteSize;
        this.spriteMap = spriteMap;
    }

    /**
     * Returns the dimension of the sprites in this map.
     *
     * @return the sprite's size
     */
    public int getSpriteSize() {
        return spriteSize;
    }

    /**
     * Returns the map from sprite identifier to sprite image bytes.
     *
     * @return the sprite map
     */
    public Map<Integer,byte[]> getSpriteMap() {
        return spriteMap;
    }

}
