package com.sun.sgs.kernel.scheduling;

import java.util.HashSet;
import java.util.Set;

/**
 * A <code>QueueingModel</code> represents an ordering strategy of a
 * priority based queue through containing a set of relatively
 * weighted, consistently ordered {@link Priority} options.
 *
 * @see PriorityPolicy
 * @since 1.0
 * @author David Jurgens
 */
public class QueueingModel {

    /**
     * The set of priorities contained in this model.
     */
    private Set<Priority> enabledPriorities;
    
    /**
     * Constructs a <code>QueueingModel</code> with the set of enabled
     * priorities.
     */
    public QueueingModel(Set<Priority> enabledPriorities) {
	this.enabledPriorities = enabledPriorities;
    }

    /**
     * Constructs a <code>QueueingModel</code> out of the unique
     * subset of the provided list of priorities.
     */
    public QueueingModel(Priority... priorities) {
	this.enabledPriorities = new HashSet<Priority>();
	for (Priority p : priorities)
	    enabledPriorities.add(p);
    }

    /**
     * Returns whether this <code>QueueingModel</code> contains the
     * specified priority.
     *
     * @param priority a priority that may be of any type
     *
     * @return true if this <code>QueueingModel</code> contains the
     *         specified priority
     */
    public boolean contains(Priority priority) {
	return enabledPriorities.contains(priority);
    }

    /**
     * Returns the <code>Priority</code> contained in this model that
     * is most similar to the specified priority, or <code>null</code>
     * if no priority in this model is comparable to the provided
     * priority.
     *
     * @param priority a priority of any type
     * 
     * @return the priority closest to the specified priority or
     *         <code>null</code> if no priority in this model is 
     *         comparable.
     *
     * @see Priority#getWeightedComparison(Priority)
     */
    public Priority getClosestPriority(Priority priority) {
	double closestDist = 0;
	Priority closest = null;
	for (Priority p : enabledPriorities) {
	    try {
		// getWeighedComparison() can return negative and
		// positive values depending on whether the priority
		// is higher or lower.  Since we only care about the
		// closest, use the absolute distance of this measure.
		double dist = Math.abs(p.getWeightedComparison(priority));
		if (dist < closestDist) {
		    dist = closestDist;
		    closest = p;
		}
	    }
	    catch (Error e) {
		// Ignore errors from uncomparable priorities.
	    }
	}
	return closest;
    }

    /**
     * Returns the set of valid priority options for this
     * <code>QueueingModel</code>.
     *
     * @return the set of valid <code>Priority</code> options
     */
    public Set<Priority> getPriorities() {
	// NOTE: should this return a copy of the priority set?
	return enabledPriorities;
    }
    
    public String toString() {
	return "QueueingModel with priorities [" + enabledPriorities + "]";
    }

}
