-- Schema target di govpay-console-api per MySQL.
--
-- Tabelle proprie di console-api. Le modifiche alle tabelle ereditate da
-- govpay-core sono nello script `upgrade-v1-to-v2.sql`.
--
-- Per applicare il bring-up completo di un DB da zero per console-api:
--   1) applicare lo schema di govpay-core V1
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
	cod_intermediario VARCHAR(35) COMMENT 'Codice intermediario dell''ultima sincronizzazione',
	check_stato VARCHAR(35) COMMENT 'Esito dell''ultima verifica contro i domini censiti',
	check_motivo VARCHAR(1024) COMMENT 'Descrizione testuale della discrepanza',
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
	cod_intermediario VARCHAR(35) COMMENT 'Codice intermediario dell''ultima sincronizzazione',
	ci_name VARCHAR(255) COMMENT 'Nome del titolare (ciName pagoPA)',
	status VARCHAR(255) COMMENT 'Stato grezzo pagoPA (es. ENABLED/DISABLED)',
	validity_date DATETIME(3) COMMENT 'Activation date pagoPA',
	description VARCHAR(512) COMMENT 'Descrizione pagoPA',
	label VARCHAR(1024) COMMENT 'Label pagoPA',
	check_stato VARCHAR(35) COMMENT 'Esito dell''ultima verifica contro IBAN_ACCREDITO',
	check_motivo VARCHAR(1024) COMMENT 'Descrizione testuale della discrepanza',
	-- fk/pk columns
	id BIGINT AUTO_INCREMENT COMMENT 'Identificativo fisico',
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
)ENGINE INNODB CHARACTER SET latin1 COLLATE latin1_general_cs;
