/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */


/*
 * CharacterStats.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Wed Mar  1, 2006	 3:59:58 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.share;

import java.io.Serializable;


/**
 * This class represents the basic statistics and information associated
 * with characters.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class CharacterStats implements Serializable
{

    // the name of the character
    private String name;

    // the numeric statistics for the character
    private int strength;
    private int intelligence;
    private int dexterity;
    private int wisdom;
    private int constitution;
    private int charisma;
    private int hitPoints;
    private int maxHitPoints;

    /**
     * Creates an instance of <code>CharacterStats</code>.
     *
     * @param name the character's name
     * @param strength the character's strength
     * @param intelligence the character's intelligence
     * @param dexterity the character's dexterity
     * @param wisdom the character's wisdom
     * @param constitution the character's constitution
     * @param charisma the character's charisma
     * @param hitPoints the character's hitPoints
     * @param maxHitPoints the character's maxHitPoints
     */
    public CharacterStats(String name, int strength, int intelligence,
                          int dexterity, int wisdom, int constitution,
                          int charisma, int hitPoints, int maxHitPoints) {
        this.name = name;
        this.strength = strength;
        this.intelligence = intelligence;
        this.dexterity = dexterity;
        this.wisdom = wisdom;
        this.constitution = constitution;
        this.charisma = charisma;
        this.hitPoints = hitPoints;
        this.maxHitPoints = maxHitPoints;
    }

    /**
     *
     */
    public String getName() {
        return name;
    }

    /**
     *
     */
    public int getStrength() {
        return strength;
    }

    /**
     *
     */
    public void setStrength(int strength) {
        this.strength = strength;
    }

    /**
     *
     */
    public int getIntelligence() {
        return intelligence;
    }

    /**
     *
     */
    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    /**
     *
     */
    public int getDexterity() {
        return dexterity;
    }

    /**
     *
     */
    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    /**
     *
     */
    public int getWisdom() {
        return wisdom;
    }

    /**
     *
     */
    public void setWisdom(int wisdom) {
        this.wisdom = wisdom;
    }

    /**
     *
     */
    public int getConstitution() {
        return constitution;
    }

    /**
     *
     */
    public void setConstitution(int constitution) {
        this.constitution = constitution;
    }

    /**
     *
     */
    public int getCharisma() {
        return charisma;
    }

    /**
     *
     */
    public void setCharisma(int charisma) {
        this.charisma = charisma;
    }

    /**
     *
     */
    public int getHitPoints() {
        return hitPoints;
    }

    /**
     *
     */
    public void setHitPoints(int hitPoints) {
        this.hitPoints = hitPoints;
    }

    /**
     *
     */
    public int getMaxHitPoints() {
        return maxHitPoints;
    }

    /**
     *
     */
    public void setMaxHitPoints(int maxHitPoints) {
        this.maxHitPoints = maxHitPoints;
    }

}
