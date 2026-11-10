package com.pic2filament.service;

import org.springframework.http.HttpStatus;

/** Error de la aplicación con un mensaje en español para el usuario y su código HTTP. */
public class Pic2filamentException extends RuntimeException {

    private final HttpStatus status;

    public Pic2filamentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public Pic2filamentException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static Pic2filamentException invalid(String message) {
        return new Pic2filamentException(HttpStatus.BAD_REQUEST, message);
    }

    public static Pic2filamentException notFound() {
        return new Pic2filamentException(HttpStatus.NOT_FOUND,
                "El trabajo no existe o ha caducado. Vuelve a subir la imagen.");
    }
}
