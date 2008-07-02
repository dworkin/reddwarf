/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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
