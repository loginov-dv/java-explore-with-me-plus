package ru.practicum.ewm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.ewm.dto.ApiError;
import ru.practicum.ewm.exception.AccessViolationException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodArgumentNotValidException(final MethodArgumentNotValidException e) {
        log.warn("400 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach((error) -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();

            errors.put(fieldName, errorMessage);
        });

        return new ApiError(errors.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining("; ")),
                "Request parameters was not valid",
                HttpStatus.BAD_REQUEST.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationException(final ValidationException e) {
        log.warn("400 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getMessage(),
                "Invalid operation",
                HttpStatus.BAD_REQUEST.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(AccessViolationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleAccessViolationException(final AccessViolationException e) {
        log.warn("403 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getMessage(),
                "Forbidden action",
                HttpStatus.FORBIDDEN.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFoundException(final NotFoundException e) {
        log.warn("404 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getMessage(),
                "Required object was not found",
                HttpStatus.NOT_FOUND.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflictException(final ConflictException e) {
        log.warn("409 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getMessage(),
                "Parameter value conflict",
                HttpStatus.CONFLICT.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(org.hibernate.exception.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleHibernateConstraintViolationException(final org.hibernate.exception.ConstraintViolationException e) {
        log.warn("409 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getConstraintName() + ": " + e.getKind(),
                "Integrity constraint has been violated",
                HttpStatus.CONFLICT.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleJakartaConstraintViolationException(final jakarta.validation.ConstraintViolationException e) {
        log.warn("400 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        Map<String, String> errors = new HashMap<>();
        e.getConstraintViolations().forEach(constraintViolation -> {
            String propertyName = constraintViolation.getPropertyPath().toString();
            String errorMessage = constraintViolation.getMessage();

            errors.put(propertyName, errorMessage);
        });

        return new ApiError(errors.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; ")),
                "Request parameters was not valid",
                HttpStatus.BAD_REQUEST.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleException(final Exception e) {
        log.warn("500 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getMessage(),
                "Unexpected error",
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleIllegalStateException(final IllegalStateException e) {
        log.warn("409 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError(e.getMessage(),
                "Illegal state value",
                HttpStatus.CONFLICT.name(),
                LocalDateTime.now().format(formatter));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingServletRequestParameterException(final MissingServletRequestParameterException e) {
        log.warn("400 {}", e.getMessage(), e);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);

        return new ApiError("Missing required parameter: " + e.getParameterName() + " (" + e.getParameterType() + ")",
                "Missing required parameters",
                HttpStatus.BAD_REQUEST.name(),
                LocalDateTime.now().format(formatter));
    }
}
