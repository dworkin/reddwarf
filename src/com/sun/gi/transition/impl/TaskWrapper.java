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

package com.sun.gi.transition.impl;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.SimTimerListener;
import com.sun.gi.transition.AppContext;
import com.sun.gi.transition.Task;

public class TaskWrapper implements SimTimerListener, Task, Restartable
{
    private static final long serialVersionUID = 1L;
    private static Logger log = Logger.getLogger("com.sun.gi.transition");
    
    private static final AtomicLong nextID = new AtomicLong();

    private GLOReference<TaskWrapper> thisRef;
    private final long id;
    private final GLOReference<? extends GLO> taskRef;
    private final long deadline;
    private boolean cleanup;

    public static GLOReference<TaskWrapper> wrapTask(Task task,
            long deadline,
            boolean cleanup)
    {
        SimTask simTask = SimTask.getCurrent();
        GLOReference<TaskWrapper> wrapperRef =
            simTask.createGLO(new TaskWrapper(task, deadline, cleanup));
        wrapperRef.get(simTask).thisRef = wrapperRef;
        return wrapperRef;
    }

    /**
     * @param task the Task to run, which must implement GLO
     * @param cleanup whether to destroy this wrapper on its first timerEvent
     */
    public TaskWrapper(Task task, long deadline, boolean cleanup) {
        this.id = nextID.incrementAndGet();
        this.taskRef = SimTask.getCurrent().lookupReferenceFor((GLO)task);
        this.deadline = deadline;
        this.cleanup = cleanup;
    }
    
    public void run() throws Exception {
        Task task = (Task) taskRef.get(SimTask.getCurrent());
        if (task == null) {
            cleanup = true;
            log.warning("Timer task was null for " + this);
            return;
        }
        task.run();
    }

    public void restart() {
        SimTask simTask = SimTask.getCurrent();
        
        long delay = (deadline - System.currentTimeMillis());
        delay = Math.max(0, delay);
        
        log.log(Level.FINE,
                "TaskWrapper {0} restart, new delay = {1}",
                new Object[] { this, Long.toString(delay) });
        
        simTask.registerTimerEvent(SimTask.ACCESS_TYPE.GET,
                delay,
                false,
                thisRef);
    }

    public void timerEvent(long eventID) {
        try {
            run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (cleanup) {
                SimTask simTask = SimTask.getCurrent();

                log.log(Level.FINEST, 
                        "TaskWrapper {0} cleanup (ref {1})",
                        new Object[] { this, thisRef });
                
                // Tell our TimerManagerImpl to depersist
                // this task now that it's been completed.
                TaskManagerImpl taskMgr =
                        ((TaskManagerImpl)AppContext.getTaskManager());
                taskMgr.depersistTask(thisRef);

                thisRef.delete(simTask);
            }
        }
    }

    public int hashCode() {
        return Long.valueOf(id).hashCode();
    }
    
    public boolean equals(Object obj) {
        if (! (obj instanceof TaskWrapper)) {
            return false;
        }

        TaskWrapper other = (TaskWrapper) obj;
        return id == other.id;
    }
}
