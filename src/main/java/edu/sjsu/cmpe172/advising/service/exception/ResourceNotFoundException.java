package edu.sjsu.cmpe172.advising.service.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, long id) {
        super(resource + " " + id + " not found");
    }
}
