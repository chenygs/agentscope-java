package io.agentscope.builder.saton.auth.service.ex;

public class BadCredentialsException extends RuntimeException {

    public BadCredentialsException(String message) {
        super(message);
    }
}
