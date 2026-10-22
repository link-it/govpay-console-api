package it.govpay.console.tracciato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.TracciatoPendenze;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code tracciati.cod_dominio} non ha FK verso {@code domini}: il core puo'
 * registrare un tracciato su un dominio inesistente, e Hibernate lascia allora
 * {@code dominio} a null. Il mapper sollevava NPE e, girando dentro lo stream
 * sui risultati, una sola riga cosi' faceva rispondere 500 all'intero elenco.
 *
 * <p>Il caso non e' riproducibile nei test di integrazione: lo schema generato
 * da Hibernate ha la FK, e sospenderla in H2 fa commit implicito della
 * transazione di test.
 */
class TracciatoMapperTest {

    private final TracciatoMapper mapper = new TracciatoMapper(JsonMapper.shared());

    private static Tracciato tracciato(Dominio dominio, String codDominio) {
        Tracciato t = new Tracciato();
        t.setId(46L);
        t.setDominio(dominio);
        ReflectionTestUtils.setField(t, "codDominio", codDominio);
        t.setFormato("JSON");
        t.setStato("ELABORAZIONE");
        t.setDataCaricamento(OffsetDateTime.now());
        return t;
    }

    @Test
    void dominioPresenteEspostoConRagioneSociale() {
        Dominio dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setRagioneSociale("Comune di Test");

        TracciatoPendenze dto = mapper.toDto(tracciato(dominio, "12345678901"));

        assertThat(dto.getDominio().getIdDominio()).isEqualTo("12345678901");
        assertThat(dto.getDominio().getRagioneSociale()).isEqualTo("Comune di Test");
    }

    @Test
    void dominioAssenteDallAnagraficaEspostoConIlSoloCodice() {
        TracciatoPendenze dto = mapper.toDto(tracciato(null, "00000000000"));

        assertThat(dto.getDominio().getIdDominio()).isEqualTo("00000000000");
        assertThat(dto.getDominio().getRagioneSociale()).isNull();
    }
}
