-- Schema target di govpay-console-api per Oracle.
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
CREATE SEQUENCE seq_pagopa_ec_cache MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 INCREMENT BY 1 CACHE 2 NOCYCLE;

CREATE TABLE pagopa_ec_cache
(
	cod_fiscale VARCHAR2(16 CHAR) NOT NULL,
	denominazione VARCHAR2(255 CHAR) NOT NULL,
	station_id VARCHAR2(35 CHAR),
	aux_digit VARCHAR2(2 CHAR) NOT NULL,
	segregation_code VARCHAR2(4 CHAR),
	cbill_code VARCHAR2(35 CHAR),
	data_ultimo_aggiornamento TIMESTAMP NOT NULL,
	-- fk/pk columns
	id NUMBER NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_ec_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_ec_cache_cf UNIQUE (cod_fiscale)
);

CREATE TRIGGER trg_pagopa_ec_cache
BEFORE
insert on pagopa_ec_cache
for each row
begin
   IF (:new.id IS NULL) THEN
      SELECT seq_pagopa_ec_cache.nextval INTO :new.id FROM dual;
   END IF;
end;
/

-- Cache locale degli IBAN abilitati su pagoPA per dominio, per il censimento
-- assistito dei conti di accredito (scritta dal batch govpay-iban-batch, letta
-- in sola lettura da console-api).
CREATE SEQUENCE seq_pagopa_iban_cache MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 INCREMENT BY 1 CACHE 2 NOCYCLE;

CREATE TABLE pagopa_iban_cache
(
	cod_dominio VARCHAR2(35 CHAR) NOT NULL,
	iban VARCHAR2(35 CHAR) NOT NULL,
	attivo NUMBER NOT NULL,
	data_ultima_verifica TIMESTAMP NOT NULL,
	-- fk/pk columns
	id NUMBER NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
);

CREATE TRIGGER trg_pagopa_iban_cache
BEFORE
insert on pagopa_iban_cache
for each row
begin
   IF (:new.id IS NULL) THEN
      SELECT seq_pagopa_iban_cache.nextval INTO :new.id FROM dual;
   END IF;
end;
/
