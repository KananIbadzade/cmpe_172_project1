package edu.sjsu.cmpe172.advising.service.exception;

public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message) {
        super(message);
    }
}
