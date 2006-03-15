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
 * AICharacterManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	 3:00:32 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.utils.StatisticalUUID;

import com.sun.gi.apps.hack.server.BasicCharacterManager;
import com.sun.gi.apps.hack.server.Character;
import com.sun.gi.apps.hack.server.CharacterManager;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.util.Collection;


/**
 * This implementation of CharacterManager is used for all AI creatures. It
 * adds the ability to do regular invocations of AI characters and handle
 * their death and re-generation.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class AICharacterManager extends BasicCharacterManager
{

    // a self-reference
    private GLOReference<AICharacterManager> selfRef = null;

    // the character we're managing
    private AICharacter character;

    /**
     * Creates an instance of <code>AICharacter</code>.
     */
    private AICharacterManager() {
        // since we need a unique identifier for all managers (which players
        // get through their login names), we just use a UUID
        super("ai:" + (new StatisticalUUID()).toString());
    }

    /**
     * Returns a reference to a new instance of <code>AICharacterManager</code>
     * that is registered correctly. After calling this you still need to
     * call <code>setCharacter</code> to give this manager a character.
     *
     * @return a reference to a new manager
     */
    public static GLOReference<AICharacterManager> newInstance() {
        /* XXX: the previous implementation was technically wrong,
         * although it might have worked.  In order to continue working
         * with the object to which we've created a GLOReference, we
         * <b>must</b> get() the object from the newly created GLOReference,
         * and <b>must not</b> continue using the instance that was passed
         * to createGLO as the "template object."
         *
         * <pre>
         *   AICharacterManager mgr = new AICharacterManager();
         *   // WRONG to modify mgr (by setting mgr.selfRef) in the next line!
         *   mgr.selfRef = SimTask.getCurrent().createGLO(mgr, mgr.toString());
         *   // NO GUARANTEE that mgr.selfRef's new value will be saved!
         *   return mgr.selfRef;
         * </pre>
         */

        SimTask task = SimTask.getCurrent();
        AICharacterManager mgrTemplate = new AICharacterManager();
        GLOReference<AICharacterManager> mgrRef =
            task.createGLO(mgrTemplate, mgrTemplate.toString());
        AICharacterManager createdMgr = mgrRef.get(task);
        // Obtain the created GLO from the ref, and modify it.
        createdMgr.selfRef = mgrRef;
        return mgrRef;
    }

    /**
     * Returns a reference to this manager.
     *
     * @return a self-reference
     */
    public GLOReference<? extends CharacterManager> getReference() {
        return selfRef;
    }

    /**
     * Returns the current character being played through this manager. Since
     * <code>AICharacterManager</code>s only have one character that they
     * manage, the current character is always the same.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
        return character;
    }

    /**
     * Sets the character for this manager.
     *
     * @param character the character to manage
     */
    public void setCharacter(AICharacter character) {
        this.character = character;
    }

    /**
     * Tells the AI creature that it's their turn to take some action.
     */
    public void run() {
        character.run();
    }

    /**
     * Notify the manager that its character has died. This will typically
     * result in re-generation of the character after some period of time.
     */
    public void notifyCharacterDied() {
        // FIXME: based on some aspect of the character's stats, and possibly
        // other parameters that aren't here yet, we decide how and where to
        // place a new character ... for now, we take the simple approach
        // of just adding a new character
        character.regenerate();
        getCurrentLevel().get(SimTask.getCurrent()).
            addCharacter(getReference());
    }

    /**
     * Sends the given board to this AI creature. Many AIs will ignore this
     * message, but some use it to construct a view of the world
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        // FIXME: where do I connect this?
    }

    /**
     * Sends space updates to this AI creature. Many AIs will ignore this
     * message, but some use it to construct a view of the world
     *
     * @param updates the updates to send
     */
    public void sendUpdate(Collection<BoardSpace> updates) {
        // FIXME: where do I connect this?
    }

}
