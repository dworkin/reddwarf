/**
 *
 * <p>Title: BattleBoardGame.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.jeffboard;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 *
 * <p>Title: BattleBoardGame.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class BattleBoardGame implements GLO{
	private static final int CITY_COUNT = 5;
	private static final int BOARD_WIDTH = 10;
	private static final int BOARD_HEIGHT = 8;
	static final int MAX_PLAYERS = 3; // 3 players per game
	
	class BattleMap {
		List<int[]> cityList = new ArrayList<int[]>();
		
		public BattleMap(){
			int count = 0;
			while (count<CITY_COUNT){
				int x = (int)(Math.random()*BOARD_WIDTH);
				int y = (int)(Math.random()*BOARD_HEIGHT);
				boolean found = false;
				for(int[] city : cityList){
					if ((city[0]==x)&&(city[1]==y)){ // duplicate
						found = true;
						break;
					}
				}
				if (!found){
					cityList.add(new int[] {x,y});
					count++;
				}
			}
		}

		/**
		 * @return
		 */
		public List<int[]> getCityList() {
			return cityList;
		}
	}
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Map<UserID,GLOReference> playerList = new LinkedHashMap<UserID,GLOReference>();
	Map<UserID,String> screenNames = 
		new HashMap<UserID,String>();
	ChannelID controlChannel;
	Map<UserID,BattleMap> playerMaps = new HashMap<UserID,BattleMap>();
	private String gameName;
	private ChannelID gameChannel;
	private String turnOrderString;
	private int joinerCount=0;
	private Iterator<UserID> playerIterator;
	private UserID currentPlayer;
	
	public BattleBoardGame(ChannelID controlChannel,String gameName){
		this.controlChannel = controlChannel;
		this.gameName = gameName;
	}
	
	/**
	 * @param playerRef
	 */
	public void addPlayer(GLOReference playerRef, UserID uid) {
		if (isFull()) {
			System.err.print(
					"BATTLEBOARD ERROR: Tried to add too many players");
			return;
		}
		playerList.put(uid,playerRef);		
	}

	/**
	 * @return
	 */
	public boolean isFull() {
		return (playerList.size()==MAX_PLAYERS);	
	}
	
	public void setScreenName(UserID uid, String screenName){
		SimTask task = SimTask.getCurrent();
		if (screenNames.values().contains(screenName)){
			task.sendData(controlChannel,new UserID[]{uid},
					ByteBuffer.wrap("already-joined".getBytes()),true);
			return;
		}
		screenNames.put(uid,screenName);	
		setupBoard(task,uid);		
		if (screenNames.size()==MAX_PLAYERS){
			// all screen names set, lets start playing!
			joinPlayers(task);
		}
	}

	/**
	 * 
	 */
	private void joinPlayers(SimTask task) {
		StringBuffer out = new StringBuffer("turn-order");
		for(String screenName : screenNames.values()){
			out.append(" "+screenName);
		}		
		turnOrderString = out.toString();
		// open a game channel
		gameChannel = task.openChannel("bb_"+gameName);
		// lock it for security
		task.lock(gameChannel,true);
		for(UserID uid : screenNames.keySet()){
			joinerCount++;
			task.join(uid,gameChannel);
		}
	}
	
	private void joinedChannel(UserID uid, ChannelID cid){
		SimTask task = SimTask.getCurrent();
		if (cid == gameChannel){
			if (screenNames.containsKey(uid)){
				joinerCount--;
				task.sendData(gameChannel,new UserID[]{uid},
						ByteBuffer.wrap(turnOrderString.getBytes()),true);
				if (joinerCount==0){
					// everyones here, lets play!
					playerIterator = screenNames.keySet().iterator();
					// make current player last one in iterator
					while(playerIterator.hasNext()){
						currentPlayer = playerIterator.next();
					}
					nextMove(task);
				}
			} else { // alien, boot em
				task.leave(uid,cid);
			}
		}
	}

	/**
	 * 
	 */
	private void nextMove(SimTask task) {
		int deadPlayerCounter=0;
		UserID lastPlayer = currentPlayer;
		currentPlayer =null;
		boolean stillPlaying = false;
		while(currentPlayer!=lastPlayer){
			if (!playerIterator.hasNext()){
				playerIterator = screenNames.keySet().iterator();
			}
			currentPlayer = playerIterator.next();
			if ((currentPlayer!=lastPlayer)&&(currentPlayer.isAlive()){
				stillPlaying=true;
				break;
			}
		}
		if (stillPlaying){
			task.sendData(currentPlayer,new UserID[]{to},
					ByteBuffer.wrap("your-move".getBytes()),true);
		} else {
			
		}
		
		
	}

	/**
	 * @param uid
	 */
	private void setupBoard(SimTask task ,UserID uid) {
		BattleMap map = new BattleMap();
		playerMaps.put(uid,map);
		StringBuffer out = new StringBuffer(
				"ok"+BOARD_WIDTH+" "+BOARD_HEIGHT+" "+
				CITY_COUNT);
		for(int[] city : map.getCityList()){
			out.append(" "+city[0]+" "+city[1]);
		}
		task.sendData(controlChannel,new UserID[]{uid},
				ByteBuffer.wrap(out.toString().getBytes()),true);
		
	}

}
