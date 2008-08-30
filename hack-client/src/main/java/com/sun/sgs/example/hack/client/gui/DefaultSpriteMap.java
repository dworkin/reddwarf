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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import java.util.logging.Logger;

import javax.imageio.ImageIO;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.RoomInfo.FloorType;
import com.sun.sgs.example.hack.share.ItemInfo.ItemType;

/**
 * This class loads sprites from the default sprite map provided with
 * the client distribution and maps the various displayable types to
 * these sprites.  This class will only work properly on the sprite
 * map being provided with the client distrivution.
 */
public class DefaultSpriteMap implements SpriteMap {


    private static final Logger logger = 
	Logger.getLogger(SpriteMap.class.getName());

    /**
     * The height and width dimension of all the sprites in this map
     */
    private final int spriteSize;

    /**
     * A mapping from {@code CreatureType} to the sprite that will be
     * used to display that type
     */
    private final Map<CreatureType,BufferedImage> creatureSprites;

    /**
     * A mapping from {@code ItemType} to the sprite that will be used
     * to display that type
     */
    private final Map<ItemType,BufferedImage> itemSprites;

    /**
     * A mapping from {@code FloorType} to the sprite that will be
     * used to display that type
     */
    private final Map<FloorType,BufferedImage> floorSprites;

    /**
     * Creates an instance of {@code DefaultSpriteMap}
     *
     * @throws IllegalStateException if {@code
     *         SPRITE_MAP_FILE_PROPERTY} or {@code
     *         SPRITE_SIZE_PROPERTY} are not defined or are
     *         incorrectly defined.
     */
    public DefaultSpriteMap() {

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

	Map<Integer,BufferedImage> spriteMap = null;
	
	try {
	    spriteMap = loadSprites(spriteMapFile);
	} catch (IOException ioe) {
	    throw new IllegalStateException("Sprite map incorrectly configured",
					    ioe);
	}
	
	creatureSprites = 
	    new EnumMap<CreatureType,BufferedImage>(CreatureType.class);

	floorSprites = 
	    new EnumMap<FloorType,BufferedImage>(FloorType.class);

	itemSprites = 
	    new EnumMap<ItemType,BufferedImage>(ItemType.class);

	// map each of the enum types to the correct sprite image
	// using the predetermined sprite indices.
	mapCreatureSprites(spriteMap);
	mapFloorSprites(spriteMap);
	mapItemSprites(spriteMap);
    }

    /**
     * Loads sprite images from the single image pointed to at the
     * provided file using the {@code spriteSize} as a guide for where
     * the images are embedded.
     *
     * @param spriteMapFile an image file that contains all the
     *        sprites
     */
    private Map<Integer,BufferedImage> loadSprites(String spriteMapFile) 
	throws IOException {
       
        logger.finer("Trying to read images from: " + spriteMapFile);

	Map<Integer,BufferedImage> spriteMap = 
	    new HashMap<Integer,BufferedImage>();

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
	return spriteMap;
    }

    /**
     * Uses the provided mapping to fill the {@code creatureSprites}
     * map.
     *
     * @param spriteMap a mapping from sprite index to image
     */
    private void mapCreatureSprites(Map<Integer,BufferedImage> spriteMap) {
	// playable character types
	creatureSprites.put(CreatureType.WARRIOR, spriteMap.get(41));
	creatureSprites.put(CreatureType.WIZARD, spriteMap.get(42));
	creatureSprites.put(CreatureType.PRIEST, spriteMap.get(43));
	creatureSprites.put(CreatureType.THIEF, spriteMap.get(44));
	creatureSprites.put(CreatureType.ARCHAEOLOGIST, spriteMap.get(45));
	creatureSprites.put(CreatureType.BARBARIAN, spriteMap.get(47));
	creatureSprites.put(CreatureType.VALKYRIE, spriteMap.get(48));
	creatureSprites.put(CreatureType.GUARD, spriteMap.get(49));
	creatureSprites.put(CreatureType.TOURIST, spriteMap.get(50));

	// other creature sprites
	creatureSprites.put(CreatureType.DRAGON, spriteMap.get(51));
	creatureSprites.put(CreatureType.BEHOLDER, spriteMap.get(52));
	creatureSprites.put(CreatureType.ORC, spriteMap.get(53));
	creatureSprites.put(CreatureType.BAT, spriteMap.get(54));
	creatureSprites.put(CreatureType.SLIME, spriteMap.get(55));
	creatureSprites.put(CreatureType.GHOST, spriteMap.get(56));
	creatureSprites.put(CreatureType.ZOMBIE, spriteMap.get(57));
	creatureSprites.put(CreatureType.VAMPIRE, spriteMap.get(58));
	creatureSprites.put(CreatureType.RODENT, spriteMap.get(59));
	creatureSprites.put(CreatureType.ANT, spriteMap.get(60));
	creatureSprites.put(CreatureType.SNAKE, spriteMap.get(61));
	creatureSprites.put(CreatureType.MINOTAUR, spriteMap.get(62));
	creatureSprites.put(CreatureType.FUNGUS, spriteMap.get(63));
	creatureSprites.put(CreatureType.CENTAUR, spriteMap.get(64));
	creatureSprites.put(CreatureType.UNICORN, spriteMap.get(65));
	creatureSprites.put(CreatureType.MIMIC, spriteMap.get(66));
	creatureSprites.put(CreatureType.WILL_O_WISP, spriteMap.get(67));
	creatureSprites.put(CreatureType.DEMON, spriteMap.get(68));
	creatureSprites.put(CreatureType.GRUE, spriteMap.get(69));
	creatureSprites.put(CreatureType.CAVEMAN, spriteMap.get(79));
    }

    /**
     * Uses the provided mapping to fill the {@code floorSprites}
     * map.
     *
     * @param spriteMap a mapping from sprite index to image
     */
    private void mapFloorSprites(Map<Integer,BufferedImage> spriteMap) {
	// usused sprites:
	// 	SPACE_VWALL = 5;
	// 	SPACE_HWALL = 6;
	// 	SPACE_CORNER_UL_SH = 7;
	// 	SPACE_CORNER_UR = 8;
	// 	SPACE_CORNER_LL = 9;
	// 	SPACE_CORNER_LR_SH = 10;
	// 	SPACE_CORNER_LR = 11;
	// 	SPACE_CORNER_UL = 12;
	// 	SPACE_STAIRS_UP = 13;
	// 	SPACE_STAIRS_DOWN = 14;
	// 	SPACE_VDOOR_CLOSED = 15;
	// 	SPACE_HDOOR_CLOSED = 16;	
	// 	SPACE_VDOOR_OPEN = 17;
	// 	SPACE_HDOOR_OPEN = 18;


	floorSprites.put(FloorType.UNKNOWN, spriteMap.get(1));
	floorSprites.put(FloorType.FLOOR, spriteMap.get(4));
	floorSprites.put(FloorType.HOLY_GROUND, spriteMap.get(3));

	// for now we only have one sprite to represent walls, so
	// we display  them all the same
	floorSprites.put(FloorType.OUTSIDE_WALL, spriteMap.get(2));
	floorSprites.put(FloorType.GRANITE, spriteMap.get(2));
	floorSprites.put(FloorType.PERMANENT_WALL, spriteMap.get(2));

	floorSprites.put(FloorType.STAIRS_UP, spriteMap.get(13));
	floorSprites.put(FloorType.STAIRS_DOWN, spriteMap.get(14));

	// currently the backing sprite map has different door sprites
	// depending on whether the door is in a horizontal or
	// vertical position.  For the time being, we just use the one
	// of these
	floorSprites.put(FloorType.CLOSED_DOOR, spriteMap.get(15));
	floorSprites.put(FloorType.OPENED_DOOR, spriteMap.get(17));
	floorSprites.put(FloorType.ALTAR, spriteMap.get(19));
	floorSprites.put(FloorType.FOUNTAIN, spriteMap.get(20));
    }

    /**
     * Uses the provided mapping to fill the {@code itemSprites}
     * map.
     *
     * @param spriteMap a mapping from sprite index to image
     */
    private void mapItemSprites(Map<Integer,BufferedImage> spriteMap) {

	// unused sprites:
	//     WEAPON_DAGGER = 26;
	//     ITEM_KEY = 32;
	//     ITEM_POTION_PINK = 33;
	//     ITEM_BOOK_BROWN = 34;
	//     ITEM_SCROLL_RED = 35;
	//     ITEM_CHEST = 36;
	//     ITEM_TORCH = 37;
	//     WEAPON_AXE = 38;
	//     ITEM_GOLD = 39;
	//     ITEM_SCROLL_GREEN = 71;
	//     ITEM_SCROLL_PURPLE = 72;
	//     ITEM_POTION_GREEN = 75;
	//     ITEM_POTION_PURPLE = 76;
	//     ITEM_BOOK_BLUE = 77;
	//     ITEM_BOOK_GREEN = 78;
	//     ITEM_RING_PURPLE = 80;
	//     ITEM_RING_RED = 83;
	//     ITEM_RING_GREEN = 84;
	//     ITEM_ORB = 87;
	//     WEAPON_CLUB = 89;

	itemSprites.put(ItemType.HELM, spriteMap.get(21));
	itemSprites.put(ItemType.SHIELD, spriteMap.get(22));
	itemSprites.put(ItemType.HARD_ARMOR, spriteMap.get(23));
	itemSprites.put(ItemType.BOOTS, spriteMap.get(24));
	itemSprites.put(ItemType.GLOVES, spriteMap.get(25));
	itemSprites.put(ItemType.CLOAK, spriteMap.get(86));

	itemSprites.put(ItemType.EDGED_WEAPON, spriteMap.get(31));
	itemSprites.put(ItemType.HAFTED_WEAPON, spriteMap.get(27));
	itemSprites.put(ItemType.POLEARM, spriteMap.get(28));

	itemSprites.put(ItemType.CONTAINER, spriteMap.get(29));
	itemSprites.put(ItemType.FOOD, spriteMap.get(30));
	itemSprites.put(ItemType.SKELETON, spriteMap.get(85));
	itemSprites.put(ItemType.LIGHT_SOURCE, spriteMap.get(90));

	itemSprites.put(ItemType.WAND, spriteMap.get(40));
	itemSprites.put(ItemType.SCROLL, spriteMap.get(73));
	itemSprites.put(ItemType.POTION, spriteMap.get(74));
	itemSprites.put(ItemType.BOOK, spriteMap.get(79));
	itemSprites.put(ItemType.ROD, spriteMap.get(80));
	itemSprites.put(ItemType.RING, spriteMap.get(82));
	itemSprites.put(ItemType.STAFF, spriteMap.get(88));
    }


    /**
     * {@inheritDoc}
     */
    public int getNumSprites() {
	return creatureSprites.size() + 
	    itemSprites.size() + floorSprites.size();
    }

    /**
     * {@inheritDoc}
     */
    public int getSpriteSize() {
        return spriteSize;
    }

    /**
     * {@inheritDoc}
     */
    public BufferedImage getCreatureSprite(CreatureType creature) {
	return creatureSprites.get(creature);
    }

    /**
     * {@inheritDoc}
     */
    public BufferedImage getFloorSprite(FloorType floor) {
	return floorSprites.get(floor);
    }

    /**
     * {@inheritDoc}
     */
    public BufferedImage getItemSprite(ItemType item) {
	return itemSprites.get(item);
    }

}
