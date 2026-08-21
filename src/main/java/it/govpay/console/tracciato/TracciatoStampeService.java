package it.govpay.console.tracciato;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Tracciato;
import it.govpay.console.repository.TracciatoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET .../tracciati/{id}/stampe}: {@code zip_stampe} non e' mappato
 * sull'entity {@link Tracciato} (a differenza di {@code raw_richiesta}/
 * {@code raw_esito}) perche' e' tipizzato {@code OID} su Postgres — un vero
 * Large Object, non un semplice BYTEA — ma {@code BLOB} sugli altri
 * dialetti. Letto via JDBC diretto, con SQL diverso per Postgres
 * ({@code lo_get(oid)}, Postgres 9.4+: estrae il Large Object come
 * {@code bytea} in un'unica query, senza passare dalla Large Object API)
 * rispetto agli altri dialetti (colonna BLOB, lettura diretta).
 */
@Service
public class TracciatoStampeService {

    private final TracciatoRepository tracciatoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentOperatorService currentOperatorService;
    private final ObjectMapper objectMapper;

    private volatile Boolean postgres;

    public TracciatoStampeService(TracciatoRepository tracciatoRepository,
                                  JdbcTemplate jdbcTemplate,
                                  CurrentOperatorService currentOperatorService,
                                  ObjectMapper objectMapper) {
        this.tracciatoRepository = tracciatoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.currentOperatorService = currentOperatorService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> get(Long id) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Tracciato tracciato = tracciatoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tracciato non trovato: " + id));
        if (!DominioVisibilita.isVisibile(tracciato.getDominio().getId(), operatore)) {
            throw new NotFoundException("Tracciato non trovato: " + id);
        }

        byte[] zip = leggiZipStampe(id);
        if (zip == null || zip.length == 0) {
            String motivo = isStampaAvvisiTrue(tracciato)
                    ? "Stampe non ancora disponibili per questo tracciato."
                    : "Stampe non previste per questo tracciato (stampaAvvisi=false).";
            throw new NotFoundException(motivo);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("avvisi-" + id + ".zip").build().toString())
                .body(new ByteArrayResource(zip));
    }

    private boolean isStampaAvvisiTrue(Tracciato tracciato) {
        String json = tracciato.getBeanDati();
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            return objectMapper.readValue(json, TracciatoBeanDati.class).isStampaAvvisi();
        } catch (JacksonException e) {
            return false;
        }
    }

    private byte[] leggiZipStampe(Long id) {
        String sql = isPostgres()
                ? "SELECT lo_get(zip_stampe) FROM tracciati WHERE id = ?"
                : "SELECT zip_stampe FROM tracciati WHERE id = ?";
        List<byte[]> risultato = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBytes(1), id);
        return risultato.isEmpty() ? null : risultato.get(0);
    }

    private boolean isPostgres() {
        Boolean cached = postgres;
        if (cached != null) {
            return cached;
        }
        boolean value = Boolean.TRUE.equals(jdbcTemplate.execute((Connection con) -> {
            try {
                return "PostgreSQL".equalsIgnoreCase(con.getMetaData().getDatabaseProductName());
            } catch (SQLException e) {
                return false;
            }
        }));
        postgres = value;
        return value;
    }
}
