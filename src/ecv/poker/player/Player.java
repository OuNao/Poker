package ecv.poker.player;

import java.util.ArrayList;
import java.util.List;

import ecv.poker.R;
import ecv.poker.card.Card;
import ecv.poker.game.Game;

/*
 * TODO: abstract this to user and computer players?
 */
public class Player {
	private int chips;
	private List<Card> cards;
	private Game game;
	private String name;
    private int curBet = 0;
    private boolean Folded = false;

	public Player(Game game, String name, int startingChips) {
		this.game = game;
		this.name = name;
		chips = startingChips;
		cards = new ArrayList<Card>(2);
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return Number of chips player has left to play with
	 */
	public int getChips() {
		return chips;
	}
	
	public Game getGame() {
		return game;
	}

    public void resetBet() {curBet = 0;}

    public int getCurBet() {return curBet;}

    public boolean IsFolded() {return Folded;}

    public void resetFolded() {Folded = false;}

	/**
	 * @param i
	 *            number of chips to be added or deducted from player
	 */
	public void addChips(int i) {
		chips += i;
	}
	
	public void setChips(int i) {
		chips = i;
	}

	/**
	 * Throw away player's cards.
	 */
	public void fold() {
		cards.clear();
        Folded = true;
		game.setAction(Game.Action.FOLD);
		//game.setCurBet(0);
		String format = game.getView().getContext().getString(R.string.folded);
		game.getView().toast(String.format(format, name));
	}

	/**
	 * No action. Nothing to call or bet
	 */
	public void check() {
		game.setAction(Game.Action.CHECK);
		//game.setCurBet(0);
		String format = game.getView().getContext().getString(R.string.checked);
		game.getView().toast(String.format(format, name));
	}

	/**
	 * Call the current bet and deduct from chip stack
	 * 
	 */
	public void call() {
		game.getView().playSound(game.getView().chipSound);
		chips -= (game.getCurBet() - curBet);
		game.addToPot(game.getCurBet() - curBet);
        curBet = game.getCurBet();
		game.setAction(Game.Action.CALL);
		String format = game.getView().getContext().getString(R.string.called);
		game.getView().toast(String.format(format, name, game.getCurBet()));
		//game.setCurBet(0);
		
	}

	/**
	 * Set the current bet, add to pot and deduct from chip stack
	 * 
	 * @param bet
	 */
	public void bet(int bet) {
		game.getView().playSound(game.getView().chipSound);
		chips -= bet;
		game.addToPot(bet);
		game.setAction(Game.Action.BET);
		game.setCurBet(bet);
        curBet = game.getCurBet();;
		String format = game.getView().getContext().getString(R.string.bet);
		game.getView().toast(String.format(format, name, bet));
	}

	/**
	 * Call the current bet and raise additional
	 * 
	 * @param raise
	 */
	public void raise(int raise) {
		game.getView().playSound(game.getView().chipSound);
		chips -= ((game.getCurBet() + raise) - curBet);
		game.addToPot((game.getCurBet() + raise) - curBet);
		game.setAction(Game.Action.RAISE);
		game.setCurBet(raise + game.getCurBet());
        curBet = game.getCurBet();
		String format = game.getView().getContext().getString(R.string.raised);
		game.getView().toast(String.format(format, name, raise));
	}

	/**
	 * @return the player's hole cards
	 */
	public List<Card> getCards() {
		return cards;
	}
}
