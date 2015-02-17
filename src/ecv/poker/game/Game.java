package ecv.poker.game;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import ecv.poker.R;
import ecv.poker.card.Card;
import ecv.poker.card.Evaluator;
import ecv.poker.player.AIPlayer;
import ecv.poker.player.Player;
import ecv.poker.view.GameView;

/**
 * A representation of a game of poker. A game has a players, a deck of cards,
 * and community cards all players can use, and a pot that goes to the winning
 * hand
 * 
 * @author Evan
 */
public class Game {

	/**
	 * An action a player can perform
	 */
	public static enum Action {
		FOLD, CHECK, CALL, BET, RAISE;
	}

	private Random random;
	private Player user;
	private AIPlayer bot, bot2;
	private List<Card> deck, communityCards;
	private int pot;
	private int curBet;
	private boolean handOver;
	private Action prevprevAction, prevAction, curAction;
	private int ante;
	private int startingChips;
	private GameView view;
    int turn;

	public Game(GameView view) {
		this.view = view;
		random = new Random();

		ante = view.getSettings().getInt("ante", 10);
		startingChips = view.getSettings().getInt("chips", 1000);
		
		deck = new ArrayList<Card>(52);
		for (int i = 100; i <= 400; i += 100) {
			for (int j = 2; j <= 14; j++) {
				deck.add(new Card(i + j));
			}
		}
		communityCards = new ArrayList<Card>(5);
		
		user = new Player(this, view.getResources().getString(R.string.you),
				startingChips);
		bot = new AIPlayer(this, view.getResources().getString(
				R.string.computer), startingChips);
        bot2 = new AIPlayer(this, view.getResources().getString(
                R.string.computer2), startingChips);
		
		turn = random.nextInt(3);
		handOver = false;
	}

	public void reset() {
		user.setChips(startingChips);
		bot.setChips(startingChips);
        bot2.setChips(startingChips);
        user.resetBet();
        bot.resetBet();
        bot2.resetBet();
        setupHand();
	}

	public GameView getView() {
		return view;
	}

	public Random getRandom() {
		return random;
	}

	/**
	 * Deal out cards to players and start the round
	 */
	public void setupHand() {
		view.playSound(view.shuffleSound);
		handOver = false;
		deck.addAll(user.getCards());
		deck.addAll(bot.getCards());
        deck.addAll(bot2.getCards());
		deck.addAll(communityCards);
		user.getCards().clear();
		bot.getCards().clear();
        bot2.getCards().clear();
		communityCards.clear();
		Collections.shuffle(deck, random);
		for (int i = 0; i < 2; i++) {
			user.getCards().add(deal());
			bot.getCards().add(deal());
            bot2.getCards().add(deal());
		}
		// post antes
		user.addChips(-ante);
		bot.addChips(-ante);
        bot2.addChips(-ante);
		pot = ante * 3;
		curBet = 0;
        user.resetBet();
        bot.resetBet();
        bot2.resetBet();
        user.resetFolded();
        bot.resetFolded();
        bot2.resetFolded();

		// bot can start evaluating hand
		bot.calculateExpectedValue();
        bot2.calculateExpectedValue();

        if (turn == 1) {
            Log.d("POKER", "Computer move");
            bot.makeMove();
        }
        else if (turn == 2) {
            Log.d("POKER", "Computer2 move");
            bot2.makeMove();
        }
        else {
            Log.d("POKER", "Player move");
        }
	}

	/**
	 * Deal next card if applicable and make the bot play, or end the hand
	 */
	public void makeNextMove() {
        if (((user.IsFolded() ? 1 : 0) + (bot.IsFolded()? 1 : 0) + (bot2.IsFolded()? 1 : 0)) > 1) {
            Log.d("POKER", ">1 Folded");
            endHand();
        }
        if (isBettingDone()) {
            dealNextCard();
            // starts a new round of betting, clear out previous actions
            prevprevAction = null;
            prevAction = null;
            curAction = null;
            curBet = 0;
            user.resetBet();
            bot.resetBet();
            bot2.resetBet();
        }
        if (turn == 1 && !handOver) {
            if (bot.IsFolded()) setNextTurn();
            else {
                Log.d("POKER", "Computer move");
                bot.makeMove();
            }
        }
        else if (turn == 2 && !handOver) {
            if (bot2.IsFolded()) setNextTurn();
            else {
                Log.d("POKER", "Computer2 move");
                bot2.makeMove();
            }
        }
        else {
            //if (user.IsFolded()) setNextTurn();
            //else {
                Log.d("POKER", "Player move");
            //}
        }
    }
	/**
	 * Flop deals out 3 cards at same time, Turn and river only deal one. End
	 * the hand if all 5 cards are already dealt
	 */
	public void dealNextCard() {
		if (communityCards.size() < 3) {
			view.playSound(view.dealSound);
			communityCards.add(deal());
			communityCards.add(deal());
			communityCards.add(deal());
			// if a player is all in, keep dealing out cards
			if (user.getChips() == 0 || bot.getChips() == 0 || bot2.getChips() == 0)
				dealNextCard();
			else {
                bot.calculateExpectedValue();
                bot2.calculateExpectedValue();
            }
		} else if (communityCards.size() < 5) {
			view.playSound(view.dealSound);
			communityCards.add(deal());
			if (user.getChips() == 0 || bot.getChips() == 0 || bot2.getChips() == 0)
				dealNextCard();
			else {
                bot.calculateExpectedValue();
                bot2.calculateExpectedValue();
            }
		} else
			endHand();
	}

	public boolean isHandOver() {
		return handOver;
	}

	/**
	 * A round of betting is complete when the last action is a check or call. A
	 * new card needs to come out or its the end of the hand.
	 * 
	 * @return
	 */
	public boolean isBettingDone() {
		if (!user.IsFolded() && !bot.IsFolded() && !bot2.IsFolded()) {
            Log.d("POKER", "0 Folded");
            if (curBet > 0) {
                if ((user.getCurBet() == curBet) && (bot.getCurBet() == curBet) && (bot2.getCurBet() == curBet))
                    return true;
            } else {
                return (prevprevAction == Action.CHECK && prevAction == Action.CHECK && curAction == Action.CHECK);
            }
        }
        else{
            Log.d("POKER", ">0 Folded");
            if (curBet > 0) {
                if (   ((user.getCurBet() == curBet) || user.IsFolded())
                        && ((bot.getCurBet() == curBet) || bot.IsFolded())
                        && ((bot2.getCurBet() == curBet) || bot2.IsFolded())        )
                    return true;
            } else {
                return (prevAction == Action.CHECK && curAction == Action.CHECK);
            }
        }
        return false;
	}

	/**
	 * Reset the deck, clear players' hands, award chips to the winner(s)
	 */
	public void endHand() {
		// determine who won
		int userRank = Evaluator.evaluate(user.getCards(), communityCards);
		int botRank = Evaluator.evaluate(bot.getCards(), communityCards);
        int bot2Rank = Evaluator.evaluate(bot2.getCards(), communityCards);
        /*if (user.IsFolded()) userRank = 0;
        if (bot.IsFolded()) botRank = 0;
        if (bot2.IsFolded()) bot2Rank = 0;*/

		String format = view.getResources().getString(R.string.award_chips);
		if ((userRank > botRank) && (userRank > bot2Rank)) {
			user.addChips(pot);
			view.toast(String.format(format, user.getName(), pot));
		} else if ((botRank > userRank) && (botRank > bot2Rank)) {
			bot.addChips(pot);
			view.toast(String.format(format, bot.getName(), pot));
        } else if ((bot2Rank > userRank) && (bot2Rank > botRank)) {
            bot2.addChips(pot);
            view.toast(String.format(format, bot2.getName(), pot));
		} else {
			if (user.IsFolded()){
                bot.addChips(pot / 2);
                bot2.addChips(pot / 2);
            }
            else if (bot.IsFolded()) {
                user.addChips(pot / 2);
                bot2.addChips(pot / 2);
            }
            else if (bot2.IsFolded()) {
                user.addChips(pot / 2);
                bot.addChips(pot / 2);
            }
            else {
                user.addChips(pot / 3);
                bot.addChips(pot / 3);
                bot2.addChips(pot / 3);
            }
			view.toast(view.getResources().getString(R.string.split_pot));
		}

		if (user.getChips() <= 0)
			view.makeEndGameDialog();
		else if (bot.getChips() <= 0)
			view.makeEndGameDialog();
        else if (bot2.getChips() <= 0)
            view.makeEndGameDialog();

		handOver = true;
	}

	public Card deal() {
		return deck.remove(deck.size() - 1);
	}

	public void setNextTurn() {
		this.turn += 1;
        if (this.turn == 3) this.turn = 0;
        makeNextMove();
    }
    public int getTurn() {
        return this.turn;
    }

	public List<Card> getDeck() {
		return deck;
	}

	public Player getUser() {
		return user;
	}

	public Player getBot() {
		return bot;
	}
    public Player getBot2() {
        return bot2;
    }

	public int getAnte() {
		return ante;
	}

    /**
	 * The smallest bet - either the min bet, or to put a player all-in
	 * 
	 * @return
	 */
	public int getMinBetAllowed() {
		if (user.getChips() < ante && user.getChips() <= bot.getChips() && user.getChips() <= bot2.getChips())
			return user.getChips();
		else if (bot.getChips() < ante && bot.getChips() <= bot2.getChips() && bot.getChips() <= user.getChips())
			return bot.getChips();
        else if (bot2.getChips() < ante && bot2.getChips() <= bot.getChips() && bot2.getChips() <= user.getChips())
            return bot2.getChips();
		else
			return ante;
	}

	/**
	 * The smaller value of user's or bot's chip stack
	 * 
	 * @return
	 */
	public int getMaxBetAllowed() {
		if (user.getChips() < bot.getChips() && user.getChips() < bot2.getChips())
			return user.getChips();
        else if (bot.getChips() < user.getChips() && bot.getChips() < bot2.getChips())
            return bot.getChips();
		else
			return bot2.getChips();
	}

	public void setAnte(int ante) {
		this.ante = ante;
	}

	public List<Card> getCommunityCards() {
		return communityCards;
	}

	/**
	 * @return Size of the pot for the current hand
	 */
	public int getPot() {
		return pot;
	}

	public void addToPot(int bet) {
		pot += bet;
	}

	public int getCurBet() {
		return curBet;
	}

	public void setCurBet(int curBet) {
		this.curBet = curBet;
	}

	/**
	 * Set the current action, The old value of curAction is sent to prevAction
	 * 
	 * @param action
	 */
	public void setAction(Action action) {
		prevprevAction = prevAction;
        prevAction = curAction;
		curAction = action;
	}
}
