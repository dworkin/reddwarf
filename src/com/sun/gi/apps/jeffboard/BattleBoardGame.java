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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
		private boolean withdrawn = false;
		
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

		/**
		 * @param x
		 * @param y
		 */
		public String bomb(int x, int y) {
			for(Iterator<int[]> iter = cityList.iterator();iter.hasNext();){
				int[] pos = iter.next();
				if ((pos[0]==x)&&(pos[1]==y)){ // hit
					iter.remove();
					if (cityList.isEmpty()){
						return "LOSS";
					} else {
						return "HIT";
					}
				}
			}
			for(Iterator<int[]> iter = cityList.iterator();iter.hasNext();){
				int[] pos = iter.next();
				if ((Math.abs(pos[0]-x)<=1)&&
					(Math.abs(pos[1]-y)<=1)){  // near miss
						return "NEAR_MISS";
				}
			}
			return "MISS";
		}

		/**
		 * @return
		 */
		public boolean isAlive() {
			// TODO Auto-generated method stub
			return cityList.isEmpty();
		}

		/**
		 * 
		 */
		public void withdraw() {
			withdrawn = true;			
		}
	}
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	LinkedList<UserID> playingList = new LinkedList<UserID>();
	List<UserID> withdrawnList = new LinkedList<UserID>();
	Map<UserID,String> screenNames = 
		new HashMap<UserID,String>();
	Map<String,UserID> reverseScreenNames = 
		new HashMap<String,UserID>();	
	Map<UserID,GLOReference> idToGLORef = 
		new HashMap<UserID,GLOReference>();
	ChannelID controlChannel;
	Map<UserID,BattleMap> playerMaps = new HashMap<UserID,BattleMap>();
	private String gameName;
	private ChannelID gameChannel;
	private String turnOrderString;
	private int joinerCount=0;
	private int currentPlayer = 0;
	
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
		playingList.add(uid);
		idToGLORef.put(uid,playerRef);
	}

	/**
	 * @return
	 */
	public boolean isFull() {
		return (playingList.size()==MAX_PLAYERS);	
	}
	
	public void setScreenName(UserID uid, String screenName){
		SimTask task = SimTask.getCurrent();
		if (reverseScreenNames.get(screenName)!=null){
			task.sendData(controlChannel,new UserID[]{uid},
					ByteBuffer.wrap("already-joined".getBytes()),true);
			return;
		}
		screenNames.put(uid,screenName);	
		reverseScreenNames.put(screenName,uid);
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
					currentPlayer = 0;
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
		if (playingList.size()>1){
			withdrawnList.clear(); // temporary list to handle edge case
			UserID thisPlayer = playingList.get(currentPlayer);
			task.sendData(gameChannel,
					new UserID[]{thisPlayer},
					ByteBuffer.wrap("your-move".getBytes()),true);
			String outstr = "move-started "+screenNames.get(thisPlayer);
			for(UserID id : screenNames.keySet()){
				if (id!=thisPlayer){
					task.sendData(gameChannel,
							new UserID[]{id},
							ByteBuffer.wrap(outstr.getBytes()),true);
				}
			}
			currentPlayer++;
			currentPlayer %= playingList.size();
		} else {
			gameOver();
		}				
	}
	
	public void passMove(UserID uid){
		if (uid != playingList.get(currentPlayer)){
			System.err.println("BB ERROR: Non current player tried to pass");
			return;
		}
		SimTask task = SimTask.getCurrent();
		String outstr = "move-ended "+screenNames.get(uid)+" pass";
		for(UserID id : screenNames.keySet()){			
				task.sendData(gameChannel,
						new UserID[]{id},
						ByteBuffer.wrap(outstr.getBytes()),true);
			
		}
		nextMove(task);
	}
	
	public void makeMove(UserID from, String bombedPlayer,int x, int y){
		SimTask task = SimTask.getCurrent();
		UserID thisPlayer = playingList.get(currentPlayer);
		if (thisPlayer!=from){
			System.err.println("BB ERROR: Player "+from+
					" moved out of turn.");
			System.err.println("BB ERROR: Expected player "+thisPlayer);
			return;
		}
		UserID target = reverseScreenNames.get(bombedPlayer);
		if (withdrawnList.contains(target)){
			task.sendData(gameChannel,
					new UserID[]{thisPlayer},
					ByteBuffer.wrap("your-move".getBytes()),true);
			withdrawnList.clear();
			return;
			
		}
		withdrawnList.clear();
		if (target==null){
			System.err.println(
				"BB ERROR: Tried to bomb nonexistant player: "+bombedPlayer);
			passMove(thisPlayer);
			return;
		}
		if (!playingList.contains(target)){
			System.err.println(
					"BB ERROR: Tried to bomb dead player: "+bombedPlayer);
			passMove(thisPlayer);
			return;
		}
		BattleMap map = playerMaps.get(target);
		String result = map.bomb(x,y);
		if (!map.isAlive()){
			removePlayer(target);
		}
		String outstr = "move-ended "+screenNames.get(thisPlayer)+" bombed "+
			screenNames.get(target)+" "+result;          ;
		for(UserID id : screenNames.keySet()){			
				task.sendData(gameChannel,
						new UserID[]{id},
						ByteBuffer.wrap(outstr.getBytes()),true);			
		}
		nextMove(task);
	}
	
	public void withdraw(UserID uid){
		withdrawnList.add(uid);
		BattleMap map = playerMaps.get(uid);
		map.withdraw();
		removePlayer(uid);
	}


	/**
	 * @param uid 
	 * 
	 */
	private void removePlayer(UserID uid) {
		int idx = playingList.indexOf(uid);
		playingList.remove(uid);
		// adjust the current player index for moved players
		if (currentPlayer>=idx){
			currentPlayer--;
			if (currentPlayer<0){
				currentPlayer = playingList.size()-1;
			}
		}
	}

	/**
	 * 
	 */
	private void gameOver() {
		SimTask task = SimTask.getCurrent();
		UserID uid = playingList.get(currentPlayer);
		for(Entry<UserID,GLOReference> entry : idToGLORef.entrySet()){
			GLOReference ref = entry.getValue();
			BattleBoardPlayer player = 
				(BattleBoardPlayer)ref.get(task);
			if (uid.equals(entry.getKey())){
				player.wins++;
			} else {
				player.losses++;
			}
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
