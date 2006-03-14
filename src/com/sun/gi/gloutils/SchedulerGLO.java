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

package com.sun.gi.gloutils;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.DeadlockException; 
import java.util.List;
import java.util.LinkedList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 */
public class SchedulerGLO implements GLO {
    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.gloutils");  
    private static final Class nullArgs[] = new Class[0];

    private final List<TaskDescriptor> tasks =
	    new LinkedList<TaskDescriptor>();
    private GLOReference<? extends SchedulerGLO> baseRef = null;
    private int pos = 0;

    protected SchedulerGLO(List<? extends TaskDescriptor> tasks) {
	if (tasks == null) {
	    throw new NullPointerException("task list is null");
	}

	// Nothing to do...
	if (tasks.isEmpty()) {
	    return;
	}

	validate(tasks);

	this.tasks.addAll(tasks);
    }

    public static void queueSequence(List<? extends TaskDescriptor> tasks) {
	SimTask simTask = SimTask.getCurrent();

	GLOReference<SchedulerGLO> schedule =
		simTask.createGLO(new SchedulerGLO(tasks));

	schedule.get(simTask).setBaseReference(schedule);

	Method processNext;
	try {
	    processNext = SchedulerGLO.class.getMethod("processNext",
		    nullArgs);
	} catch (NoSuchMethodException e) {
	    NoSuchMethodError err = new NoSuchMethodError();
	    err.initCause(e);
	    throw err;
	}

	simTask.queueTask(schedule, processNext, new Object[] { });
    }

    protected void setBaseReference(GLOReference<? extends SchedulerGLO> ref) {
	this.baseRef = ref;
    }

    // XXX: processNext needs to be exposed to the Simulator so that
    // it can get dispatched from the SimTask queue.  However, it should
    // never be called by developers, so ideally we would find a way
    // to hide it from them.
    // Inner classes have issues with Serializable, but if all we
    // need is access to this objects's task queue, that is already
    // a final field.
    // -jm
    public void processNext() throws Throwable {
	if (tasks.isEmpty()) {
	    return;
	}

	SimTask simTask = SimTask.getCurrent();
	TaskDescriptor td = tasks.remove(0);

	/* run the thing!!! */
	log.fine("processNext at position " + pos);

	GLOReference<? extends GLO> targetRef = td.getTarget();

	// GLO runobj = td.getTarget().get(simTask);
	GLO runobj = null;

	try {
	    switch (td.getAccessType()) {
		case GET:
		    runobj = targetRef.get(simTask);
		    break;
		case PEEK:
		    runobj = targetRef.peek(simTask);
		    break;
		case ATTEMPT:
		default:
		    // SHOULD NOT HAPPEN
		    log.severe("Access type ATTEMPT not allowed here");
		    // How do we get out of here?
		    runobj = null;
		    break;
	    }
	} catch (DeadlockException de) {
	    log.throwing(getClass().getName(), "preparing", de);
	    throw de;
	} catch (RuntimeException re) {
	    // what to do, what to do?
	    log.throwing(getClass().getName(), "preparing", re);
	    throw re;
	} catch (Exception e) {
	    // impossible?  What to do?
	    log.throwing(getClass().getName(), "preparing", e);
	    // throw e;
	}

	if (runobj == null) {
	    // XXX: Need to tell the world we failed.  How?
	    return;
	}

	Method subtaskMethod = null;
	try {
	    subtaskMethod = runobj.getClass().getMethod(td.getMethodName(),
		    nullArgs);
	} catch (NoSuchMethodException e) {
	    log.warning("getMethod failed for " + td.getMethodName());
	    // Need to actually communicate the error to the outside world.
	    return ;
	}

	if ((subtaskMethod == null) || (runobj == null)) {

	    /*
	     * XXX:  we're in deep trouble.  How do we tell the world
	     * about it?
	     */

	  return;
	}

	try {
	    subtaskMethod.invoke(runobj, td.getParams());
	} catch (InvocationTargetException ex) {
	    Throwable realException = ex.getCause();
	    log.throwing(getClass().getName(), "invoke", realException);

	    // throw the unwrapped exception.
	    throw realException;
	} catch (IllegalAccessException ia) {
	    // ulch.
	    log.throwing(getClass().getName(), "invoke", ia);
	    throw ia;
	}

	/*
	 * We survived.  Time to schedule the next item in the list.
	 */

	Method processNext;
	try {
	    processNext = SchedulerGLO.class.getMethod("processNext", nullArgs);
	} catch (NoSuchMethodException e) {
	    log.warning("getMethod failed for processNext");
	    // Need to actually communicate the error to the outside world.
	    return ;
	}

	// on to the next one.
	pos++;
	simTask.queueTask(baseRef, processNext, new Object[0]);
    }

    /**
     * Determines whether the given List of {@link TaskDescriptors} is
     * valid, or doomed. <p>
     *
     * This is first-order sanity checking, not true validation.  The
     * subtasks may behave in any arbitrary way during their invocation.
     * This only attempts to determine whether the machinery of
     * performing the invocation itself is going to fail.
     *
     * @param tasks the list of task descriptors for the tasks
     *
     * @return <code>true</code> if the list appears valid, <code>false</code>
     * otherwise
     */
    protected static void validate(List<? extends TaskDescriptor> tasks)
	    throws IllegalArgumentException
    {

	SimTask simTask = SimTask.getCurrent();

	// null list: prohibited.
	if (tasks == null) {
	    throw new IllegalArgumentException("task list is null");
	}

	// empty list: prohibited.
	if (tasks.size() == 0) {
	    throw new IllegalArgumentException("task list is empty");
	}

	int i = 0;
	for (TaskDescriptor td : tasks) {

	    // Don't know what to do about ATTEMPT, so prohibit
	    if (td.getAccessType() == ACCESS_TYPE.ATTEMPT) {
		throw new IllegalArgumentException("task " + i +
			" type is ATTEMPT");
	    }

	    // empty target: prohibited
	    if (td.getTarget() == null) {
		throw new IllegalArgumentException("task " + i +
			" target is null");
	    }

	    // make sure that the object can be accessed.
	    //
	    // I assume that it's fairly harmless to peek at an object

	    GLO runobj = td.getTarget().peek(simTask);
	    if (runobj == null) {
		throw new IllegalArgumentException("task " + i +
			" target cannot be peeked");
	    }

	    // there better be a name for this method...
	    String methodName = td.getMethodName();
	    if (methodName == null) {
		throw new IllegalArgumentException("task " + i +
			" method name is null");
	    }

	    // params might be empty, but not null (?)
	    if (td.getParams() == null) {
		throw new IllegalArgumentException("task " + i +
			" param list is null");
	    }

	    // Make sure we can find the method.
	    try {
		Method method = runobj.getClass().getMethod(methodName,
			nullArgs);
	    } catch (NoSuchMethodException e) {
		throw new IllegalArgumentException("task " + i +
			" method " + methodName + " cannot be found");
	    }

	    i++;
	}
    }
}

