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

package com.sun.gi.framework.timer;

import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface TimerManager {

    /**
     * This method registers an event to be queued after the specified
     * delay in MS. If repeating is set true it will be queued again in
     * the same number of MS and continue doing that until removed.
     * <p>
     * Timer events are *not* persistant nor ditributed. They need to be
     * set up by the Boot object of the app and always run local to the
     * slice they are registered on. We have provided a destributed and
     * persistant timer GLO library runs of this local timer in app
     * space to allow for destributed processing of persistant events.
     * <p>
     * The system will make a best-effort to queue the task as close to
     * the requested time as possible. Note that actual execution is
     * then up to the code that handles executing queued tasks.
     * <p>
     * IMPORTANT: The backend system will be configured with a minimum
     * timer tick (default is 1 second.) The size of the delay will be
     * rounded up to the nearest full multiple of that tick. (eg, a 500
     * ms request at the default tick rate of 1sec (1000ms) will mean an
     * actual event frequency of approximately 1/sec.)
     * 
     * @see com.sun.gi.gloutils.pdtimer.PDTimer
     * 
     * @param tid the timer event ID of this event
     * @param sim the simulation who is requesting this timer event
     * @param access
     * @param startObjectID ID of a GLO that implements
     * TimerManagerListener to receive the event
     * @param delay the time in ns to delay before queuint the event.
     * @param repeat if false, this is a one shot, else it repeats
     * 
     * @return an ID for the event
     */
    public long registerEvent(long tid, Simulation sim, ACCESS_TYPE access,
            long startObjectID, long delay, boolean repeat);

    /**
     * Removes a task from the lost of timed events.
     * 
     * @param eventID The ID returned from the call used to register the
     * event.
     */
    public void removeEvent(long eventID);

    /**
     * @return long the next ID from the TimerID sequence
     */
    public long getNextTimerID();
}
