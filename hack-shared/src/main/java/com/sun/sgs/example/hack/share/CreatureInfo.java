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
 * A shared class for defining all creature-related enums and
 * interfaces.  The {@code CreatureType} enum defined in this class is
 * also used for character classes, with the list of playable
 * characeter classes being defined by {@link
 * #isPlayableCreatureType(CreatureType)} The information contained in
 * this class should be used between the client and server.  This
 * class also provides utility methods for encoding and decoding its
 * enums.
 *
 * @see ItemInfo
 * @see RoomInfo
 */
public class CreatureInfo {

    private static final CreatureType[] CREATURE_TYPE_DECODING 
	= EnumSet.allOf(CreatureType.class).toArray(new CreatureType[] { });

    /**
     * The set of playable characters for the game
     */
    private static final EnumSet<CreatureType> PLAYABLE_CREATURE_TYPES
	= EnumSet.of(CreatureType.WARRIOR,
		     CreatureType.WIZARD,
		     CreatureType.PRIEST,
		     CreatureType.THIEF,
		     CreatureType.ARCHAEOLOGIST,
		     CreatureType.BARBARIAN,
		     CreatureType.VALKYRIE,
		     CreatureType.GUARD,
		     CreatureType.TOURIST);

    
    public static CreatureType decodeCreatureType(int encodedCreatureType) {
	if (encodedCreatureType < 0 || 
	    encodedCreatureType >= CREATURE_TYPE_DECODING.length) {
	    throw new IllegalArgumentException(encodedCreatureType + " is not " 
					       + "a valid encoding for a " 
					       + "creature type");
	}
	return CREATURE_TYPE_DECODING[encodedCreatureType];
    }

    public static int encodeCreatureType(CreatureType type) {
	return type.ordinal();
    }

    public static boolean isPlayableCreatureType(CreatureType type) {
	return PLAYABLE_CREATURE_TYPES.contains(type);
    }

    /**
     * A shared interface for passing information between client and
     * server about a specific creature.
     */
    public interface Creature {

	/**
	 * Returns the unique Id for the creature
	 */
	public long getCreatureId();

	/**
	 * Returns the specific type of this creature
	 */
	public CreatureType getCreatureType();

	/**
	 * Returns the name of this creature
	 */
	public String getName();

    }
    
    /**
     * All of the creature types available for creatures in the game.
     * This enum also includes all the types for playable characters.
     * This allows the game to instantiate non-player creatures of
     * playable types.
     */
    public enum CreatureType {
	// non-playable creatures
	ANGEL, // 0
	    ROBOT,
	    HUMAN,
	    DEMON,
	    ANIMAL,
	    GIANT, // 5
	    ORC,
	    TROLL,
	    ELF,
	    DWARF,
	    HOBBIT, // 10
	    DRAGON,
	    LICH,
	    BEHOLDER,
	    BAT,
	    SLIME, // 15
	    GHOST,
	    ZOMBIE,
	    VAMPIRE,
	    ANT,
	    SNAKE, // 20
	    MINOTAUR,
	    FUNGUS,
	    CENTAUR,
	    MIMIC,
	    WILL_O_WISP, // 25
	    GRUE,
	    CAVEMAN,
	    RODENT,
	    UNICORN,
	    
	    // playable character types
	    WARRIOR, // 30
	    WIZARD,
	    PRIEST,
	    THIEF,
	    ARCHAEOLOGIST,
	    BARBARIAN, // 35
	    VALKYRIE,
	    GUARD,
	    TOURIST
    }

    public enum CreatureAttribute {

	    DROP_GOOD,
	    DROP_GREAT
	    
    }


    /**
     * Returns the name of the provided {@code CreatureType} in a
     * printable format where the first character of every word is
     * capitalized, the remaining characters are lower case and all
     * {@code _} characters are replaced with spaces.
     *
     * @param type the type to be printed
     *
     * @return a printable version of the type
     */
    public static String getPrintableName(CreatureType type) {
	String name = type.name();
	return name.substring(0,1) + name.substring(1).toLowerCase();
    }

}