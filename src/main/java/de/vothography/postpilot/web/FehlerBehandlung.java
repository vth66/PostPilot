package de.vothography.postpilot.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * Sorgt dafür, dass in der Oberfläche eine lesbare Meldung ankommt statt einer
 * technischen Fehlerseite.
 */
@RestControllerAdvice
public class FehlerBehandlung {

    public record Fehlermeldung(String meldung) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Fehlermeldung ungueltigeEingabe(IllegalArgumentException e) {
        return new Fehlermeldung(e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Fehlermeldung dateiProblem(IOException e) {
        return new Fehlermeldung("Dateizugriff fehlgeschlagen: " + e.getMessage());
    }
}
