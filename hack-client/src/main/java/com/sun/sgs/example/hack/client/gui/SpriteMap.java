/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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

/**
 * This represents a single sprite map. This provides the size of the
 * sprites (which are of uniform height and width).
 */
public class SpriteMap {

    private static final long serialVersionUID = 1;

    private static final Logger logger = 
	Logger.getLogger(SpriteMap.class.getName());

    public static final String SPRITE_MAP_FILE_PROPERTY = 
	SpriteMap.class.getName() + ".sprite.map";

    public static final String SPRITE_SIZE_PROPERTY = 
	SpriteMap.class.getName() + ".sprite.size";

    /**
     * The standard namespace prefix.
     */
    public static final String NAME_PREFIX = "spritemap:";

    /**
     * The height and width dimension of all the sprites in this map
     */
    private final int spriteSize;

    /**
     * The map from sprite id to the image that represents it
     */
    private final Map<Integer,BufferedImage> spriteMap;

    /**
     * Creates an instance of <code>SpriteMap</code>.
     *
     * @param spriteSize the dimension of each sprite
     * @param spriteMap the file that cotains the sprite data
     *
     * @throws IllegalStateException if {@code
     *         SPRITE_MAP_FILE_PROPERTY} or {@code
     *         SPRITE_SIZE_PROPERTY} are not defined or are
     *         incorrectly defined.
     */
    public SpriteMap() {

	Properties properties = System.getProperties();

	String spriteMapFile = properties.getProperty(SPRITE_MAP_FILE_PROPERTY);
	if (spriteMapFile == null) {
	    logger.severe("Sprite Map File is undefined!  No graphics!");
	    throw new IllegalStateException("Must define a sprite map using " +
					    SPRITE_MAP_FILE_PROPERTY + 
					    " property");
	}
	String spriteSizeStr = properties.getProperty(SPRITE_SIZE_PROPERTY);
	if (spriteSizeStr == null)
	    logger.severe("Sprite size is undefined, sprites undisplayable");

	spriteSize = (spriteSizeStr == null)
	    ? 0 : Integer.parseInt(spriteSizeStr);
	
	if (spriteSize <= 0) {
	    throw new IllegalStateException("Must define a sprite size using " +
					    SPRITE_SIZE_PROPERTY + 
					    " property");
	}

        this.spriteMap = new HashMap<Integer,BufferedImage>();

	try {
	    loadSprites(spriteMapFile);
	} catch (IOException ioe) {
	    throw new IllegalStateException("Sprite map incorrectly configured",
					    ioe);
	}
    }

    /**
     * Loads sprite images from the single image pointed to at the
     * provided file using the {@code spriteSize} as a guide for where
     * the images are embedded.
     *
     * @param spriteMapFile an image file that contains all the
     *        sprites
     */
    private void loadSprites(String spriteMapFile) throws IOException {
        logger.finer("Trying to read images from: " + spriteMapFile);

        // open the file into a single image
        BufferedImage image = ImageIO.read(new File(spriteMapFile));

        // now split up the image into its component sprites

        // REMINDER: In the event that we don't control the image
        //           data, this method should check that the images
        //           are the right dimension
        int identifier = 1;
        for (int y = 0; y < image.getHeight() / spriteSize; y++) {
            for (int x = 0; x < image.getWidth() / spriteSize; x++) {
                BufferedImage sprite =
                    image.getSubimage(x * spriteSize, y * spriteSize,
                                      spriteSize, spriteSize);
                spriteMap.put(identifier++, sprite);
            }
        }

        logger.fine("loaded " + identifier + "sprites");
    }

    /**
     * Returns the number of sprites held in this sprite map
     *
     * @return the number of sprites
     */
    public int getNumSprites() {
	return spriteMap.size();
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
     * Returns the sprite image referenced by the provided Id
     *
     * @param a sprite Id
     *
     * @return the image for the provided Id, or {@code null} if no
     *         image exists for the Id
     */
    public BufferedImage getSprite(int spriteId) {
	return spriteMap.get(spriteId);
    }

}
