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

package com.sun.gi.gloutils.pdtimer;

import java.util.logging.Logger;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * PDTimer is the primary GLO in the Persistant/Distributed timer system
 * <p>
 * The sim.logic slice timer is non-persistant and local to the slice.
 * The PD timer system is a set of GLOs that are driven off the slice
 * timer manager but provide for the processing of any event on any
 * slice, and the persistance of registered events between runs of the
 * slice.
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimer implements SimTimerListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.gloutils.pdtimer");

    GLOReference<PDTimerEventList> timerListRef = null;

    /**
     * Constructor for a PDTimer object
     * @param task The current SimTask
     */
    public PDTimer(SimTask task) {
        log.fine("init PDTimer");
        PDTimerEventList list = null;
        try {
            list = new PDTimerEventList();
        } catch (InstantiationException e) {

            e.printStackTrace();
        }
        timerListRef = task.createGLO(list, null);
    }

    /**
     * This method registers the PDTimer with the slice's
     * heartbeat manager for heartbeat callbacks.
     * 
     * @param task The Current SimTask
     * @param heartbeat How many ms between heartbeats. Note that
     * setting this too low can flood your slice's system event queue
     * and seriously degrade performance.
     */
    public void start(SimTask task, long heartbeat) {
	task.registerTimerEvent(ACCESS_TYPE.PEEK, heartbeat, true,
		task.lookupReferenceFor(this));
    }

    /**
     * A callback used  by the System heartbeat manager. <b>Not for
     * app user.</b>
     */
    public void timerEvent(long eventID) {
        // Note that when this is called we have just been PEEKed.
        // Thsi is intentional to prvent needless blocking by the
        // different
        // slices all servicing timer events
        log.finest("pd timer tick");
        SimTask task = SimTask.getCurrent();
        PDTimerEventList eventList = timerListRef.peek(task);
        eventList.tick(task, System.currentTimeMillis());
    }
    
    /**
     * This method is called to register an event for timed callback.
     * @param task The Current SimTask
     * @param ACCESS_TYPE The kind of access that is used by the system 
     * to fetch the target obejct when the timed event is triggered.
     * @param delay How long from now the event happens (in ms)
     * @param repeat If false then the event will get triggered one, in approximately 
     * delay seconds.  If true the the event will get triggered repeatedly, approximately
     * every delay seconds.
     * @param target The object to start the event task with.
     * @param methodName The name of the method on the object to start the
     * event with.
     * @param parameters The parameters to pass to the start method.
     * 
     * @returns A GLOReference to a PDTimerEvent object.
     */

    public GLOReference<PDTimerEvent> addTimerEvent(SimTask task,
            ACCESS_TYPE access, long delay, boolean repeat,
            GLOReference target, String methodName, Object[] parameters)
    {
        PDTimerEvent evnt = new PDTimerEvent(access, delay, repeat, target,
                methodName, parameters);
        GLOReference<PDTimerEvent> evntRef = task.createGLO(evnt, null);
        PDTimerEventList list = timerListRef.get(task);
        list.addEvent(task, evntRef);
        return evntRef;
    }

    /**
     * This method cancles a previosuly registered event.
     * @param task The current SimTask
     * @param eventRef a GLOReference to the event to cancel
     */
    public void removeTimerEvent(SimTask task,
            GLOReference<PDTimerEvent> eventRef)
    {
        PDTimerEventList list = timerListRef.get(task);
        list.removeEvent(eventRef);
    }

}
