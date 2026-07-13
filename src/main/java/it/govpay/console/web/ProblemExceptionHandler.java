package it.govpay.console.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import it.govpay.console.avviso.AvvisoMbtException;
import it.govpay.console.avviso.AvvisoNonDisponibileException;
import it.govpay.console.avviso.StampeNotConfiguredException;
import it.govpay.console.avviso.StampeUnavailableException;
import it.govpay.console.model.Problem;
import it.govpay.console.model.ProblemErrorsInner;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ProblemExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    private static final String DETAIL_500 = "Errore interno del server.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        List<ProblemErrorsInner> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ProblemErrorsInner().field(fe.getField()).message(fe.getDefaultMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "La richiesta contiene parametri non validi.", request, errors, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Problem> handleConstraintViolation(ConstraintViolationException ex,
                                                             HttpServletRequest request) {
        List<ProblemErrorsInner> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv ->
                errors.add(new ProblemErrorsInner()
                        .field(cv.getPropertyPath().toString())
                        .message(cv.getMessage())));
        return build(HttpStatus.BAD_REQUEST, "La richiesta contiene parametri non validi.", request, errors, ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Problem> handleNotReadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Body della richiesta non leggibile.", request, null, ex);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Problem> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Problem> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                      HttpServletRequest request) {
        String detail = "Valore non valido per il parametro '" + ex.getName() + "': "
                + (ex.getValue() != null ? ex.getValue() : "<null>");
        return build(HttpStatus.BAD_REQUEST, detail, request, null, ex);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class, NotFoundException.class })
    public ResponseEntity<Problem> handleNotFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler({ OptimisticLockException.class, IfMatchMismatchException.class })
    public ResponseEntity<Problem> handlePreconditionFailed(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.PRECONDITION_FAILED, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ResponseEntity<Problem> handlePreconditionRequired(PreconditionRequiredException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.PRECONDITION_REQUIRED, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Problem> handleMissingHeader(MissingRequestHeaderException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.PRECONDITION_REQUIRED,
                "Header obbligatorio mancante: " + ex.getHeaderName() + ".", request, null, ex);
    }

    @ExceptionHandler({ DataIntegrityViolationException.class, ConflictException.class })
    public ResponseEntity<Problem> handleConflict(Exception ex, HttpServletRequest request) {
        String detail = ex instanceof ConflictException
                ? ex.getMessage()
                : "Conflitto con lo stato corrente della risorsa.";
        return build(HttpStatus.CONFLICT, detail, request, null, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Problem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Problem> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(NotAcceptableMediaTypeException.class)
    public ResponseEntity<Problem> handleNotAcceptableMediaType(NotAcceptableMediaTypeException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Problem> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE,
                "Accept header non compatibile coi content-type supportati.",
                request, null, ex);
    }

    @ExceptionHandler({ AvvisoMbtException.class, AvvisoNonDisponibileException.class,
            UnprocessableEntityException.class })
    public ResponseEntity<Problem> handleUnprocessable(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Problem> handlePayloadTooLarge(PayloadTooLargeException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<Problem> handleUnsupportedMediaType(UnsupportedMediaTypeException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Problem> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                               HttpServletRequest request) {
        String detail = "Content-Type non supportato"
                + (ex.getContentType() != null ? " (" + ex.getContentType() + ")" : "")
                + ": tipi ammessi " + ex.getSupportedMediaTypes() + ".";
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, detail, request, null, ex);
    }

    @ExceptionHandler(StampeUnavailableException.class)
    public ResponseEntity<Problem> handleStampeUnavailable(StampeUnavailableException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(StampeNotConfiguredException.class)
    public ResponseEntity<Problem> handleStampeNotConfigured(StampeNotConfiguredException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, DETAIL_500, request, null, ex);
    }

    private ResponseEntity<Problem> build(HttpStatus status,
                                          String detail,
                                          HttpServletRequest request,
                                          List<ProblemErrorsInner> errors,
                                          Exception ex) {
        Problem problem = new Problem(status.value())
                .title(status.getReasonPhrase())
                .detail(detail)
                .instance(URI.create(request.getRequestURI()));
        if (errors != null && !errors.isEmpty()) {
            problem.setErrors(errors);
        }
        logException(status, request, ex);
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(problem);
    }

    private void logException(HttpStatus status, HttpServletRequest request, Exception ex) {
        String msg = "{} {} -> {} {}";
        if (status.is5xxServerError()) {
            log.error(msg, request.getMethod(), request.getRequestURI(), status.value(), status.getReasonPhrase(), ex);
        } else {
            log.warn(msg, request.getMethod(), request.getRequestURI(), status.value(), status.getReasonPhrase(),
                    ex.toString());
        }
    }
}
