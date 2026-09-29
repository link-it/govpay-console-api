package it.govpay.console.web;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rifiuta {@code ?cursor=} sulle operazioni che non dichiarano la paginazione
 * keyset. Senza questo controllo il parametro veniva semplicemente ignorato: la
 * risposta era 200, non portava mai {@code nextCursor}, e un client convinto di
 * paginare a cursore rileggeva la prima pagina all'infinito senza alcun
 * segnale.
 * <p>
 * La verifica e' sulla firma dell'operazione e non su un elenco di rotte: una
 * operazione supporta il cursore se e solo se lo dichiara nell'OpenAPI, da cui
 * il {@code @RequestParam("cursor")} del metodo generato. Un elenco andrebbe
 * tenuto allineato a mano ogni volta che una risorsa adotta la modalita'
 * keyset, e il caso in cui non lo si fa e' esattamente quello da evitare.
 */
@Component
public class CursorSupportInterceptor implements HandlerInterceptor {

    static final String CURSOR = "cursor";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!request.getParameterMap().containsKey(CURSOR)) {
            return true;
        }
        if (handler instanceof HandlerMethod handlerMethod && dichiaraCursor(handlerMethod)) {
            return true;
        }
        throw new BadRequestException(
                "Parametro 'cursor' non supportato da questa risorsa: la paginazione e' per pagina "
                        + "(?page=N&limit=L). Le risorse con paginazione keyset lo dichiarano nell'OpenAPI "
                        + "e restituiscono 'nextCursor'.");
    }

    private static boolean dichiaraCursor(HandlerMethod handlerMethod) {
        for (var parametro : handlerMethod.getMethodParameters()) {
            RequestParam annotazione = parametro.getParameterAnnotation(RequestParam.class);
            if (annotazione != null && CURSOR.equals(annotazione.value())) {
                return true;
            }
        }
        return false;
    }
}
