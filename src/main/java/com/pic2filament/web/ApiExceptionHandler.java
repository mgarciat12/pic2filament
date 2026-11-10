package com.pic2filament.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.service.ImageLoader;
import com.pic2filament.service.Pic2filamentException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Convierte los errores de la API en respuestas JSON coherentes con mensajes en español. Las
 * peticiones de páginas (no /api) se dejan al controlador de errores de Spring, que muestra la
 * plantilla {@code error.html}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final long maxUpload;

    public ApiExceptionHandler(Pic2filamentProperties props) {
        this.maxUpload = props.maxUploadSize().toBytes();
    }

    @ExceptionHandler(Pic2filamentException.class)
    ResponseEntity<ErrorResponse> app(Pic2filamentException e, HttpServletRequest req) {
        return body(e.status(), e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> details = new ArrayList<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            details.add(fe.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors().forEach(g -> details.add(g.getDefaultMessage()));
        String msg = details.isEmpty() ? "Los ajustes no son válidos." : details.get(0);
        return body(HttpStatus.BAD_REQUEST, msg, details, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> methodValidation(HandlerMethodValidationException e, HttpServletRequest req) {
        List<String> details = new ArrayList<>();
        e.getAllErrors().forEach(err -> details.add(err.getDefaultMessage()));
        String msg = details.isEmpty() ? "Los parámetros no son válidos." : details.get(0);
        return body(HttpStatus.BAD_REQUEST, msg, details, req);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<ErrorResponse> unreadable(Exception e, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "La petición no tiene un formato válido.", List.of(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return body(HttpStatus.CONTENT_TOO_LARGE, ImageLoader.tooLarge(maxUpload).getMessage(), List.of(), req);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorResponse> multipart(MultipartException e, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "No se ha podido leer el fichero enviado.", List.of(), req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido.", List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido.", List.of(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noResource(NoResourceFoundException e, HttpServletRequest req)
            throws NoResourceFoundException {
        if (!isApi(req)) {
            throw e; // páginas: 404 con la plantilla de error
        }
        return body(HttpStatus.NOT_FOUND, "Recurso no encontrado.", List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest req) throws Exception {
        if (!isApi(req)) {
            throw e;
        }
        log.error("Error inesperado en {}", req.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Se ha producido un error inesperado.", List.of(), req);
    }

    private static boolean isApi(HttpServletRequest req) {
        return req.getRequestURI().startsWith(req.getContextPath() + "/api/");
    }

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, String message, List<String> details,
            HttpServletRequest req) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, details,
                        req.getRequestURI()));
    }
}
