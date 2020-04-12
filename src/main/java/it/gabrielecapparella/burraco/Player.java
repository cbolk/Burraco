package it.gabrielecapparella.burraco;

import java.util.List;

public class Player {
	private CardSet hand;
	private Game game;
	private Team team;
	public String id;
	public Turn turn;
	private PlayerSession endpoint;

	public Player(Game game, Team team, int id) {
		this.game = game;
		this.team = team;
		this.id = String.valueOf(id);
	}

	public void drawCard() {
		if (!(this.turn==Turn.TAKE)) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "You cannot draw now."));
			return;
		}
		Card drawn = this.game.draw();
		if (drawn==null) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "Deck is empty."));
			return;
		}
		this.hand.add(drawn);
		this.sendMessage(new Message(MsgType.DRAW, "Player", drawn.toString()));
		this.game.broadcast(new Message(MsgType.DRAW, "Game", this.id));
		this.setTurn(Turn.DISCARD);
	}

	public void pickDiscard() {
		if (!(this.turn==Turn.TAKE)) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "You cannot pick now."));
			return;
		}
		List<Card> picked = this.game.pickDiscardPile();
		this.hand.addAll(picked);
		this.game.broadcast(new Message(MsgType.PICK, "Game", this.id));
		this.setTurn(Turn.DISCARD);
	}

	public void meld(CardSet cs, int runIndex) {
		if (!(this.turn==Turn.DISCARD)) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "You cannot meld now."));
			return;
		}
		for (Card c: cs) {
			if (!this.hand.contains(c)) {
				this.sendMessage(new Message(MsgType.CHAT, "Player", "You don't have those cards."));
				return;
			}
		}
		if (runIndex<0 && !cs.isLegitRun()) { // new run
			this.sendMessage(new Message(MsgType.CHAT, "Player", "Not a valid run."));
			return;
		}
		boolean willPot = cs.size()==this.hand.size();
		boolean willBurraco = this.team.willBurraco(cs.size(), runIndex);
		if(willPot && this.team.potTaken && !willBurraco) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "Cannot remain without cards."));
			return;
		}
		runIndex = this.team.meld(cs, runIndex);
		if (runIndex<0) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "Not a valid run."));
			return;
		}

		for (Card c: cs) { // .removeAll() would remove duplicates too
			this.hand.remove(c);
		}

		CardSet newRun = this.team.getRun(runIndex);
		this.game.broadcast(new Message(MsgType.MELD, this.id, runIndex+";"+newRun.toString()));

		if (willPot) {
			this.setHand(this.team.getPot()); // the setter notifies the client
			this.game.broadcast(new Message(MsgType.POT, this.id, null));
		}
	}

	public void discard(Card c) {
		if (!(this.turn==Turn.DISCARD)) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "You cannot discard now."));
			return;
		}
		if (!this.hand.contains(c)) {
			this.sendMessage(new Message(MsgType.CHAT, "Player", "You don't own that card."));
			return;
		}

		boolean willEmpty = this.hand.size()==1;
		this.hand.remove(c);
		this.game.discard(this, c);
		this.game.broadcast(new Message(MsgType.DISCARD, this.id, c.toString()));

		if (willEmpty && !this.team.potTaken) {
			this.setHand(this.team.getPot()); // the setter notifies the client
			this.game.broadcast(new Message(MsgType.POT, this.id, null));
		} else if (willEmpty && this.team.canClose()){
			this.team.points += 100;
			this.game.closeRound();
		}
		this.setTurn(Turn.NOPE);
	}

	public void payHandPoints() {
		this.team.points -= this.hand.countPoints();
	}

	public boolean sendMessage(Message msg) {
		if (this.endpoint==null) return false;
		return this.endpoint.send(msg);
	}

	public void setEndpoint(PlayerSession ps) {
		this.endpoint = ps;
		this.sendMessage(new Message(MsgType.JOIN, "Player", this.id));
	}

	public void setHand(CardSet hand) {
		this.hand = hand;
		this.sendMessage(new Message(MsgType.HAND, "Player", hand.toString()));
	}

	public void setTurn(Turn t) {
		this.turn = t;
		this.game.broadcast(new Message(MsgType.TURN, this.id, t.name()));
	}
}
