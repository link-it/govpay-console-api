package it.govpay.console.ricevuta.pagopa;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import it.govpay.console.ricevuta.pagopa.adapter.DataTypeAdapterCXF;

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
		private static final long serialVersionUID = 1L;

		LocalTimeSerializer() {
			super(LocalTime.class);
		}

		@Override
		public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeString(DataTypeAdapterCXF.printLocalTime(value));
		}
	}

	static final class LocalDateSerializer extends StdScalarSerializer<LocalDate> {
		private static final long serialVersionUID = 1L;

		LocalDateSerializer() {
			super(LocalDate.class);
		}

		@Override
		public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeString(DataTypeAdapterCXF.printLocalDate(value));
		}
	}

	static final class LocalDateTimeSerializer extends StdScalarSerializer<LocalDateTime> {
		private static final long serialVersionUID = 1L;

		LocalDateTimeSerializer() {
			super(LocalDateTime.class);
		}

		@Override
		public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeString(DataTypeAdapterCXF.printLocalDateTime(value));
		}
	}
}
