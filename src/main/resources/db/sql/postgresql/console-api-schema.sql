-- Schema target di govpay-console-api per PostgreSQL.
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
	cod_intermediario VARCHAR(35),
	check_stato VARCHAR(35),
	check_motivo VARCHAR(1024),
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
	cod_intermediario VARCHAR(35),
	ci_name VARCHAR(255),
	status VARCHAR(255),
	validity_date TIMESTAMP,
	description VARCHAR(512),
	label VARCHAR(1024),
	check_stato VARCHAR(35),
	check_motivo VARCHAR(1024),
	-- fk/pk columns
	id BIGINT DEFAULT nextval('seq_pagopa_iban_cache') NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
);

-- Tabella di appoggio per il recupero puntuale di una RT su richiesta
-- dell'operatore (POST /ricevute/recuperi): console-api scrive la tripla,
-- govpay-rt-batch la elabora e la elimina (o la marca su esito negativo).
CREATE SEQUENCE seq_rt_recuperi start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE rt_recuperi
(
	cod_dominio VARCHAR(35) NOT NULL,
	iuv VARCHAR(35) NOT NULL,
	iur VARCHAR(35) NOT NULL,
	data_richiesta TIMESTAMP NOT NULL,
	id_operatore BIGINT,
	esito VARCHAR(35),
	data_ultimo_tentativo TIMESTAMP,
	-- fk/pk columns
	id BIGINT DEFAULT nextval('seq_rt_recuperi') NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_rt_recuperi PRIMARY KEY (id)
);
