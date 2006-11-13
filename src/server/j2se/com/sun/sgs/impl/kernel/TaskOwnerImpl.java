
package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;


/**
 * This is a simple implementation of <code>TaskOwner</code> that is nothing
 * more than a container for a name and a context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class TaskOwnerImpl implements TaskOwner {

    // the name of the owner
    private String identity;

    // the context of the owner
    private KernelAppContext context;

    // a cache for the hash code
    private int hash;

    /**
     * Creates an instance of <code>SimpleTaskOwner</code>.
     *
     * @param identity the name of the owner
     * @param context the context in which this owner runs tasks
     */
    TaskOwnerImpl(String identity, KernelAppContext context) {
        this.identity = identity;
        this.context = context;

        // cache the hash code as the hash of the identity and the context
        hash = (identity + context.toString()).hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public KernelAppContext getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    public String getIdentity() {
        return identity;
    }

    /**
     * Returns <code>true</code> if the object is an instance of
     * <code>TaskOwnerImpl</code> and represents the same owner.
     *
     * @param o the other owner
     *
     * @return <code>true</code> if the given owner is the same as this
     *         owner, <code>false</code> otherwise
     */
    public boolean equals(Object o) {
        if (! (o instanceof TaskOwnerImpl))
            return false;

        TaskOwnerImpl other = (TaskOwnerImpl)o;

        return ((other.identity.equals(identity)) &&
                (other.context.equals(context)));
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return hash;
    }

}
