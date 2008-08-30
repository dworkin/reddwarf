/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * A shared class for defining all item-related enums and interfaces.
 * The information contained in this class should be used between the
 * client and server.  This class also provides utility methods for
 * encoding and decoding its enums.
 *
 * @see RoomInfo
 * @see CreatureInfo
 */
public final class ItemInfo {

    private static final ItemType[] ITEM_TYPE_DECODING 
	= EnumSet.allOf(ItemType.class).toArray(new ItemType[] { });

    /**
     * A common interface for sharing item data between the client and
     * server
     */
    public interface Item {
	/**
	 * Returns the item's type.
	 *
	 * @return the identifier
	 */
	public ItemType getItemType();
	
	/**
	 * Returns the unique identifier for this item.
	 */
	public long getItemId();
	
	/**
	 * Returns the name for this item.
	 */
	public String getName();
    }

    /**
     * A enum of all the main types of items in the game.  Each item
     * type will have a set of subtypes that specialized the
     * properties of an item.
     *
     * @see ItemInfo$ItemSubType
     */
    public enum ItemType {

	// dummy varialbe to only be used during dungeon creation
	UNKNOWN,

	    // consumables
	    MUSHROOM,
	    FOOD, 

	    // weapons
	    EDGED_WEAPON,
	    HAFTED_WEAPON,
	    POLEARM, 
	    INSTRUMENT,
	    
	    // ranged weapons
	    BOW,
	    CROSSBOW,
	    SLING, 
	    MISSILE, 

	    // ammunition
	    SHOT,
	    ARROW,
	    BOLT,
	    
	    // digging tools
	    DIGGING_TOOL,
	    
	    // armor
	    CLOAK, 
	    GLOVES,
	    BOOTS,
	    HELM,
	    CROWN,
	    SOFT_ARMOR,
	    HARD_ARMOR,
	    DRAGON_SCALE_MAIL,
	    SHIELD, 
	    RING, 
	    AMULET,

	    // light sources
	    LIGHT_SOURCE,

	    // usable items

	    SCROLL, 
	    POTION, 
	    WAND, 
	    STAFF, 
	    ROD,

	    // containers, bottles, bags
	    CONTAINER,

	    // magical accessories
	    BOOK, 

	    // 
	    ANIMAL_SKIN,

	    // door spikes
	    SPIKES,

	    // worthless items
	    JUNK,
	    SKELETON, 
	    }


    public static ItemType decodeItemType(int encodedItemType) {
	if (encodedItemType < 0 || 
	    encodedItemType >= ITEM_TYPE_DECODING.length) {
	    throw new IllegalArgumentException(encodedItemType + " is not " 
					       + "a valid encoding for a " 
					       + "item type");
	}
	return ITEM_TYPE_DECODING[encodedItemType];
    }

    public static int encodeItemType(ItemType type) {
	return type.ordinal();
    }



    public enum ItemSubType {
	// SKELETON
	SKULL,
	    BONE,
	    SKELTON,
	    TEETH,

	    // JUNK
	    ROCK,
	    SHARD,
	    STICK,
	    STUMP,
	    BRANCH,
	    SAND,
	    EARTH,
	    MUD,
	    DUST,
	    ICE,

	    // ANIMAL_SKIN
	    SKIN,
	    SCALES,
	    FEATHERS,
	    FURS,
	    SCALE_COAT,
	    FEATHER_COAT,
	    FUR_COAT,

	    // SHOT
	    PEBBLES,
	    SHOT,

	    // ARROW
	    ARROWS,
	    SEEKER_ARROWS,
	    SPECIAL_ARROWS,
	    STEEL_ARROWS,
	    GRAPPLE_ARROWS,

	    // BOLT
	    BOLT,
	    SEEKER_BOLTS,
	    SPECIAL_BOLTS,
	    STEEL_BOLTS,
	    GRAPPLE_BOLTS,
	    
	    // SLING
	    SLING,

	    // BOW
	    SHORT_BOW,
	    LONG_BOW,

	    // CROSS BOW
	    HAND_CROSSBOW,
	    LIGHT_CROSSBOW,
	    HEAVY_CROSSBOW,

	    // DIGGING_TOOL
	    SHOVEL,
	    GNOMISH_SHOVEL,
	    DWARVEN_SHOVEL,
	    PICK,
	    ORCISH_PICK,
	    DWARVEN_PICK,
	    MATTOCK,

	    // HAFTED_WEAPON
	    THROWING_HAMMER,
	    WHIP,
	    QUARTER_STAFF,
	    BATON,
	    MACE,
	    BALL_AND_CHAIN,
	    WAR_HAMMER,
	    LUCERN_HAMMER,
	    MORNING_STAR,
	    FLAIL,
	    LEAD_FILLED_MACE,
	    TWO_HANDED_MACE,
	    TWO_HANDED_FLAIL,
	    MACE_OF_DISRUPTION,
	    GROND,

	    // POLEARM
	    DART,
	    JAVELIN,
	    AWL_PIKE,
	    SPEAR,
	    HARPOON,
	    PIKE,
	    BEAKED_AXE,
	    BROAD_AXE,
	    GLAIVE,
	    TRIDENT,
	    HALBERD,
	    SCYTHE,
	    TWO_HANDED_SPEAR,
	    BATTLE_AXE,
	    GREAT_AXE,
	    LANCE,
	    LOCHABER_AXE,
	    SCYTHE_OF_SLICING,
	    
	    // EDGED_WEAPON
	    BROKEN_DAGGER,
	    BROKEN_SWORD,
	    DAGGER,
	    MAIN_GAUCHE,
	    RAPIER,
	    SMALL_SWORD,
	    SHORT_SWORD,
	    SABRE,
	    CUTLASS,
	    TULWAR,
	    BROAD_SWORD,
	    LONG_SWORD,
	    SCIMITAR,
	    KATANA,
	    BASTARD_SWORD,
	    TWO_HANDED_SWORD,
	    EXECUTIONERS_SWORD,
	    BLADE_OF_CHAOS,

	    // SHIELD
	    SMALL_LEATHER_SHIELD,
	    SMALL_METAL_SHIELD,
	    LARGE_LEATHER_SHIELD,
	    LARGE_METAL_SHIELD,
	    SHIELD_OF_DEFLECTION,

	    // HELMET
	    HARD_LEATHER_CAP,
	    METAL_CAP,
	    IRON_HELM,
	    STEEL_HELM,
	    IRON_CROWN,
	    GOLDEN_CROWN,
	    JEWELED_CROWN,
	    MORGOTH,

	    // BOOTS
	    SOFT_LEATHER_BOOTS,
	    HARD_LEATHER_BOOTS,
	    METAL_SHOD_BOOTS,

	    // CLOAK
	    CLOAK,
	    TABARD,
	    LEATHER_COAT,
	    SHADOW_CLOAK,
	    RIVITED_LEATHER_COAT,
	    CHAIN_MAIL_COAT,

	    // GLOVES
	    SET_OF_LEATHER_GLOVES,
	    SET_OF_GAUNTLETS,
	    SET_OF_CESTI,

	    // SOFT_ARMOR
	    FILTHY_RAG,
	    ROBE,
	    SOFT_LEATHER_ARMOR,
	    SOFT_STUDDED_LEATHER,
	    HARD_LEATHER_ARMOR,
	    HARD_STUDDED_LEATHER,
	    LEATHER_SCALE_MAIL,	 

	    // HARD_ARMOR
	    RUSTY_CHAIN_MAIL,
	    METAL_SCALE_MAIL,
	    CHAIL_MAIL,
	    AUGMENTED_CHAIL_MAIL,
	    DOUBLE_CHAIN_MAIL,
	    BAR_CHAIN_MAIL,
	    METAL_BRIGADINE_ARMOR,
	    PARTIAL_PLATE_MAIL,
	    METAL_LAMELLAR_ARMOR,
	    FULL_PLATE_ARMOR,
	    RIBBED_PLATE_ARMOR,
	    MITHRIL_CHAIN_MAIL,
	    MITHRIL_PLATE_MAIL,
	    ADAMANTITE_PLATE_MAIL,

	    // dragon scale mail
	    DRAGON_BLACK,
	    DRAGON_BLUE,
	    DRAGON_WHITE,
	    DRAGON_RED,
	    DRAGON_GREEN,
	    DRAGON_MULTIHUED,
	    ETHER,
	    COPPER,
	    BRASS,
	    BRONZE,
	    SILVER,
	    GOLD,
	    LAW,
	    CHAOS,
	    BALANCE,
	    POWER
	    
	    }
    
    public enum ItemAttribute {
	STR,
	    INT,
	    WIS,
	    DEX,
	    CON,
	    CHR,
	    SAVE, // saving throws
	    STEALTH,
	    SEARCH,
	    INFRA,
	    TUNNEL,
	    SPEED,
	    BLOWS,
	    SHOTS,
	    MIGHT,
	    SLAY_NATURAL,
	    BRAND_HOLY,
	    SLAY_UNDEAD,
	    SLAY_DEMON,
	    SLAY_ORC,
	    SLAY_TROLL,
	    SLAY_GIANT,
	    SLAY_DRAGON,
	    KILL_DRAGON,
	    KILL_DEMON,
	    KILL_UNDEAD,
	    BRAND_POIS,
	    BRAND_ACID,
	    BRAND_ELEC,
	    BRAND_FIRE,
	    BRAND_COLD,

	    SUST_STR,
	    SUST_INT,
	    SUST_WIS,
	    SUST_DEX,
	    SUST_CON,
	    SUST_CHR,
	    IGNORE_ACID,
	    IGNORE_ELEC,
 	    IGNORE_FIRE,
	    IGNORE_COLD,
	    IGNORE_WATER,
	    IGNORE_THEFT,
	    IM_ACID,
	    IM_ELEC,
	    IM_FIRE,
	    IM_COLD,
	    RES_ACID,
	    RES_ELEC,
	    RES_FIRE,
	    RES_COLD,
	    RES_POIS,
	    RES_FEAR,
	    RES_LITE,
	    RES_DARK,
	    RES_BLIND,
	    RES_CONFU,
	    RES_SOUND,
	    RES_SHARD,
	    RES_NEXUS,
	    RES_NETHR,
	    RES_CHAOS,
	    RES_DISEN,
	    
	    SLOW_DIGEST,
	    FEATHER,
	    LITE,
	    REGEN_HP,
	    TELEPATHY,
	    SEE_INVIS,
	    FREE_ACT,
	    HOLD_LIFE,
	    ESP_DEMON,
	    ESP_DRAGON,
	    ESP_GIANT,
	    ESP_ORC,
	    ESP_TROLL,
	    ESP_UNDEAD,
	    ESP_NATURE,
	    REGEN_MANA,
	    DRAIN_HP,
	    DRAIN_MANA,
	    DRAIN_EXP,
	    AGGRAVATE,
	    UNCONTROLLED,
	    RANDOM,
	    ACTIVATE,
	    BLESSED,
	    INSTA_ART,
	    HUNGER,
	    IMPACT,
	    THROWING,
	    LIGHT_CURSE,
	    HEAVY_CURSE,
	    PERMA_CURSE,

	    BRAND_DARK,
	    BRAND_LITE,
	    HURT_LITE,
	    HURT_WATER,
	    VAMP_HP,
	    VAMP_MANA,
	    IM_POIS,
	    RES_DISEASE,
	    RES_WATER,
	    SLAY_MAN,
	    SLAY_ELF,
	    SLAY_DWARF,
	    ANCHOR,
	    SILENT,
	    STATIC,
	    WINDY,

	    // effects on player race
	    ANIMAL,
	    EVIL,
	    UNDEAD,
	    DEMON,
	    ORC,
	    TROLL,
	    GIANT,
	    DRAGON,
	    MAN,
	    DWARF,
	    ELF,
	    HURT_POIS,
	    HURT_ACID,
	    HURT_ELEC,
	    HURT_FIRE,
	    HURT_COLD,
	    
	    SHOW_DD, // show dice
	    SHOW_MODS,
	    SHOW_CHARGE,
	    SHOW_TURNS,
	    HAS_PVAL,
	    HAS_BONUS,
	    HAS_CHARGES,
	    EXTRA_DAM,
	    EDGED,
	    BLUNT,
	    DO_CUTS,
	    DO_STUN,
	    IS_JUNK,

	    // breakage chances
	    BREAK_100,  
	    BREAK_50,
	    BREAK_25,
	    BREAK_10,

	    GLOW,
	    EXHAUST,
	    RECHARGE,
	    HURT_XXXX,

	    WEAPON,
	    ARMOUR,
	    FLAVOR,
	    NAMED,
	    ONE_HANDED, // parsing-incompatable flag
	    TWO_HANDED, // parsing-incompatable flag
	    OFF_HAND,
	    W_SHIELD, // can be used with a shield

	    BROWSE,
	    REFUEL,
	    APPLY,
	    QUAFF,
	    WIELD,
	    STUDY,
	    CAST,
	    FIRE,
	    FILL,
	    READ,
	    AIM,
	    EAT,
	    USE,
	    ZAP,
	    AMMO
    }

}