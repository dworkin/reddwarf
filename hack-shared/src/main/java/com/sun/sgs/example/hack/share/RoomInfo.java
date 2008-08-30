/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import java.util.EnumSet;

/**
 * A shared class for defining all room-related enums and interfaces.
 * The information contained in this class should be used between the
 * client and server.  This class also provides utility methods for
 * encoding and decoding its enums.
 *
 * @see ItemInfo
 * @see CreatureInfo
 */
public final class RoomInfo {

    private static final FloorType[] FLOOR_TYPE_DECODING 
	= EnumSet.allOf(FloorType.class).toArray(new FloorType[] { });

    private static final RoomType[] ROOM_TYPE_DECODING 
	= EnumSet.allOf(RoomType.class).toArray(new RoomType[] { });

    public enum RoomRating {
	AVERAGE,
	    ABOVE_AVERAGE,
	    GOOD,
	    VERY_GOOD,
	    EXCELLENT,
	    SUPERB,
	    SPECIAL
    }

    public enum RoomType {
	// basic type, a rectangle
	ORDINARY,
	    CHECKERED,
	    TALL,
	    LONG,

	    // unusual types
	    CROSS_SHAPED,
	    INNER_ROOM,
	    STARBURST,
	    CHAMBERED,
	    MAZE,

	    // special room types
	    LESSER_VAULT,
	    GREATER_VAULT,
	    MONSTER_PIT,
	    MONSTER_NEST,
    }

    public static RoomType decodeRoomType(int encodedRoomType) {
	if (encodedRoomType < 0 || 
	    encodedRoomType >= ROOM_TYPE_DECODING.length) {
	    throw new IllegalArgumentException(encodedRoomType + " is not " +
					       "a valid encoding for a " + 
					       "room type");
	}
	return ROOM_TYPE_DECODING[encodedRoomType];
    }

    public static int encodeRoomType(RoomType type) {
	return type.ordinal();
    }

    /**
     * The possible floor types for each of the grid squares.
     */
    public enum FloorType {	
	UNUSED, // 0
	    FLOOR, // 1
	    OUTSIDE_WALL, // 2	    
	    GRANITE, // 3
	    PERMANENT_WALL, // 4
	    CLOSED_DOOR, // 5
	    SECRET_DOOR,
	    OPENED_DOOR, // 7
	    CENTER,
	    PATH, // 9
	    DEBUG, 
	    STAIRS_UP, // 11
	    STAIRS_DOWN,
	    HOLY_GROUND, // 13
	    CURSED_GROUND, 
	    ALTAR,  // 15
	    FOUNTAIN,
	    UNKNOWN, // 17
    }

    public static FloorType decodeFloorType(int encodedFloorType) {
	if (encodedFloorType < 0 || 
	    encodedFloorType >= FLOOR_TYPE_DECODING.length) {
	    throw new IllegalArgumentException(encodedFloorType + " is not " +
					       "a valid encoding for a " + 
					       "floor type");
	}
	return FLOOR_TYPE_DECODING[encodedFloorType];
    }

    public static int encodeFloorType(FloorType type) {
	return type.ordinal();
    }

}