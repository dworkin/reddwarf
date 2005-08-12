package com.sun.gi.comm.routing;

import com.sun.gi.utils.StatisticalUUID;



public class ChannelID extends StatisticalUUID {
	public ChannelID(long time, long tiebreaker){
		super(time,tiebreaker);
	}
}
