package com.sun.gi.comm.routing;

import com.sun.gi.utils.StatisticalUUID;



public class ChannelID extends StatisticalUUID {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -820866582533419659L;
	public ChannelID(){
		super();
	}
	public ChannelID(long time, long tiebreaker){
		super(time,tiebreaker);
	}
}
