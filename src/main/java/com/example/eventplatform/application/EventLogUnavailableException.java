package com.example.eventplatform.application;

public class EventLogUnavailableException extends RuntimeException {
    public EventLogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
