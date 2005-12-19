/**
 * *******************************************************************
 * This Software is copyright INRIA. 1997.
 * 
 * INRIA holds all the ownership rights on the Software. The scientific
 * community is asked to use the SOFTWARE in order to test and evaluate
 * it.
 * 
 * INRIA freely grants the right to use the Software. Any use or
 * reproduction of this Software to obtain profit or for commercial ends
 * being subject to obtaining the prior express authorization of INRIA.
 * 
 * INRIA authorizes any reproduction of this Software
 * 
 * - in limits defined in clauses 9 and 10 of the Berne agreement for
 * the protection of literary and artistic works respectively specify in
 * their paragraphs 2 and 3 authorizing only the reproduction and quoting
 * of works on the condition that :
 * 
 * - "this reproduction does not adversely affect the normal
 * exploitation of the work or cause any unjustified prejudice to the
 * legitimate interests of the author".
 * 
 * - that the quotations given by way of illustration and/or tuition
 * conform to the proper uses and that it mentions the source and name of
 * the author if this name features in the source",
 * 
 * - under the condition that this file is included with any
 * reproduction.
 * 
 * Any commercial use made without obtaining the prior express agreement
 * of INRIA would therefore constitute a fraudulent imitation.
 * 
 * The Software beeing currently developed, INRIA is assuming no
 * liability, and should not be responsible, in any manner or any case,
 * for any direct or indirect dammages sustained by the user.
 * ******************************************************************
 */

/*
 * EntityTable.java - a table of entities indexed by ID.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 20 May 1998.
 * Updated: no.
 */
package inria.util;

import java.util.*;

/**
 * EntityTable contains a set of entities indexed by ID. An EntityTable is generally
 * cheaper and offers faster access than Hashtable. All entities are sorted
 * in the table by ID.
 */
public class EntityTable extends Vector {

    /**
     * constructs an EntityTable object.
     */
    public EntityTable() {
        super(8, 8);
    }

    /**
     * constructs an EntityTable object.
     * @param initialCapacity the initial capacity.
     * @param increments the increments for expanding the table.
     */
    public EntityTable(int initialCapacity, int increments) {
        super(initialCapacity, increments);
    }

    /**
     * adds the given entity to the table.
     * @param obj the entity to be added.
     */
    public void addEntity(Entity obj) {

        /*
         * keeps the list sorted according to the id.
         */
        int l = 0;                  /* low bound */
        int h = elementCount;       /* high bound */

        while ((h - l) > 0) {
            int m = (h + l) >> 1;
            Entity ent = (Entity) elementData[m];

            if (ent.getID() < obj.getID()) {
                l = m + 1;
            } else if (ent.getID() > obj.getID()) {
                h = m;
            } else {
                return;     /* already exists in cache */
            }
        }

        insertElementAt(obj, l);
    }

    /**
     * contains the entity.
     * @param id the entity ID.
     */
    public boolean containEntity(int id) {
        int l = 0;                  /* low bound */
        int h = elementCount;       /* high bound */

        while ((h - l) > 0) {
            int m = (h + l) >> 1;
            Entity obj = (Entity) elementData[m];

            if (obj.getID() < id) {
                l = m + 1;
            } else if (obj.getID() > id) {
                h = m;
            } else {
                return true;
            }
        }

        return false;
    }

    /**
     * gets the entity.
     * @param id the entity ID.
     */
    public Entity getEntity(int id) {
        int l = 0;                  /* low bound */
        int h = elementCount;       /* high bound */

        while ((h - l) > 0) {
            int m = (h + l) >> 1;
            Entity obj = (Entity) elementData[m];

            if (obj.getID() < id) {
                l = m + 1;
            } else if (obj.getID() > id) {
                h = m;
            } else {
                return obj;
            }
        }

        return null;
    }

    /**
     * remove the given entity from the table.
     * @param obj the entity to remove.
     */
    public void removeEntity(Entity obj) {
        for (int i = 0; i < elementCount; i++) {
            if (obj.equals(elementData[i])) {
                removeElementAt(i);

                return;
            }
        }
    }

    /**
     * remove the entity with the given ID from the table.
     * @param id the entity ID.
     */
    public void removeEntity(int id) {
        int l = 0;                  /* low bound */
        int h = elementCount;       /* high bound */

        while ((h - l) > 0) {
            int m = (h + l) >> 1;
            Entity obj = (Entity) elementAt(m);

            if (obj.getID() < id) {
                l = m + 1;
            } else if (obj.getID() > id) {
                h = m;
            } else {
                removeElementAt(m);

                return;
            }
        }
    }

}

