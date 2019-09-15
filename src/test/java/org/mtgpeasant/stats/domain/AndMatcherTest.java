package org.mtgpeasant.stats.domain;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.List;

public class AndMatcherTest {
    @Test
    public void matcher_should_match() {
        // Given
        Cards cards = Cards.builder()
                .card("swamp")
                .card("mountain")
                .card("ulamog crusher")
                .card("animate dead")
                .card("dark ritual")
                .card("lotus petal")
                .card("putrid imp")
                .build();

        Matcher matcher = AndMatcher.builder()
                .matcher(CardMatcher.builder().card("swamp").build())
                .matcher(CardMatcher.builder().card("animate dead").build())
                .matcher(CardMatcher.builder().card("ulamog crusher").build())
                .matcher(CardMatcher.builder().card("putrid imp").build())
                .matcher(CardMatcher.builder().card("dark ritual").build())
                .build();

        // When
        List<Match> matches = matcher.matches(Match.from(cards), null);

        // Then
        Assertions.assertThat(matches).hasSize(1);
        Assertions.assertThat(matches.get(0).getSelected().size()).isEqualTo(5);
        Assertions.assertThat(matches.get(0).getRemaining().size()).isEqualTo(2);
    }

    @Test
    public void matcher_should_not_match() {
        // Given
        Cards cards = Cards.builder()
                .card("swamp")
                .card("mountain")
                .card("ulamog crusher")
                .card("animate dead")
                .card("dark ritual")
                .card("lotus petal")
                .card("putrid imp")
                .build();

        Matcher matcher = AndMatcher.builder()
                .matcher(CardMatcher.builder().card("swamp").build())
                .matcher(CardMatcher.builder().card("swamp").build())
                .matcher(CardMatcher.builder().card("animate dead").build())
                .matcher(CardMatcher.builder().card("ulamog crusher").build())
                .matcher(CardMatcher.builder().card("putrid imp").build())
                .build();

        // When
        List<Match> matches = matcher.matches(Match.from(cards), null);

        // Then
        Assertions.assertThat(matches).isEmpty();
    }
}