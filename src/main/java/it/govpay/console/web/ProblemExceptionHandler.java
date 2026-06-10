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
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class, NotFoundException.class })
    public ResponseEntity<Problem> handleNotFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler({ OptimisticLockException.class, IfMatchMismatchException.class })
    public ResponseEntity<Problem> handlePreconditionFailed(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.PRECONDITION_FAILED, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Problem> handleDataIntegrity(DataIntegrityViolationException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflitto con lo stato corrente della risorsa.", request, null, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Problem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Problem> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null, ex);
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
