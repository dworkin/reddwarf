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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.gi.transition.PeriodicTaskHandle;
import com.sun.gi.transition.Task;
import com.sun.gi.transition.TaskManager;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;

public class TaskManagerImpl implements TaskManager {
    public static final String TASKLISTNAME = "_SGS_TaskManagerData";
    private static Logger log = Logger.getLogger("com.sun.gi.transition");
    
    private final Map<GLOReference<? extends TaskWrapper>, Long> timerMap;

    private final Method callbackMethod;
    
    private GLOReference<TaskList> taskListRef;
    
    public TaskManagerImpl() {
        timerMap = new HashMap<GLOReference<? extends TaskWrapper>, Long>();
        try {
            callbackMethod =
                    SimTimerListener.class.getMethod("timerEvent",
                    new Class[] { Long.TYPE });
        } catch (NoSuchMethodException ex) {
            NoSuchMethodError err = new NoSuchMethodError();
            err.initCause(ex);
            throw err;
        }
    }
    
    public void setTaskListRef(GLOReference<TaskList> ref) {
        taskListRef = ref;
    }
    
    public void persistTask(GLOReference<? extends TaskWrapper> ref) {
        SimTask simTask = SimTask.getCurrent();
        TaskList taskList = taskListRef.get(simTask);
        taskList.add(ref);
    }

    public void depersistTask(TaskWrapper wrapper) {
        SimTask simTask = SimTask.getCurrent();
        TaskList taskList = taskListRef.get(simTask);
        GLOReference<? extends TaskWrapper> ref =
                simTask.lookupReferenceFor(wrapper);
        taskList.remove(ref);
        
        if (wrapper instanceof PeriodicTaskWrapper) {
            Long timerID = timerMap.get(ref);
            if (timerID != null) {
                // @@ NOTE @@
                // We cannot remove this wrapper's entry from the map, because
                // this transaction might fail and we may be asked for its
                // timerID in a future (successful) transaction.
                // TODO: clean up stale entries from time to time.
                simTask.deregisterTimerEvent(timerID);
                log.log(Level.FINER, "Cancel timer for {0} id {1}",
                        new Object[] { wrapper, timerID });
            }
        }
    }

    public void scheduleTask(Task task) {
        SimTask simTask = SimTask.getCurrent();

        long deadline = System.currentTimeMillis();
        
        GLOReference<TaskWrapper> taskWrapperRef =
                TaskWrapper.wrapTask(task, deadline);
        
        persistTask(taskWrapperRef);
        
        // Queue the TimerWrapper with access GET since it will need
        // to be locked anyway for deletion.
        simTask.queueTask(SimTask.ACCESS_TYPE.GET,
                taskWrapperRef,
                callbackMethod,
                new Object[] { new Long(-1) });
    }

    public void scheduleTask(Task task, long delay) {
        SimTask simTask = SimTask.getCurrent();
        
        long deadline = System.currentTimeMillis() + delay;
        
        GLOReference<TaskWrapper> taskWrapperRef =
                TaskWrapper.wrapTask(task, deadline);

        persistTask(taskWrapperRef);

        // Queue the TimerWrapper with access GET since it will need
        // to be locked anyway for deletion.
        simTask.registerTimerEvent(SimTask.ACCESS_TYPE.GET,
                delay,
                false,
                taskWrapperRef);
    }

    public PeriodicTaskHandle schedulePeriodicTask(Task task,
            long delay,
            long period)
    {
        SimTask simTask = SimTask.getCurrent();
        
        long deadline = System.currentTimeMillis() + delay;
        
        GLOReference<PeriodicTaskWrapper> wrapperRef = 
                PeriodicTaskWrapper.create(task, deadline, period);
        
        // We can register with access PEEK here, since the
        // PeriodicTaskWrapper won't need to be modified.
        Long timerID =
                simTask.registerTimerEvent(SimTask.ACCESS_TYPE.GET,
                    delay,
                    false,
                    wrapperRef);

        persistTask(wrapperRef);
        setTaskTimerID(wrapperRef, timerID);
        
        return new PeriodicTaskHandleImpl(wrapperRef);
    }
    
    public void setTaskTimerID(GLOReference<? extends TaskWrapper> ref,
            long newTimerID)
    {
        SimTask simTask = SimTask.getCurrent();
        
        log.log(Level.FINEST, 
                "Update timer for {0} ID {1}",
                new Object[] { ref.peek(simTask), newTimerID });

        timerMap.put(ref, newTimerID);
    }
}
