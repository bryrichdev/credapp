package dev.bryrich.credapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProviderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleProviderNotFound(ProviderNotFoundException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(LicenseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleLicenseNotFound(LicenseNotFoundException ex) {
        return ex.getMessage();
    }


}