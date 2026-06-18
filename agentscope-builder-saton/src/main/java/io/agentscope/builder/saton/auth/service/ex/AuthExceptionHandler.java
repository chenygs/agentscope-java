package io.agentscope.builder.saton.auth.service.ex;

import cn.dev33.satoken.exception.NotLoginException;
import io.agentscope.builder.saton.common.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(401, "not logged in: " + e.getType()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<R<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(401, e.getMessage()));
    }

    @ExceptionHandler(io.agentscope.builder.saton.common.error.NotFoundException.class)
    public ResponseEntity<R<Void>> handleNotFound(
            io.agentscope.builder.saton.common.error.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(404, e.getMessage()));
    }

    @ExceptionHandler(io.agentscope.builder.saton.common.error.ConflictException.class)
    public ResponseEntity<R<Void>> handleConflict(
            io.agentscope.builder.saton.common.error.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(409, e.getMessage()));
    }

    @ExceptionHandler(io.agentscope.builder.saton.common.error.ForbiddenException.class)
    public ResponseEntity<R<Void>> handleForbidden(
            io.agentscope.builder.saton.common.error.ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(403, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, e.getMessage()));
    }
}
