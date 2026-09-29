package it.govpay.console.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Interceptor trasversali alle risorse. Separata da
 * {@link it.govpay.console.gde.GdeWebMvcConfig}, che registra quello del
 * giornale eventi: non hanno nulla in comune se non il punto di aggancio.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CursorSupportInterceptor cursorSupportInterceptor;

    public WebMvcConfig(CursorSupportInterceptor cursorSupportInterceptor) {
        this.cursorSupportInterceptor = cursorSupportInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cursorSupportInterceptor);
    }
}
