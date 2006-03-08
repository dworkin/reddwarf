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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimerEventList implements GLO {
    private static Logger log = Logger.getLogger("com.sun.gi.gloutils.pdtimer");
    private SortedMap<Long, HashSet<GLOReference<PDTimerEvent>>> timerEvents =
        new TreeMap<Long, HashSet<GLOReference<PDTimerEvent>>>();
    private static final int BIGCLEANUP_PERIOD = 100;
    private int bigCleanupCountdown = BIGCLEANUP_PERIOD;

    private static final long serialVersionUID = 1L;

    public PDTimerEventList() throws InstantiationException {
        super();
    }

    /** Tick is designed to be called with ACCESS.PEEK */
    public void tick(SimTask task, long time) {
        task.access_check(ACCESS_TYPE.PEEK, this);
        log.finest("Ticking timer list");
        List<GLOReference<PDTimerEvent>> cleanupList =
            new ArrayList<GLOReference<PDTimerEvent>>();
        for (Entry<Long, HashSet<GLOReference<PDTimerEvent>>> entry : timerEvents.entrySet()) {
            if (entry.getKey() <= time) {
                for (GLOReference<PDTimerEvent> eventRef : entry.getValue()) {
                    PDTimerEvent event = eventRef.attempt(task);
                    if (event != null) { // if null then we can skip
                        if (event.isActive()) { // if active then do it
                            event.fire(task);
                            if (event.requiresCleanup()) {
                                cleanupList.add(eventRef);
                            }
                        }
                    }
                }
            } else {
                break; // out of events
            }
        }
        if (cleanupList.size() > 0) {
            try {
                Method cleanupMethod = PDTimerEventList.class.getMethod(
                        "cleanup", List.class);
                task.queueTask(ACCESS_TYPE.GET, task.makeReference(this),
                        cleanupMethod, new Object[] { cleanupList });
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Designed to be called with ACCESS.GET
     * 
     * @param task
     * @param evntRef
     */
    public void addEvent(SimTask task, GLOReference<PDTimerEvent> evntRef) {
        task.access_check(ACCESS_TYPE.GET, this);
        PDTimerEvent evnt = evntRef.peek(task);
        long fireTime = evnt.delayTime() + System.currentTimeMillis();
        HashSet<GLOReference<PDTimerEvent>> bucket = timerEvents.get(fireTime);

        if (bucket == null) {
            bucket = new HashSet<GLOReference<PDTimerEvent>>();
            timerEvents.put(fireTime, bucket);
        }

        bucket.add(evntRef);
    }

    /**
     * Designed to be called with ACCESS.GET
     * 
     * @param eventRef
     */
    public void removeEvent(GLOReference<PDTimerEvent> eventRef) {
        Iterator<Entry<Long, HashSet<GLOReference<PDTimerEvent>>>> it;
        for (it = timerEvents.entrySet().iterator(); it.hasNext();) {
            Entry<Long, HashSet<GLOReference<PDTimerEvent>>> entry = it.next();
            if (entry.getValue().contains(eventRef)) {
                if (entry.getValue().size() == 1)
                    it.remove();
                else
                    entry.getValue().remove(eventRef);
                return;
            }
        }
    }

    /**
     * Called from a task.
     * Designed to be called with ACCESS.GET
     */
    public void cleanup(List<GLOReference<PDTimerEvent>> cleanupList) {
        SimTask task = SimTask.getCurrent();
        task.access_check(ACCESS_TYPE.GET, this);
        log.finest("Doing cleanup");
        /*
         * if (--bigCleanupCountdown==0){ // do a big cleanup
         * bigCleanup(task); } else
         */{ // do a normal destributed cleanup
            for (GLOReference<PDTimerEvent> ref : cleanupList) {
                PDTimerEvent evnt = ref.get(task);
                if (evnt.isRepeating()) {
                    removeEvent(ref);
                    evnt.reset(task); // resets it for next ring
                    addEvent(task, ref);
                } else if (evnt.isMoribund()) {
                    removeEvent(ref);
                    ref.delete(task);
                }
            }
        }
    }

    /**
     * To handle lost cleanups, scrubs the whole list.
     * Designed to be called with ACCESS.GET
     */
    private void bigCleanup(SimTask task) {
        long time = System.currentTimeMillis();
        for (Entry<Long, HashSet<GLOReference<PDTimerEvent>>> entry : timerEvents.entrySet()) {
            if (entry.getKey().longValue() <= time) {
                for (GLOReference<PDTimerEvent> ref : entry.getValue()) {
                    PDTimerEvent evnt = ref.get(task);
                    if (evnt.requiresCleanup()) { // needs to be cleaned
                        if (evnt.isRepeating()) {
                            System.out.println("re-installing");
                            removeEvent(ref); // FIXME: Breaks iterator!
                            evnt.reset(task); // resets it for next ring
                            addEvent(task, ref); // FIXME: Breaks iterator!
                        } else if (evnt.isMoribund()) {
                            removeEvent(ref); // FIXME: Breaks iterator!
                            ref.delete(task);
                        }
                    }
                }
            } else {
                // end of events that have fired already
                break;
            }
        }
    }

}
