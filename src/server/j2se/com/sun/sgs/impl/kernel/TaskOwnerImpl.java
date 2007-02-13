
package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;


/**
 * This is a simple implementation of <code>TaskOwner</code> that is nothing
 * more than a container for a name and a context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class TaskOwnerImpl implements TaskOwner {

    // the identity of the owner
    private final Identity identity;

    // the context of the owner
    private final KernelAppContext context;

    // a cache for the hash code
    private final int hash;

    /**
     * Creates an instance of <code>SimpleTaskOwner</code>.
     *
     * @param identity the <code>Identity</code> of the owner
     * @param context the context in which this owner runs tasks
     */
    public TaskOwnerImpl(Identity identity, KernelAppContext context) {
        this.identity = identity;
        this.context = context;

        // cache the hash code as the hash of the identity and the context
        hash = identity.hashCode() ^ context.hashCode();
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
    public Identity getIdentity() {
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
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (! (o instanceof TaskOwnerImpl))
            return false;

        TaskOwnerImpl other = (TaskOwnerImpl)o;

        return ((other.identity.equals(identity)) &&
                (other.context.equals(context)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return hash;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getClass().getName() + '[' +
            identity.getName() + "]@" +
            Integer.toHexString(hashCode());
    }
}
