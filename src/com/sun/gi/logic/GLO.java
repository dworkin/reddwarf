package com.sun.gi.logic;

import java.io.Serializable;

/**
 * <p>Title: GLO</p>
 * 
 * <p>Description: All GLOs (Game Logic Objects) should implement this interface.
 * GLOs are the basic unit of a game's presence on the SGS.  The only constraint
 * on a GLO is that it be Serializable to facilitate storage on the back-end.
 *
 * All GLOs must refer to other GLOs through GLOReferences.  This allows
 * the persistance mechanisms to work correctly.  If a GLO has a normal java
 * reference to another GLO as a field then that second GLO's state becoems
 * part of the state of the first GLO as oppsoed to an independant object
 * in the objectstore.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public interface GLO extends Serializable {

}
