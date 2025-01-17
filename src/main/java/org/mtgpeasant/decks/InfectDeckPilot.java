package org.mtgpeasant.decks;

import lombok.Builder;
import lombok.Value;
import org.mtgpeasant.perfectdeck.common.Mana;
import org.mtgpeasant.perfectdeck.common.cards.Cards;
import org.mtgpeasant.perfectdeck.common.matchers.MulliganRules;
import org.mtgpeasant.perfectdeck.common.utils.Permutations;
import org.mtgpeasant.perfectdeck.goldfish.DeckPilot;
import org.mtgpeasant.perfectdeck.goldfish.Game;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InfectDeckPilot extends DeckPilot {

    public static final Mana G = Mana.of("G");
    public static final Mana G1 = Mana.of("1G");
    public static final Mana GG = Mana.of("GG");
    public static final Mana TWO = Mana.of("2");

    // LANDS
    public static final String FOREST = "forest";
    public static final String PENDELHAVEN = "pendelhaven";

    // CREATURES
    public static final String ICHORCLAW_MYR = "ichorclaw myr";
    public static final String GLISTENER_ELF = "glistener elf";
    public static final String BLIGHT_MAMBA = "blight mamba";

    // BOOSTS
    public static final String RANCOR = "rancor"; //
    public static final String SEAL_OF_STRENGTH = "seal of strength"; // (enchant) G: sacrifice: +3/+3
    public static final String SCALE_UP = "scale up"; // G: crea become 6/4
    public static final String VINES_OF_VASTWOOD = "vines of vastwood"; // (instant) GG: +4/+4
    public static final String GIANT_GROWTH = "giant growth";
    public static final String LARGER_THAN_LIFE = "larger than life"; // 1G: +4/+4
    public static final String INVIGORATE = "invigorate"; // (free if forest on board) +4/+4
    public static final String MUTAGENIC_GROWTH = "mutagenic growth"; // (-2 life): +2/+2
    public static final String GROUNDSWELL = "groundswell"; // G: +2/+2; landfall: +4/+4
    public static final String RANGER_S_GUILE = "ranger's guile"; // G: +1/+1
    public static final String MIGHT_OF_OLD_KROSA = "might of old krosa"; // G: +4/+4 on your turn
    public static final String BLOSSOMING_DEFENSE = "blossoming defense"; // G: +2/+2

    // FREE MANA
    public static final String LOTUS_PETAL = "lotus petal";

    // OTHERS
    public static final String GITAXIAN_PROBE = "gitaxian probe";
    public static final String MENTAL_MISSTEP = "mental misstep";
    public static final String APOSTLE_S_BLESSING = "apostle's blessing";

    private static String[] MANA_PRODUCERS = new String[]{PENDELHAVEN, FOREST, LOTUS_PETAL};
    private static String[] CREATURES = new String[]{GLISTENER_ELF, ICHORCLAW_MYR, BLIGHT_MAMBA};

    private static MulliganRules rules;

    static {
        try {
            rules = MulliganRules.parse(new InputStreamReader(InfectDeckPilot.class.getResourceAsStream("/infect-rules.txt")));
        } catch (IOException e) {
            rules = null;
            System.err.println(e);
        }
    }

    public InfectDeckPilot(Game game) {
        super(game);
    }

    @Override
    public boolean keepHand(Cards hand) {
        if (game.getMulligans() >= 3) {
            return true;
        }
        return rules.firstMatch(hand).isPresent();
    }

    @Override
    public void start() {
        getRid(game.getMulligans());
    }

    @Override
    public void firstMainPhase() {
        // whichever the situation, if I have a probe in hand: play it
        while (game.getHand().contains(GITAXIAN_PROBE)) {
            game.castNonPermanent(GITAXIAN_PROBE, Mana.zero()).draw(1);
        }

        // land
        // pendelhaven if no invigorate in hand and no forest on board
        if (game.getHand().contains(PENDELHAVEN) && (!game.getHand().contains(INVIGORATE) || game.getBoard().contains(FOREST))) {
            game.land(PENDELHAVEN);
        } else if (game.getHand().contains(FOREST)) {
            game.land(FOREST);
        }

        // play all petals
        while (game.getHand().contains(LOTUS_PETAL)) {
            game.castPermanent(LOTUS_PETAL, Mana.zero());
        }
    }

    @Override
    public void combatPhase() {
        // boost all creatures and attack
        Cards creatures = game.getBoard().findAll(CREATURES);
        if (creatures.isEmpty()) {
            return;
        }

        // first start by playing all free spells

        // play all mutagenic
        while (game.getHand().contains(MUTAGENIC_GROWTH)) {
            game.castNonPermanent(MUTAGENIC_GROWTH, Mana.zero()).poisonOpponent(2);
        }

        // play all invigorates (if forest)
        if (game.getBoard().contains(FOREST)) {
            while (game.getHand().contains(INVIGORATE)) {
                game.castNonPermanent(INVIGORATE, Mana.zero()).poisonOpponent(4).damageOpponent(-3);
            }
        }

        // play all possible scale up
        int castableScaleUp = Math.min(creatures.size(), game.getHand().count(SCALE_UP));
        while (castableScaleUp > 0 && canPay(G)) {
            preparePool(G);
            game.castNonPermanent(SCALE_UP, G).poisonOpponent(5);
            castableScaleUp--;
        }

        // is there an optimal order to play my spells to kill this turn ?
        int poisonCounters = game.getOpponentPoisonCounters();
        poisonCounters += 3 * game.getBoard().count(SEAL_OF_STRENGTH);
        poisonCounters += 1 * game.getBoard().count(CREATURES);
        poisonCounters += 2 * game.getBoard().count(RANCOR);

        Mana potentialPool = game.getPool()
                .plus(Mana.of(0, 0, game.countUntapped(MANA_PRODUCERS), 0, 0, 0));

        Collection<String> boostsToPlay = game.isLanded() ?
                game.getHand().findAll(RANCOR, GROUNDSWELL, MIGHT_OF_OLD_KROSA, GIANT_GROWTH, SEAL_OF_STRENGTH, BLOSSOMING_DEFENSE, LARGER_THAN_LIFE, VINES_OF_VASTWOOD, RANGER_S_GUILE)
                : game.getHand().findAll(RANCOR, MIGHT_OF_OLD_KROSA, GIANT_GROWTH, SEAL_OF_STRENGTH, BLOSSOMING_DEFENSE, LARGER_THAN_LIFE, VINES_OF_VASTWOOD, GROUNDSWELL, RANGER_S_GUILE);

        if (simulate(potentialPool, boostsToPlay).getCounters() + poisonCounters < 10) {
            // I can't kill with default order (rancors first): is there another order to play my boosts that can kill this turn ?
            Stream<Stream<String>> allBoostsOrderCombinations = Permutations.of(new ArrayList<>(boostsToPlay));
            Optional<Sim> bestOrder = allBoostsOrderCombinations
                    .map(boosts -> simulate(potentialPool, boosts.collect(Collectors.toList())))
                    .sorted(Comparator.reverseOrder())
                    .findFirst();

            if (bestOrder.isPresent() && bestOrder.get().getCounters() + poisonCounters >= 10) {
//                System.out.println("I can rush with " + bestOrder.get().boosts + " instead of " + boostsToPlay);
                boostsToPlay = bestOrder.get().boosts;
            }
        }

        // now play boosts in optimal order (if I can kill) or fallback order (rancors first) if not
        for (String boost : boostsToPlay) {
            switch (boost) {
                case RANCOR:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castPermanent(RANCOR, G);
                    }
                    break;
                case MIGHT_OF_OLD_KROSA:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castNonPermanent(MIGHT_OF_OLD_KROSA, G).poisonOpponent(4);
                    }
                    break;
                case GROUNDSWELL:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castNonPermanent(GROUNDSWELL, G).poisonOpponent(game.isLanded() ? 4 : 2);
                    }
                    break;
                case GIANT_GROWTH:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castNonPermanent(GIANT_GROWTH, G).poisonOpponent(3);
                    }
                    break;
                case SEAL_OF_STRENGTH:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castPermanent(SEAL_OF_STRENGTH, G);
                    }
                    break;
                case BLOSSOMING_DEFENSE:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castNonPermanent(BLOSSOMING_DEFENSE, G).poisonOpponent(2);
                    }
                    break;
                case LARGER_THAN_LIFE:
                    if (canPay(G1)) {
                        preparePool(G1);
                        game.castNonPermanent(LARGER_THAN_LIFE, G1).poisonOpponent(4);
                    }
                    break;
                case VINES_OF_VASTWOOD:
                    if (canPay(GG)) {
                        preparePool(GG);
                        game.castNonPermanent(VINES_OF_VASTWOOD, GG).poisonOpponent(4);
                    }
                    break;
                case RANGER_S_GUILE:
                    if (canPay(G)) {
                        preparePool(G);
                        game.castNonPermanent(RANGER_S_GUILE, G).poisonOpponent(1);
                    }
                    break;
            }
        }

        // sacrifice all seals
        game.getBoard().findAll(SEAL_OF_STRENGTH).forEach(card -> game.sacrifice(card).poisonOpponent(3));

        // attach with all creatures
        creatures.forEach(card -> game.tapForAttack(card, 1).poisonOpponent(1));

        // add rancors
        game.getBoard().findAll(RANCOR).forEach(card -> game.tap(card).poisonOpponent(2));

        // use one untapped pendelhaven to boost
        game.getUntapped(PENDELHAVEN).forEach(card -> game.tap(card).poisonOpponent(1));
    }

    private Sim simulate(Mana potentialPool, Collection<String> boosts) {
        int counters = 0;
        for (String boost : boosts) {
            switch (boost) {
                case RANCOR:
                    if (potentialPool.contains(G)) {
                        counters += 2;
                        potentialPool = potentialPool.minus(G);
                    }
                    break;
                case MIGHT_OF_OLD_KROSA:
                    if (potentialPool.contains(G)) {
                        counters += 4;
                        potentialPool = potentialPool.minus(G);
                    }
                    break;
                case GROUNDSWELL:
                    if (potentialPool.contains(G)) {
                        counters += game.isLanded() ? 4 : 2;
                        potentialPool = potentialPool.minus(G);
                    }
                    break;
                case GIANT_GROWTH:
                case SEAL_OF_STRENGTH:
                    if (potentialPool.contains(G)) {
                        counters += 3;
                        potentialPool = potentialPool.minus(G);
                    }
                    break;
                case BLOSSOMING_DEFENSE:
                    if (potentialPool.contains(G)) {
                        counters += 2;
                        potentialPool = potentialPool.minus(G);
                    }
                    break;
                case LARGER_THAN_LIFE:
                    if (potentialPool.contains(G1)) {
                        counters += 4;
                        potentialPool = potentialPool.minus(G1);
                    }
                    break;
                case VINES_OF_VASTWOOD:
                    if (potentialPool.contains(GG)) {
                        counters += 4;
                        potentialPool = potentialPool.minus(GG);
                    }
                    break;
                case RANGER_S_GUILE:
                    if (potentialPool.contains(G)) {
                        counters += 1;
                        potentialPool = potentialPool.minus(G);
                    }
                    break;
            }
        }
        return Sim.builder().counters(counters).boosts(boosts).build();
    }

    @Builder
    @Value
    private static class Sim implements Comparable<Sim> {
        final int counters;
        final Collection<String> boosts;

        @Override
        public int compareTo(Sim other) {
            return counters - other.counters;
        }
    }

    @Override
    public void secondMainPhase() {
        // cast 1 creature if none on board
        if (game.getBoard().count(CREATURES) == 0) {
            if (game.getHand().contains(GLISTENER_ELF) && canPay(G)) {
                preparePool(G);
                game.castPermanent(GLISTENER_ELF, G);
            } else if (game.getHand().contains(BLIGHT_MAMBA) && canPay(G1)) {
                preparePool(G1);
                game.castPermanent(BLIGHT_MAMBA, G1);
            } else if (game.getHand().contains(ICHORCLAW_MYR) && canPay(TWO)) {
                preparePool(TWO);
                game.castPermanent(ICHORCLAW_MYR, TWO);
            }
        }

        // play all seals
        while (game.getHand().contains(SEAL_OF_STRENGTH) && canPay(G)) {
            preparePool(G);
            game.castPermanent(SEAL_OF_STRENGTH, G);
        }

        // play rancors
        if (game.getBoard().count(CREATURES) > 0) {
            while (game.getHand().contains(RANCOR) && canPay(G)) {
                preparePool(G);
                game.castPermanent(RANCOR, G);
            }
        }

        // cast extra creatures
        for (String crea : game.getHand().findAll(CREATURES)) {
            Mana cost = crea.equals(GLISTENER_ELF) ? G : crea.equals(BLIGHT_MAMBA) ? G1 : TWO;
            if (canPay(cost)) {
                preparePool(cost);
                game.castPermanent(crea, cost);
            }
        }
    }

    @Override
    public void endingPhase() {
        if (game.getHand().size() > 7) {
            discard(game.getHand().size() - 7);
        }
    }

    void getRid(int number) {
        for (int i = 0; i < number; i++) {
            if (game.putOnBottomOfLibraryOneOf(MENTAL_MISSTEP, APOSTLE_S_BLESSING).isPresent()) {
                continue;
            }
            // discard extra lands
            if (game.getHand().count(MANA_PRODUCERS) > 2 && game.putOnBottomOfLibraryOneOf(MANA_PRODUCERS).isPresent()) {
                continue;
            }
            // discard extra creatures
            if (game.getHand().count(CREATURES) > 2 && game.putOnBottomOfLibraryOneOf(CREATURES).isPresent()) {
                continue;
            }
            // TODO: choose better
            game.putOnBottomOfLibrary(game.getHand().getFirst());
        }
    }

    void discard(int number) {
        for (int i = 0; i < number; i++) {
            if (game.discardOneOf(MENTAL_MISSTEP, APOSTLE_S_BLESSING).isPresent()) {
                continue;
            }
            // discard extra lands
            if (game.getBoard().count(MANA_PRODUCERS) + game.getHand().count(MANA_PRODUCERS) > 3 && game.discardOneOf(MANA_PRODUCERS).isPresent()) {
                continue;
            }
            // discard extra creatures
            if (game.getBoard().count(CREATURES) + game.getHand().count(CREATURES) > 2 && game.discardOneOf(CREATURES).isPresent()) {
                continue;
            }
            // TODO: choose better
            game.discard(game.getHand().getFirst());
        }
    }

    boolean canPay(Mana cost) {
        // potential mana pool is current pool + untapped lands + petals on board
        Mana potentialPool = game.getPool()
                .plus(Mana.of(0, 0, game.countUntapped(MANA_PRODUCERS), 0, 0, 0));
        return potentialPool.contains(cost);
    }

    void preparePool(Mana cost) {
        while (!game.canPay(cost)) {
            Optional<String> producer = game.findFirstUntapped(FOREST, PENDELHAVEN, LOTUS_PETAL);
            if (producer.isPresent()) {
                if (producer.get().equals(LOTUS_PETAL)) {
                    game.sacrifice(LOTUS_PETAL).add(G);
                } else {
                    // a land
                    game.tapLandForMana(producer.get(), G);
                }
            } else {
                // can't preparePool !!!
                return;
            }
        }
    }
}
