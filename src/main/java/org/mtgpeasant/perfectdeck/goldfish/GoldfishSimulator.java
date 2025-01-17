package org.mtgpeasant.perfectdeck.goldfish;

import com.google.common.base.Predicates;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.mtgpeasant.perfectdeck.common.cards.Cards;
import org.mtgpeasant.perfectdeck.common.cards.Deck;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;


@Builder
@Value
public class GoldfishSimulator {
    public enum Start {OTP, OTD, BOTH}

    @Builder.Default
    final int draw = 7;
    @Builder.Default
    final Start start = Start.BOTH;
    @Builder.Default
    final int iterations = 50000;
    @Builder.Default
    final int maxTurns = 20;
    @Builder.Default
    final boolean verbose = false;

    final Class<? extends DeckPilot> pilotClass;

    /**
     * TODO:
     * stats on mulligans & OTP + kill turn breakdown
     */
    @Builder
    @Getter
    public static class DeckStats {
        final Deck deck;
        final List<GameResult> results;
        final int iterations;

        /**
         * Lists all win turns matching the given predicate
         *
         * @param filter predicate
         * @return ordered list of win turns
         */
        public List<Integer> getWinTurns(Predicate<GameResult> filter) {
            return results.stream()
                    .filter(filter)
                    .map(GameResult::getEndTurn)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        /**
         * Counts the number of game results matching the given predicate
         *
         * @param filter predicate
         * @return total count of games matching the predicate
         */
        public long count(Predicate<GameResult> filter) {
            return results.stream()
                    .filter(filter)
                    .mapToInt(GameResult::getCount)
                    .sum();
        }

        /**
         * Computes average win turn among game results matching the given predicate
         *
         * @param filter predicate
         * @return average win turn
         */
        public double getAverageWinTurn(Predicate<GameResult> filter) {
            long sum = results.stream()
                    .filter(filter)
                    .mapToLong(result -> result.getEndTurn() * result.getCount())
                    .sum();
            long count = count(filter);
            return (double) sum / (double) count;
        }

        /**
         * <a href="https://en.wikipedia.org/wiki/Average_absolute_deviation">Mean absolute deviation</a> around average win turn
         */
        public double getWinTurnMAD(Predicate<GameResult> filter) {
            double avg = getAverageWinTurn(filter);
            double distanceSum = results.stream()
                    .filter(filter)
                    .mapToDouble(result -> Math.abs(avg - result.getEndTurn()) * result.getCount())
                    .sum();
            long count = count(filter);
            return distanceSum / (double) count;
        }

        /**
         * <a href="https://en.wikipedia.org/wiki/Standard_deviation">Standard deviation</a> around average win turn
         */
        public double getWinTurnSD(Predicate<GameResult> filter) {
            double avg = getAverageWinTurn(filter);
            double distanceSum = results.stream()
                    .filter(filter)
                    .mapToDouble(result -> ((double) result.getEndTurn() - avg) * ((double) result.getEndTurn() - avg) * result.getCount())
                    .sum();
            long count = count(filter);
            return Math.sqrt(distanceSum / (double) count);
        }

        /**
         * Lists all mulligans taken matching the given predicate
         *
         * @param filter predicate
         * @return ordered list of mulligans taken
         */
        public List<Integer> getMulligans(Predicate<GameResult> filter) {
            return results.stream()
                    .filter(filter)
                    .map(GameResult::getMulligans)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        public List<Integer> getMulligans() {
            return getMulligans(Predicates.alwaysTrue());
        }
    }


    public List<DeckStats> simulate(Iterable<Deck> decksProvider) {
        return StreamSupport.stream(decksProvider.spliterator(), false)
                .map(deck -> simulate(deck))
                .collect(Collectors.toList());
    }

    public DeckStats simulate(Deck deck) {
        List<GameResult> results = IntStream.range(0, iterations)
                .parallel()
                // simulate a game
                .mapToObj(idx -> simulateGame(deck, onThePlay(start, idx)))
                // aggregate results
                .collect(Collectors.groupingBy(Function.identity()))
                .entrySet().stream()
                .map(entry -> GameResult.builder()
                        .mulligans(entry.getKey().mulligans)
                        .onThePlay(entry.getKey().onThePlay)
                        .outcome(entry.getKey().outcome)
                        .endTurn(entry.getKey().endTurn)
                        .count(entry.getValue().size())
                        .build()
                )
                .collect(Collectors.toList());
        return DeckStats.builder().deck(deck).iterations(iterations).results(results).build();
    }

    private boolean onThePlay(Start start, int idx) {
        switch (start) {
            case OTP:
                return true;
            case OTD:
                return false;
            case BOTH:
            default:
                return idx % 2 == 0;
        }
    }

    GameResult simulateGame(Deck deck, boolean onThePlay) {
        // instantiate new game
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        Game game = new Game(onThePlay, writer);

        // instantiate deck pilot (from class)
        DeckPilot pilot = null;
        try {
            pilot = pilotClass.getConstructor(Game.class).newInstance(game);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't instantiate pilot", e);
        }

        writer.println("=====================");
        writer.println("=== New Game: " + (onThePlay ? "OTP" : "OTD") + " ===");
        writer.println("=====================");

        // 1: select opening hand
        while (true) {
            Cards library = deck.getMain().shuffle();
            Cards hand = library.draw(draw);
            if (pilot.keepHand(hand)) {
                game.keepHandAndStart(library, hand);
                break;
            }
            game.rejectHand(hand);
        }
        // 2: start and check mulligans have been taken
        pilot.start();

        if (game.getHand().size() > draw - game.getMulligans()) {
            throw new IllegalStateException("You shouldn't have " + game.getHand().size() + " cards in hand after " + game.getMulligans() + " mulligans.");
        }

        try {
            while (game.getCurrentTurn() <= maxTurns) {
                // start next turn
                game.startNextTurn();

                // untap
                pilot.untapPhase();

                // upkeep
                pilot.upkeepPhase();

                // draw (unless first turn on the play)
                if (!game.isOnThePlay() || game.getCurrentTurn() > 1) {
                    pilot.drawPhase();
                }

                // first main phase
                game.emptyPool();
                pilot.firstMainPhase();

                // combat phase
                game.emptyPool();
                pilot.combatPhase();

                // second main phase
                game.emptyPool();
                pilot.secondMainPhase();

                // end phase
                game.emptyPool();
                pilot.endingPhase();

                // check no more than 7 cards in hand
                if (game.getHand().size() > draw) {
                    throw new IllegalStateException("You shouldn't have " + game.getHand().size() + " cards in hand after ending phase.");
                }

                // check won
                String winReason = pilot.checkWin();
                if (winReason != null) {
                    writer.println("===> WIN: " + winReason);
                    return GameResult.builder()
                            .onThePlay(game.isOnThePlay())
                            .mulligans(game.getMulligans())
                            .outcome(GameResult.Outcome.WON)
                            .endTurn(game.getCurrentTurn())
//                            .reason(winReason)
                            .build();
                }
            }
            writer.println("===> MAX TURNS REACHED");
            return GameResult.builder()
                    .onThePlay(game.isOnThePlay())
                    .mulligans(game.getMulligans())
                    .outcome(GameResult.Outcome.TIMEOUT)
                    .endTurn(maxTurns + 1)
                    .build();
        } catch (Exception e) {
            throw new GameInternalError("An unexpected error occurred in a game\n\n" + output.toString(), e);
        } finally {
            if (verbose) {
                System.out.println(output.toString());
                System.out.println();
            }
        }
    }

    @EqualsAndHashCode(exclude = "count")
    @Builder
    @Value
    public static class GameResult {
        public enum Outcome {WON, TIMEOUT}

        final boolean onThePlay;
        final int mulligans;
        final Outcome outcome;
        final int endTurn;
        @Builder.Default
        final int count = 1;
    }
}
