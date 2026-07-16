package com.sount.restful.common.resolver;

/**
 * Signals a transient project-index or PSI failure while resolving endpoints.
 * The endpoint index must retry these failures instead of publishing an empty,
 * apparently successful snapshot.
 */
public final class EndpointResolutionException extends RuntimeException {
    public EndpointResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
