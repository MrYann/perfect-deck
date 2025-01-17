package org.mtgpeasant.perfectdeck.goldfish;

import org.mtgpeasant.perfectdeck.common.cards.Cards;

/**
 * The abstract class you have to extend to implement your own goldfish player.
 */
public abstract class DeckPilot {
    protected final Game game;

    public DeckPilot(Game game) {
        this.game = game;
    }

    /**
     * Determines whether the given hand should be kept
     *
     * @param hand opening hand
     * @return {@code true} if the hand should be kept
     */
    public abstract boolean keepHand(Cards hand);

    /**
     * Starts a game
     * <p>
     * Mulligans taken must be applied (N cards sent on the bottom of the library)
     * <p>
     * Default implementation puts random cards on bottom of library
     */
    public void start() {
        for (int i = 0; i < game.getMulligans(); i++) {
            game.putOnBottomOfLibrary(game.getHand().getFirst());
        }
    }

    /**
     * Untap phase
     * <p>
     * Default implementation untaps all permanents
     * <p>
     * Override if necessary
     */
    public void untapPhase() {
        game.untapAll();
    }

    /**
     * Upkeep phase
     * <p>
     * Default implementation does nothing
     * <p>
     * Override if necessary
     */
    public void upkeepPhase() {

    }

    /**
     * Upkeep phase
     * <p>
     * Default implementation draws one card
     * <p>
     * Override if necessary
     */
    public void drawPhase() {
        game.draw(1);
    }

    /**
     * First main phase
     * <p>
     * Default implementation does nothing
     * <p>
     * Override if necessary
     */
    public void firstMainPhase() {

    }

    /**
     * Combat phase
     * <p>
     * Default implementation does nothing
     * <p>
     * Override if necessary
     */
    public void combatPhase() {

    }

    /**
     * Second main phase
     * <p>
     * Default implementation does nothing
     * <p>
     * Override if necessary
     */
    public void secondMainPhase() {

    }

    /**
     * Ending phase
     * <p>
     * Default implementation discards random cards until 7 in hand
     * <p>
     * Override if necessary
     */
    public void endingPhase() {
        while (game.getHand().size() > 7) {
            game.discard(game.getHand().getFirst());
        }
    }

    /**
     * Checks whether win conditions are reached
     * <p>
     * Default implementation checks whether opponent has zero or less life or 10 or more poison counters
     * <p>
     * Override if necessary
     */
    public String checkWin() {
        if (game.getOpponentLife() <= 0) {
            return "opponent is dead";
        }
        if (game.getOpponentPoisonCounters() >= 10) {
            return "opponent is deadly poisoned";
        }
        return null;
    }
}
