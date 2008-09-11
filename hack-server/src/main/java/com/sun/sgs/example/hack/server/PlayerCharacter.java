/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.io.Serializable;


/**
 * This is an implementation of <code>Character</code> used by all
 * <code>Player</code>s.
 */
public class PlayerCharacter implements Character, ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    // a reference to the player that owns this character
    private ManagedReference<Player> playerRef;

    // the id of this character
    private int id;

    // the statistics for this character
    private CharacterStats stats;

    /**
     * Creates an instance of <code>PlayerCharacter</code>.
     *
     * @param player the <code>Player</code> that owns this character
     * @param id the identifier for this character
     * @param stats the statistics for this character
     */
    public PlayerCharacter(Player player, int id, CharacterStats stats) {
        playerRef = AppContext.getDataManager().createReference(player);
        this.id = id;
        this.stats = stats;
    }

    /**
     * Returns this entity's identifier. Typically this maps to the sprite
     * used on the client-side to render this entity.
     *
     * @return the identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Returns the name of this entity.
     *
     * @return the name
     */
    public String getName() {
        return stats.getName();
    }

    /**
     * Returns the statistics associated with this character.
     *
     * @return the character's statistics
     */
    public CharacterStats getStatistics() {
        return stats;
    }

    /**
     * This method tells the character that their stats have changed. This
     * notifies the player of the change.
     */
    public void notifyStatsChanged() {
	AppContext.getDataManager().markForUpdate(this);

        // send the player our updated stats...
        Player player = playerRef.get();
        player.sendCharacter(this);

        // ...and check to see if we're still alive
        if (stats.getHitPoints() == 0) {
            // we were killed, so send a message...
            player.sendServerMessage("You died.  Now returning to the lobby");

            // ...remove ourself directly from the level, so there's no
            // confusion about interacting with us before we get called
            // back to do the removal...
            player.leaveCurrentLevel();

            stats.setHitPoints(stats.getMaxHitPoints());

            // NOTE: we could add some message screen here, if we
            //       wanted

            // finally, throw the players into the lobby
            Game lobby = (Lobby) AppContext.getDataManager().
                getBinding(Lobby.IDENTIFIER);
            AppContext.getTaskManager().
                scheduleTask(new MoveGameTask(player, lobby));
        }
    }

    /**
     * Called when a character collides into us. We always call back
     * the other character's <code>collidedInto</code> method, and then
     * check on the result. Since the player is interactive, we don't
     * automatically retaliate (although that could easily be added at
     * this point).
     *
     * @param character the character that collided with us
     *
     * @return the result of processing our interaction
     */
    public ActionResult collidedFrom(Character character) {
        // getting hit invokes a simple double-dispatch pattern, where
        // we call the other party and let them know that they hit us, and
        // then we react to this
        
        // remember out current hp count, and then call the other party
        int previousHP = stats.getHitPoints();
        if (character.collidedInto(this)) {
            // our stats were effected, so see if we lost any hit points
            Player player = playerRef.get();
            int lostHP = previousHP - stats.getHitPoints();
            if (lostHP > 0)
                player.sendServerMessage(character.getName() + " hit you for " +
                                       lostHP + "HP");

            // do the general stat notify routine
            notifyStatsChanged();
        }

        // regardless of what happened, we don't yield our ground yet
        return ActionResult.FAIL;
    }

    /**
     * Called when you collide into the character. This will always try
     * to attack the character.
     *
     * @return boolean if any statistics changed
     */
    public boolean collidedInto(Character character) {
	AppContext.getDataManager().markForUpdate(this);

        // FIXME: this isn't trying to use any stats at this point, it's
        // just using some testing values
        int damage = NSidedDie.roll6Sided();
        CharacterStats stats = character.getStatistics();
        int newHp = (damage > stats.getHitPoints()) ? 0 :
            stats.getHitPoints() - damage;
        stats.setHitPoints(newHp);

        return true;
    }

    /**
     * Sends a text message to the character's player.
     *
     * @param message the message to send
     */
    public void sendMessage(String message) {
        playerRef.get().sendServerMessage(message);
    }

}
