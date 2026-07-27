-- Schema target di govpay-console-api per SQL Server.
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
	cod_fiscale VARCHAR(16) NOT NULL,
	denominazione VARCHAR(255) NOT NULL,
	station_id VARCHAR(35),
	aux_digit VARCHAR(2) NOT NULL,
	segregation_code VARCHAR(4),
	cbill_code VARCHAR(35),
	data_ultimo_aggiornamento DATETIME2 NOT NULL,
	-- fk/pk columns
	id BIGINT IDENTITY,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_ec_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_ec_cache_cf UNIQUE (cod_fiscale)
);

-- Cache locale degli IBAN abilitati su pagoPA per dominio, per il censimento
-- assistito dei conti di accredito (scritta dal batch govpay-iban-batch, letta
-- in sola lettura da console-api).
CREATE TABLE pagopa_iban_cache
(
	cod_dominio VARCHAR(35) NOT NULL,
	iban VARCHAR(35) NOT NULL,
	attivo BIT NOT NULL,
	data_ultima_verifica DATETIME2 NOT NULL,
	-- fk/pk columns
	id BIGINT IDENTITY,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
);
