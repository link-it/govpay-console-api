-- Schema target di govpay-console-api per PostgreSQL.
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
CREATE SEQUENCE seq_pagopa_ec_cache start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE pagopa_ec_cache
(
	cod_fiscale VARCHAR(16) NOT NULL,
	denominazione VARCHAR(255) NOT NULL,
	station_id VARCHAR(35),
	aux_digit VARCHAR(2) NOT NULL,
	segregation_code VARCHAR(4),
	cbill_code VARCHAR(35),
	data_ultimo_aggiornamento TIMESTAMP NOT NULL,
	-- fk/pk columns
	id BIGINT DEFAULT nextval('seq_pagopa_ec_cache') NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_ec_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_ec_cache_cf UNIQUE (cod_fiscale)
);

-- Cache locale degli IBAN abilitati su pagoPA per dominio, per il censimento
-- assistito dei conti di accredito (scritta dal batch govpay-iban-batch, letta
-- in sola lettura da console-api).
CREATE SEQUENCE seq_pagopa_iban_cache start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE pagopa_iban_cache
(
	cod_dominio VARCHAR(35) NOT NULL,
	iban VARCHAR(35) NOT NULL,
	attivo BOOLEAN NOT NULL,
	data_ultima_verifica TIMESTAMP NOT NULL,
	-- fk/pk columns
	id BIGINT DEFAULT nextval('seq_pagopa_iban_cache') NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
);

-- Politica di logging verso il Giornale degli Eventi, una riga per ciascuna
-- delle 8 interfacce API di GovPay. Sotto-risorsa `giornaleEventi` del blob
-- V1 `configurazione` (il connettore GDE vero e proprio e' sulla tabella
-- condivisa `connettori`, cod_connettore 'govpay_gde_api').
CREATE TABLE giornale_eventi_interfacce
(
	nome_interfaccia VARCHAR(20) NOT NULL,
	log_letture VARCHAR(20) NOT NULL,
	dump_letture VARCHAR(20) NOT NULL,
	log_scritture VARCHAR(20) NOT NULL,
	dump_scritture VARCHAR(20) NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_giornale_eventi_interfacce PRIMARY KEY (nome_interfaccia)
);
