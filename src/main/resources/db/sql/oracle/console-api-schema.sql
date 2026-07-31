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

-- Politica di logging verso il Giornale degli Eventi, una riga per ciascuna
-- delle 8 interfacce API di GovPay. Sotto-risorsa `giornaleEventi` del blob
-- V1 `configurazione` (il connettore GDE vero e proprio e' sulla tabella
-- condivisa `connettori`, cod_connettore 'govpay_gde_api').
CREATE TABLE giornale_eventi_interfacce
(
	nome_interfaccia VARCHAR2(20 CHAR) NOT NULL,
	log_letture VARCHAR2(20 CHAR) NOT NULL,
	dump_letture VARCHAR2(20 CHAR) NOT NULL,
	log_scritture VARCHAR2(20 CHAR) NOT NULL,
	dump_scritture VARCHAR2(20 CHAR) NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_giornale_eventi_interfacce PRIMARY KEY (nome_interfaccia)
);

-- Server SMTP usato per l'invio dei promemoria (riga singola). Le password
-- (SMTP, keystore, truststore) sono scritte solo dall'endpoint dedicato
-- `.../password`, mai esposte in lettura.
CREATE TABLE impostazioni_mail_server
(
	id NUMBER(5) NOT NULL,
	abilitato NUMBER(1) NOT NULL,
	host VARCHAR2(255 CHAR),
	port NUMBER(10),
	username VARCHAR2(35 CHAR),
	from_indirizzo VARCHAR2(255 CHAR),
	read_timeout_ms NUMBER(10),
	connection_timeout_ms NUMBER(10),
	start_tls NUMBER(1) NOT NULL,
	ssl_abilitato NUMBER(1) NOT NULL,
	ssl_tipo VARCHAR2(255 CHAR),
	ssl_hostname_verifier NUMBER(1) NOT NULL,
	ks_location VARCHAR2(255 CHAR),
	ks_tipo VARCHAR2(255 CHAR),
	ks_management_algorithm VARCHAR2(255 CHAR),
	ts_location VARCHAR2(255 CHAR),
	ts_tipo VARCHAR2(255 CHAR),
	ts_management_algorithm VARCHAR2(255 CHAR),
	password VARCHAR2(255 CHAR),
	ks_password VARCHAR2(255 CHAR),
	ts_password VARCHAR2(255 CHAR),
	CONSTRAINT pk_impostazioni_mail_server PRIMARY KEY (id)
);

-- Template FreeMarker dei promemoria spediti via mail, una riga per tipo
-- (AVVISO/RICEVUTA/SCADENZA).
CREATE TABLE impostazioni_mail_promemoria
(
	tipo_promemoria VARCHAR2(20 CHAR) NOT NULL,
	oggetto VARCHAR2(4000 CHAR),
	messaggio VARCHAR2(4000 CHAR),
	allega_pdf NUMBER(1),
	solo_eseguiti NUMBER(1),
	preavviso NUMBER(10),
	CONSTRAINT pk_impostazioni_mail_promemoria PRIMARY KEY (tipo_promemoria)
);

-- Template FreeMarker dei promemoria spediti via notifica push App IO, una
-- riga per tipo (AVVISO/RICEVUTA/SCADENZA). Nessun allegato (a differenza
-- della variante mail): niente colonna allega_pdf.
CREATE TABLE impostazioni_appio_promemoria
(
	tipo_promemoria VARCHAR2(20 CHAR) NOT NULL,
	oggetto VARCHAR2(4000 CHAR),
	messaggio VARCHAR2(4000 CHAR),
	solo_eseguiti NUMBER(1),
	preavviso NUMBER(10),
	CONSTRAINT pk_impostazioni_appio_promemoria PRIMARY KEY (tipo_promemoria)
);
