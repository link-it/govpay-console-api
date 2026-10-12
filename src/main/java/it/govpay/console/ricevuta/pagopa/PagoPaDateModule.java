package it.govpay.console.ricevuta.pagopa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import it.govpay.console.ricevuta.pagopa.adapter.DataTypeAdapterCXF;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializza i tipi {@code java.time} dei modelli PagoPA usando lo stesso
 * formato XSD applicato in unmarshalling dagli adapter, così che il JSON delle
 * ricevute riporti date/orari nella forma canonica dei tracciati pagoPA.
 */
public class PagoPaDateModule extends SimpleModule {

	private static final long serialVersionUID = 1L;

	public PagoPaDateModule() {
		super();
		addSerializer(LocalTime.class, new LocalTimeSerializer());
		addSerializer(LocalDate.class, new LocalDateSerializer());
		addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
	}

	static final class LocalTimeSerializer extends StdScalarSerializer<LocalTime> {
		LocalTimeSerializer() {
			super(LocalTime.class);
		}

		@Override
		public void serialize(LocalTime value, JsonGenerator gen, SerializationContext ctxt) {
			gen.writeString(DataTypeAdapterCXF.printLocalTime(value));
		}
	}

	static final class LocalDateSerializer extends StdScalarSerializer<LocalDate> {
		LocalDateSerializer() {
			super(LocalDate.class);
		}

		@Override
		public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) {
			gen.writeString(DataTypeAdapterCXF.printLocalDate(value));
		}
	}

	static final class LocalDateTimeSerializer extends StdScalarSerializer<LocalDateTime> {
		LocalDateTimeSerializer() {
			super(LocalDateTime.class);
		}

		@Override
		public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
			gen.writeString(DataTypeAdapterCXF.printLocalDateTime(value));
		}
	}
}
