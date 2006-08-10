package com.sun.sgs.kernel.scheduling;

/**
 * A <code>Priority</code> denotes the ordering category that a
 * prioritizeable object with which it should be enqueued.
 *
 * Priorities extend {@link Comparable} so that a unique ordering
 * exists among a set of <code>Priority</code> objects.  However, each
 * <code>Priority</code> must also provide a relative priority
 * distance between itself and another priority; an ordering is
 * necessary but not sufficient enough to reflect the semantic
 * distance between neighboring priorities for any subset of the total
 * priority ordering.  For this reason a <code>Priority</code> must
 * define the {@link Priority#getWeightedComparison(Priority)}
 * function.  This behavior can lead to non-linear priority models for
 * systems that require it.
 *
 * For the global set of priorities, each priority must know about all
 * other priorities for {@link Priority#getWeightedComparison(Priority)} to
 * properly evaluate.  This implies that no unknown priorities are
 * injected into the system at runtime.  Should this happen, the
 * run-time behavior is left up to the developer.
 *
 * @see QueueingModel
 * @see PriorityPolicy
 * @since 1.0
 * @author David Jurgens
 */
public interface Priority extends Comparable {

    /**
     * Compares this object with the specified object for order and if
     * <code>other</code> is of type {@link Priority} returns an
     * ordering consistent with {@link getWeightedComparison(Priority}.
     */
    public int compareTo(Object other);

    /**
     * Returns the relative distance between this priority and
     * <code>other</code>.  In keeping with the precedent set by
     * {@link compareTo(Object)}, if this priority is of higher
     * priority than <code>other</code> than a negative value,
     * <code>0</code> if the same priority, and a positive value if of
     * lower priority.  
     *
     * This function <i>must</i> return a total ordering that is the
     * same as {@link compareTo(Object)}.  It should also be
     * transitive and idempotent for a global set of priorities.
     *
     * If <code>other</code> is not comparable to this priority, then
     * the result is undefined and left up to the implementation
     *
     * @param other the <code>Priority</code> to be compared with this priority
     *
     * @return a negative value if this is of higher priority than
     * <code>other</code>, <code>0</code> if this is of equal
     * priority, or a negative value if this is of lower priority.
     */
    public double getWeightedComparison(Priority other);

}
