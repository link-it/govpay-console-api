package it.govpay.console.utenza;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import it.govpay.console.web.BadRequestException;

class PasswordPolicyTest {

    @Test
    void passwordValidaPassa() {
        assertThatCode(() -> PasswordPolicy.validate("Password01")).doesNotThrowAnyException();
    }

    @Test
    void passwordDiOttoCaratteriEsattiPassa() {
        assertThatCode(() -> PasswordPolicy.validate("Abcdef01")).doesNotThrowAnyException();
    }

    @Test
    void nullOVuotaSonoObbligatorie() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("obbligatorio");
        assertThatThrownBy(() -> PasswordPolicy.validate(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("obbligatorio");
    }

    @Test
    void troppoCortaSegnalaLunghezzaMinima() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("almeno 8 caratteri");
    }

    @Test
    void troppoLungaSegnalaLunghezzaMassima() {
        String lunga = "Aa1" + "x".repeat(253);
        assertThatThrownBy(() -> PasswordPolicy.validate(lunga))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("al massimo 255 caratteri");
    }

    @Test
    void senzaMinuscolaSegnalaMinuscola() {
        assertThatThrownBy(() -> PasswordPolicy.validate("PASSWORD01"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("almeno una lettera minuscola");
    }

    @Test
    void senzaMaiuscolaSegnalaMaiuscola() {
        assertThatThrownBy(() -> PasswordPolicy.validate("password01"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("almeno una lettera maiuscola");
    }

    @Test
    void senzaCifraSegnalaCifra() {
        assertThatThrownBy(() -> PasswordPolicy.validate("PasswordXx"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("almeno una cifra");
    }

    @Test
    void conSpaziSegnalaSpaziatura() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Password 01"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nessun carattere di spaziatura");
    }

    @Test
    void violazioniMultipleSonoTutteElencate() {
        assertThatThrownBy(() -> PasswordPolicy.validate("ab c"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("almeno 8 caratteri")
                .hasMessageContaining("almeno una lettera maiuscola")
                .hasMessageContaining("almeno una cifra")
                .hasMessageContaining("nessun carattere di spaziatura");
    }
}
