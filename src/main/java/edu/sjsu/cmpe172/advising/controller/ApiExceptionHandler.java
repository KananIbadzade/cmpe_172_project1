package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.InvalidCredentialsException;
import edu.sjsu.cmpe172.advising.service.exception.ResourceNotFoundException;
import edu.sjsu.cmpe172.advising.service.exception.SlotConflictException;
import edu.sjsu.cmpe172.advising.service.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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
}

