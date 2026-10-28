package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.InvalidCredentialsException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import edu.sjsu.cmpe172.advising.service.exception.SlotConflictException;
import edu.sjsu.cmpe172.advising.service.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 7807 ProblemDetail. The parent class keeps Spring MVC's own
 * errors (malformed JSON, wrong method, unknown path) at their proper 4xx status, so the
 * catch-all below only sees genuinely unexpected failures.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({UnauthorizedException.class, InvalidCredentialsException.class})
    public ProblemDetail handleUnauthorized(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(SlotConflictException.class)
    public ProblemDetail handleConflict(SlotConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Full stack trace goes to the log; the client only gets a generic message. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("unexpected error type={}", ex.getClass().getSimpleName(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}

