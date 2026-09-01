package it.govpay.console.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    void percentoEscaped() {
        assertThat(LikePatterns.escape("100%")).isEqualTo("100\\%");
    }

    @Test
    void underscoreEscaped() {
        assertThat(LikePatterns.escape("mario_rossi")).isEqualTo("mario\\_rossi");
    }

    @Test
    void backslashEscapedPerPrimo() {
        // il backslash va escaped prima di %/_ altrimenti l'escape di %/_ produrrebbe
        // un secondo backslash che verrebbe a sua volta (erroneamente) raddoppiato.
        assertThat(LikePatterns.escape("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void valoreSenzaWildcardInvariato() {
        assertThat(LikePatterns.escape("Mario Rossi")).isEqualTo("Mario Rossi");
    }

    @Test
    void soloWildcardCompletamenteEscaped() {
        assertThat(LikePatterns.escape("%%%")).isEqualTo("\\%\\%\\%");
        assertThat(LikePatterns.escape("___")).isEqualTo("\\_\\_\\_");
    }

    @Test
    void combinazioneRealistica() {
        // termine con tutti e tre i caratteri speciali insieme, ordine misto.
        assertThat(LikePatterns.escape("50%_off\\now")).isEqualTo("50\\%\\_off\\\\now");
    }
}
