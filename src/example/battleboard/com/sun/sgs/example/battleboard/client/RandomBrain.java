package com.sun.sgs.example.battleboard.client;

import com.sun.sgs.example.battleboard.BattleBoard;
import java.util.Random;

/**
 * A {@link BattleBrain} that chooses moves at random.
 */
public class RandomBrain implements BattleBrain {

    /**
     * Local source of randomness.
     */
    private Random random = new Random();

    /**
     * Chooses a random move from unknown, vacant, or city positions
     * among the remaining boards.  <p>
     *
     * There's no strategy here; it doesn't try to be clever about
     * choosing positions that are already known to be "near" a city.
     * Will also behave badly if there are no possible moves, or
     * if the array of boards is empty.
     *
     * @param boards the boards from which to choose a position to
     * bomb
     *
     * @return an array of three strings that represent the name of the
     * player to bomb and the x and y positions on their board
     */
    @SuppressWarnings("null")
    public String[] getMove(BattleBoard[] boards) {
	BattleBoard.PositionValue pv = BattleBoard.PositionValue.HIT;
	BattleBoard board;
	int x = 0;
	int y = 0;

	do {
	    board = boards[random.nextInt(boards.length)];
	    if (board == null) {
		continue;
	    }
	    x = random.nextInt(board.getWidth());
	    y = random.nextInt(board.getHeight());
	    pv = board.getBoardPosition(x, y);
	} while (pv != BattleBoard.PositionValue.UNKNOWN &&
		pv != BattleBoard.PositionValue.VACANT &&
		pv != BattleBoard.PositionValue.CITY);

	return new String[] {
		board.getPlayerName(),
		String.valueOf(x),
		String.valueOf(y)
	};
    }
}
