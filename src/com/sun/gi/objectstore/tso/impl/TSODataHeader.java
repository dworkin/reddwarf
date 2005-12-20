/**
 *
 * <p>Title: TSODataHeader.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.impl;

import java.io.Serializable;

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
		public Serializable dataObject;
		public boolean free;
		
		public TSODataHeader(long time, long tiebreaker,SGSUUID uuid, Serializable obj){
			this.time =time;
			this.tiebreaker = tiebreaker;
			this.uuid = uuid;
			this.dataObject = obj;
			free = true;
		}
		
		public boolean before(TSODataHeader other){
			if (time<other.time){
				return true;
			} else if ((time==other.time)&&(tiebreaker<other.tiebreaker)){
				
			}
			return false;			
		}
	}