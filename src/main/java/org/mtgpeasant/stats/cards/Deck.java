package org.mtgpeasant.stats.cards;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Deck {
    final Cards main;
    final Cards sideboard;
}