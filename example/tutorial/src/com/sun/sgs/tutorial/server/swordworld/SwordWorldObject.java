package com.sun.sgs.tutorial.server.swordworld;

import java.io.Serializable;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;

/**
 * A {@code ManagedObject} that has a name and a description.
 */
public class SwordWorldObject
    implements Serializable, ManagedObject
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The name of this object. */
    private String name;

    /** The description of this object. */
    private String description;

    /**
     * Creates a new {@code SwordWorldObject} with the given {@code name}
     * and {@code description}.
     *
     * @param name the name of this object
     * @param description the description of this object
     */
    public SwordWorldObject(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Sets the name of this object.
     *
     * @param name the name of this object
     */
    public void setName(String name) {
        AppContext.getDataManager().markForUpdate(this);
        this.name = name;
    }

    /**
     * Returns the name of this object.
     *
     * @return the name of this object
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the description of this object.
     *
     * @param description the description of this object
     */
    public void setDescription(String description) {
        AppContext.getDataManager().markForUpdate(this);
        this.description = description;
    }

    /**
     * Returns the description of this object.
     *
     * @return the description of this object
     */
    public String getDescription() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getName();
    }
}
