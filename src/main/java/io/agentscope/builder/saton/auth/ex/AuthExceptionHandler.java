package io.agentscope.builder.saton.auth.ex;

import cn.dev33.satoken.exception.NotLoginException;
import io.agentscope.builder.saton.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiError> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "not logged in: " + e.getType()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, e.getMessage()));
    }

    @ExceptionHandler(io.agentscope.builder.saton.common.error.NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            io.agentscope.builder.saton.common.error.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, e.getMessage()));
    }

    @ExceptionHandler(io.agentscope.builder.saton.common.error.ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            io.agentscope.builder.saton.common.error.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, e.getMessage()));
    }
}
