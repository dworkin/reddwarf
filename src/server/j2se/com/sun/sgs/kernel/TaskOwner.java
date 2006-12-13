
package com.sun.sgs.kernel;

import com.sun.sgs.auth.Identity;


/**
 * This interface provides details about the owner of a task. All tasks run
 * through the <code>TaskScheduler</code> have a <code>TaskOwner</code>. The
 * owner may be a user (i.e., a <code>ClientSession</code>), or it may be some
 * component of the system (e.g., a <code>Service</code>).
 * <p>
 * All implementations of <code>TaskOwner</code> must implement
 * <code>hashCode</code> and <code>equals</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface TaskOwner
{

    /**
     * Returns the context in which this <code>TaskOwner</code>'s tasks run.
     *
     * @return the <code>TaskOwner</code>'s <code>KernelAppContext</code>
     */
    public KernelAppContext getContext();

    /**
     * Returns the <code>Identity</code> for this <code>TaskOwner</code>.
     *
     * @return this task's owner's <code>Identity</code>
     */
    public Identity getIdentity();

}
