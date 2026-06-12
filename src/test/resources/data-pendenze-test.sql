-- Fixture E2E per la suite di test integration dello scope I (issue #9).
-- Caricata via @Sql sui test che ne hanno bisogno. Lavora su H2 in modalita'
-- PostgreSQL con schema generato da ddl-auto=create-drop sulle entity slim,
-- quindi qui usiamo SOLO le colonne effettivamente mappate dalle entity V2.
--
-- Dati: 1 dominio, 1 applicazione, 1 tipo versamento, 2 pendenze (entrambe
-- pagate), 1 SV ciascuna, 2 RT entrambe con xml_rt valorizzato.
-- L'autenticazione (utenza + operatore con visibilita' totale) viene allestita
-- dal @BeforeEach del test via JPA con GovpayPasswordEncoder.

-- Cleanup: H2 con DB_CLOSE_DELAY=-1 persiste lo stato tra test del medesimo
-- SpringBootTest. Per idempotenza, cancelliamo i dati con gli ID della fixture
-- prima di reinserirli. Ordine inverso alle FK.
DELETE FROM gp_audit;
DELETE FROM rpt WHERE id IN (9001, 9002);
DELETE FROM singoli_versamenti WHERE id IN (8001, 8002);
DELETE FROM versamenti WHERE id IN (7001, 7002);
DELETE FROM tipi_vers_domini WHERE id = 6001;
DELETE FROM tipi_versamento WHERE id = 5001;
DELETE FROM applicazioni WHERE id = 4001;
DELETE FROM domini WHERE id = 3001;
DELETE FROM operatori WHERE nome = 'Op Fixture';
DELETE FROM utenze WHERE principal = 'op-fixture';

INSERT INTO domini (id, cod_dominio, ragione_sociale, aux_digit)
VALUES (3001, '12345678901', 'Comune Fixture', 0);

INSERT INTO applicazioni (id, cod_applicazione)
VALUES (4001, 'APP-FIXTURE');

INSERT INTO tipi_versamento (id, cod_tipo_versamento, descrizione)
VALUES (5001, 'TARI', 'TARI Fixture');

INSERT INTO tipi_vers_domini (id, id_dominio, id_tipo_versamento)
VALUES (6001, 3001, 5001);

INSERT INTO versamenti (id, cod_versamento_ente, importo_totale, importo_pagato,
                        stato_versamento, data_creazione, data_ora_ultimo_aggiornamento,
                        debitore_identificativo, debitore_anagrafica, src_debitore_identificativo,
                        anomalo, ack, tipo, causale_versamento,
                        iuv_versamento, numero_avviso,
                        id_dominio, id_applicazione, id_tipo_versamento, id_tipo_versamento_dominio)
VALUES (7001, 'PEND-FIX-1', 100.0, 100.0,
        'ESEGUITO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'RSSMRA80A01H501U', 'Mario Rossi Fixture', 'RSSMRA80A01H501U',
        FALSE, TRUE, 'DOVUTO', 'TARI fixture causale',
        '1111111111111', '011111111111111111',
        3001, 4001, 5001, 6001);

INSERT INTO versamenti (id, cod_versamento_ente, importo_totale, importo_pagato,
                        stato_versamento, data_creazione, data_ora_ultimo_aggiornamento,
                        debitore_identificativo, debitore_anagrafica, src_debitore_identificativo,
                        anomalo, ack, tipo, causale_versamento,
                        iuv_versamento, numero_avviso,
                        id_dominio, id_applicazione, id_tipo_versamento, id_tipo_versamento_dominio)
VALUES (7002, 'PEND-FIX-2', 50.0, 50.0,
        'ESEGUITO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'VRDLGI90B02H501T', 'Luigi Verdi Fixture', 'VRDLGI90B02H501T',
        FALSE, TRUE, 'DOVUTO', 'TARI fixture causale 2',
        '2222222222222', '022222222222222222',
        3001, 4001, 5001, 6001);

INSERT INTO singoli_versamenti (id, cod_singolo_versamento_ente, stato_singolo_versamento,
                                importo_singolo_versamento, descrizione, indice_dati,
                                id_versamento)
VALUES (8001, 'SV-FIX-1-A', 'ESEGUITO', 100.0, 'Voce fixture 1', 1, 7001);

INSERT INTO singoli_versamenti (id, cod_singolo_versamento_ente, stato_singolo_versamento,
                                importo_singolo_versamento, descrizione, indice_dati,
                                id_versamento)
VALUES (8002, 'SV-FIX-2-A', 'ESEGUITO', 50.0, 'Voce fixture 2', 1, 7002);

-- 2 RT entrambe con xml_rt valorizzato ed esito 0 (Eseguito).
INSERT INTO rpt (id, iuv, ccp, cod_dominio, xml_rt, cod_esito_pagamento,
                 importo_totale_pagato, data_msg_richiesta, data_msg_ricevuta,
                 versione, cod_psp, denominazione_attestante, cod_transazione_rt,
                 id_versamento)
VALUES (9001, '1111111111111', 'CCP-FIX-001', '12345678901',
        CAST('<RT xmlns="urn:fixture"><id>FIX-1</id></RT>' AS BYTEA), 0,
        100.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'SANP_240_V2', 'BNCFIXTURE', 'Banca Fixture S.p.A.', 'TX-FIX-1',
        7001);

INSERT INTO rpt (id, iuv, ccp, cod_dominio, xml_rt, cod_esito_pagamento,
                 importo_totale_pagato, data_msg_richiesta, data_msg_ricevuta,
                 versione, cod_psp, denominazione_attestante, cod_transazione_rt,
                 id_versamento)
VALUES (9002, '2222222222222', 'CCP-FIX-002', '12345678901',
        CAST('<RT xmlns="urn:fixture"><id>FIX-2</id></RT>' AS BYTEA), 0,
        50.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'SANP_240_V2', 'BNCFIXTURE', 'Banca Fixture S.p.A.', 'TX-FIX-2',
        7002);
