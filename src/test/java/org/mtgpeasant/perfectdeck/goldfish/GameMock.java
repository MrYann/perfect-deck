package org.mtgpeasant.perfectdeck.goldfish;

import org.mtgpeasant.perfectdeck.common.cards.Cards;

import java.io.PrintWriter;

public class GameMock {
    public static Game mock(boolean onThePlay, Cards hand, Cards library, Cards graveyard, Cards board, Cards exile) {
        Game game = new Game(onThePlay, new PrintWriter(System.out));
        game.keepHandAndStart(library, hand);
        game.getGraveyard().addAll(graveyard);
        game.getBoard().addAll(board);
        game.getExile().addAll(exile);
        return game;
    }
}
