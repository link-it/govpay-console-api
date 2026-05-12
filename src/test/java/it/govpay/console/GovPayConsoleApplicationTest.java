package it.govpay.console;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.builder.SpringApplicationBuilder;

class GovPayConsoleApplicationTest {

    @Test
    void configureRegistersApplicationSource() {
        GovPayConsoleApplication application = new GovPayConsoleApplication();
        SpringApplicationBuilder builder = new SpringApplicationBuilder();

        SpringApplicationBuilder configured = application.configure(builder);

        assertSame(builder, configured);
    }

    @Test
    void jsonCustomizerIsNotNull() {
        GovPayConsoleApplication application = new GovPayConsoleApplication();
        application.timeZone = "Europe/Rome";

        Jackson2ObjectMapperBuilderCustomizer customizer = application.jsonCustomizer();

        assertNotNull(customizer);
    }
}
