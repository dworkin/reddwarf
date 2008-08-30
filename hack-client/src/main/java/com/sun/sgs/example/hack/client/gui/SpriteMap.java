/*
 * This work is hereby released into the Public Domain.  To view a
 * copy of the public domain dedication, visit
 * http://creativecommons.org/licenses/publicdomain/ or send a letter
 * to Creative Commons, 171 Second Street, Suite 300, San Francisco,
 * California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.gui;

import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import java.util.logging.Logger;

import javax.imageio.ImageIO;

import  com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import  com.sun.sgs.example.hack.share.ItemInfo.ItemType;
import  com.sun.sgs.example.hack.share.RoomInfo.FloorType;

/**
 * This represents a single sprite map. This provides the size of the
 * sprites (which are of uniform height and width).
 */
public interface SpriteMap {

    /**
     * Specifies a Java class which contains the correct logic to
     * parse the image file specified by {@code
     * SPRITE_MAP_FILE_PROPERTY}
     */
    public static final String SPRITE_MAP_CLASS_PROPERTY =
	SpriteMap.class.getName() + ".sprite.class";

    /**
     * Specifies the name of a file that contains the sprite map to be
     * used.  This file will be loaded by the Java class specified by
     * {@code SPRITE_MAP_CLASS_PROPERTY}.
     */
    public static final String SPRITE_MAP_FILE_PROPERTY = 
	SpriteMap.class.getName() + ".sprite.map";

    public static final String SPRITE_SIZE_PROPERTY = 
	SpriteMap.class.getName() + ".sprite.size";

    /**
     * Returns the number of sprites held in this sprite map
     *
     * @return the number of sprites
     */
    public int getNumSprites();

    /**
     * Returns the dimension of the sprites in this map.
     *
     * @return the sprite's size
     */
    public int getSpriteSize();

    /**
     * Returns the sprite image for the provided floor type
     *
     * @param floor the type of floor that is to be displayed
     *
     * @return the image for the provided Id, or {@code null} if no
     *         image exists for the Id
     */
    public BufferedImage getFloorSprite(FloorType floor);

    /**
     * Returns the sprite image for the provided creature type
     *
     * @param floor the type of creature that is to be displayed
     *
     * @return the image for the provided Id, or {@code null} if no
     *         image exists for the Id
     */
    public BufferedImage getCreatureSprite(CreatureType creature);

    /**
     * Returns the sprite image for the provided item type
     *
     * @param floor the type of item that is to be displayed
     *
     * @return the image for the provided Id, or {@code null} if no
     *         image exists for the Id
     */
    public BufferedImage getItemSprite(ItemType item);

}
