/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.hack.share;


/**
 * A simple set of identifiesd for all the sprites in the default map.
 * <p>
 * FIXME: This should be an enumeration. Also, it should probably be removed,
 * because while it provides some convenience, it's just the name mapping
 * for one sprite map, and should never be referenced directly.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameIdentifiers
{

    public static final int SPACE_BLANK = 1;
    public static final int SPACE_FLOOR = 2;
    public static final int SPACE_HLYGRND = 3;
    public static final int SPACE_UNUSED = 4;
    public static final int SPACE_VWALL = 5;
    public static final int SPACE_HWALL = 6;
    public static final int SPACE_CORNER_UL_SH = 7;
    public static final int SPACE_CORNER_UR = 8;
    public static final int SPACE_CORNER_LL = 9;
    public static final int SPACE_CORNER_LR_SH = 10;
    public static final int SPACE_CORNER_LR = 11;
    public static final int SPACE_CORNER_UL = 12;
    public static final int SPACE_STAIRS_UP = 13;
    public static final int SPACE_STAIRS_DOWN = 14;
    public static final int SPACE_VDOOR_CLOSED = 15;
    public static final int SPACE_HDOOR_CLOSED = 16;
    public static final int SPACE_VDOOR_OPEN = 17;
    public static final int SPACE_HDOOR_OPEN = 18;
    public static final int SPACE_ALTAR = 19;
    public static final int SPACE_FOUNTAIN = 20;

    public static final int ARMOR_HELMET = 21;
    public static final int ARMOR_SHIELD = 22;
    public static final int ARMOR_ARMOR = 23;
    public static final int ARMOR_BOOTS = 24;
    public static final int ARMOR_GLOVES = 25;

    public static final int WEAPON_DAGGER = 26;
    public static final int WEAPON_MACE = 27;
    public static final int WEAPON_WHIP = 28;

    public static final int ITEM_BAG = 29;
    public static final int ITEM_FOOD = 30;

    public static final int WEAPON_SWORD = 31;

    public static final int ITEM_KEY = 32;
    public static final int ITEM_POTION_PINK = 33;
    public static final int ITEM_BOOK_BROWN = 34;
    public static final int ITEM_SCROLL_RED = 35;
    public static final int ITEM_CHEST = 36;
    public static final int ITEM_TORCH = 37;

    public static final int WEAPON_AXE = 38;

    public static final int ITEM_GOLD = 39;

    public static final int WEAPON_WAND = 40;

    public static final int CHAR_WARRIOR = 41;
    public static final int CHAR_WIZARD = 42;
    public static final int CHAR_PRIEST = 43;
    public static final int CHAR_THIEF = 44;
    public static final int CHAR_ARCHAEOLOGIST = 45;
    public static final int CHAR_FRED = 46;
    public static final int CHAR_BARBARIAN = 47;
    public static final int CHAR_VALKYRIE = 48;
    public static final int CHAR_GUARD = 49;
    public static final int CHAR_TOURIST = 50;

    public static final int MONSTER_DRAGON = 51;
    public static final int MONSTER_BEHOLDER = 52;
    public static final int MONSTER_ORC = 53;
    public static final int MONSTER_BAT = 54;
    public static final int MONSTER_SLIME = 55;
    public static final int MONSTER_GHOST = 56;
    public static final int MONSTER_ZOMBIE = 57;
    public static final int MONSTER_VAMPIRE = 58;
    public static final int MONSTER_RODENT = 59;
    public static final int MONSTER_ANT = 60;
    public static final int MONSTER_SNAKE = 61;
    public static final int MONSTER_MINOTAUR = 62;
    public static final int MONSTER_FUNGUS = 63;
    public static final int MONSTER_CENTAUR = 64;
    public static final int MONSTER_UNICORN = 65;
    public static final int MONSTER_MIMIC = 66;
    public static final int MONSTER_WILL_O_WISP = 67;
    public static final int MONSTER_DEMON = 68;
    public static final int MONSTER_GRUE = 69;
    public static final int MONSTER_CAVEMAN = 70;

    public static final int ITEM_SCROLL_GREEN = 71;
    public static final int ITEM_SCROLL_PURPLE = 72;
    public static final int ITEM_SCROLL_BLUE = 73;
    public static final int ITEM_POTION_BLUE = 74;
    public static final int ITEM_POTION_GREEN = 75;
    public static final int ITEM_POTION_PURPLE = 76;
    public static final int ITEM_BOOK_BLUE = 77;
    public static final int ITEM_BOOK_GREEN = 78;
    public static final int ITEM_BOOK_RED = 79;

    public static final int WEAPON_WAND_SILLY = 80;

    public static final int ITEM_RING_PURPLE = 80;
    public static final int ITEM_RING_WHITE = 82;
    public static final int ITEM_RING_RED = 83;
    public static final int ITEM_RING_GREEN = 84;
    public static final int ITEM_CORPSE = 85;

    public static final int ARMOR_CLOAK = 86;

    public static final int ITEM_ORB = 87;

    public static final int WEAPON_STAFF = 88;
    public static final int WEAPON_CLUB = 89;

    public static final int ITEM_LANTERN = 90;

}
