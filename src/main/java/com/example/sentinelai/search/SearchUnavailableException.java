package com.example.sentinelai.search;

/** Elasticsearch could not answer (down, timed out, index missing). Mapped to HTTP 503. */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
