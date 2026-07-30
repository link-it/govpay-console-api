-- Schema target di govpay-console-api per MySQL.
--
-- Tabelle proprie di console-api. Le modifiche alle tabelle ereditate da
-- govpay-core sono nello script `upgrade-v1-to-v2.sql`.
--
-- Per applicare il bring-up completo di un DB da zero per console-api:
--   1) applicare lo schema di govpay-core (`gov_pay.sql` di govpay-381)
--   2) applicare `upgrade-v1-to-v2.sql` di questo progetto
--   3) applicare questo file

-- Cache locale dell'anagrafica Enti Creditori sincronizzata da pagoPA
-- (scritta dal batch govpay-iban-batch, letta in sola lettura da console-api).
CREATE TABLE pagopa_ec_cache
(
	cod_fiscale VARCHAR(16) NOT NULL COMMENT 'Codice fiscale dell''ente creditore',
	denominazione VARCHAR(255) NOT NULL COMMENT 'Denominazione (companyName pagoPA)',
	station_id VARCHAR(35) COMMENT 'Identificativo stazione associata',
	aux_digit VARCHAR(2) NOT NULL COMMENT 'Cifra ausiliaria numero avviso',
	segregation_code VARCHAR(4) COMMENT 'Codice segregazione',
	cbill_code VARCHAR(35) COMMENT 'Codice CBILL',
	data_ultimo_aggiornamento DATETIME(3) NOT NULL COMMENT 'Timestamp ultima sincronizzazione dal batch',
	-- fk/pk columns
	id BIGINT AUTO_INCREMENT COMMENT 'Identificativo fisico',
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_ec_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_ec_cache_cf UNIQUE (cod_fiscale)
)ENGINE INNODB CHARACTER SET latin1 COLLATE latin1_general_cs;

-- Cache locale degli IBAN abilitati su pagoPA per dominio, per il censimento
-- assistito dei conti di accredito (scritta dal batch govpay-iban-batch, letta
-- in sola lettura da console-api).
CREATE TABLE pagopa_iban_cache
(
	cod_dominio VARCHAR(35) NOT NULL COMMENT 'Codice del dominio (EC)',
	iban VARCHAR(35) NOT NULL COMMENT 'IBAN abilitato su pagoPA',
	attivo BOOLEAN NOT NULL COMMENT 'Stato ENABLED su pagoPA',
	data_ultima_verifica DATETIME(3) NOT NULL COMMENT 'Timestamp ultima sincronizzazione dal batch',
	-- fk/pk columns
	id BIGINT AUTO_INCREMENT COMMENT 'Identificativo fisico',
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
)ENGINE INNODB CHARACTER SET latin1 COLLATE latin1_general_cs;

-- Politica di logging verso il Giornale degli Eventi, una riga per ciascuna
-- delle 8 interfacce API di GovPay. Sotto-risorsa `giornaleEventi` del blob
-- V1 `configurazione` (il connettore GDE vero e proprio e' sulla tabella
-- condivisa `connettori`, cod_connettore 'govpay_gde_api').
CREATE TABLE giornale_eventi_interfacce
(
	nome_interfaccia VARCHAR(20) NOT NULL COMMENT 'Nome dell''interfaccia API (es. apiEnte)',
	log_letture VARCHAR(20) NOT NULL COMMENT 'Politica di log per le letture',
	dump_letture VARCHAR(20) NOT NULL COMMENT 'Politica di dump per le letture',
	log_scritture VARCHAR(20) NOT NULL COMMENT 'Politica di log per le scritture',
	dump_scritture VARCHAR(20) NOT NULL COMMENT 'Politica di dump per le scritture',
	-- fk/pk keys constraints
	CONSTRAINT pk_giornale_eventi_interfacce PRIMARY KEY (nome_interfaccia)
)ENGINE INNODB CHARACTER SET latin1 COLLATE latin1_general_cs;
