/**
 *
 * <p>Title: TSODataHeader.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sun.gi.utils.SGSUUID;

/**
 *
 * <p>Title: TSODataHeader.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TSODataHeader  implements Serializable{
		/**
		 * 
		 */
		private static final long serialVersionUID = 5666201439015976463L;
		public long time;
		public long tiebreaker;
		public SGSUUID uuid;
		public boolean free;
		public Set<SGSUUID> availabilityListeners;
		public long objectID;
		
		public TSODataHeader(long time, long tiebreaker,SGSUUID uuid, long objID){
			this.time =time;
			this.tiebreaker = tiebreaker;
			this.uuid = uuid;
			this.objectID = objID;
			free = true;
			availabilityListeners = new HashSet<SGSUUID>();
		}
		
		public boolean before(TSODataHeader other){
			if (time<other.time){
				return true;
			} else if ((time==other.time)&&(tiebreaker<other.tiebreaker)){
				
			}
			return false;			
		}
	}