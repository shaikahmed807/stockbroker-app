package com.stockbroker.exception;

import com.stockbroker.exception.GlobalExceptionHandler.ApiError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler – consistent JSON errors")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsItsStatusAndMessage() {
        ResponseEntity<ApiError> res = handler.handleApi(new ApiException(HttpStatus.CONFLICT, "Taken"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().status()).isEqualTo(409);
        assertThat(res.getBody().error()).isEqualTo("Conflict");
        assertThat(res.getBody().message()).isEqualTo("Taken");
        assertThat(res.getBody().timestamp()).isNotNull();
    }

    @Test
    void badCredentialsBecomeGeneric401() {
        ResponseEntity<ApiError> res = handler.handleBadCredentials(new BadCredentialsException("user x not found"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // must not leak whether the email exists
        assertThat(res.getBody().message()).isEqualTo("Invalid email or password");
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() {
        ResponseEntity<ApiError> res = handler.handleOther(new IllegalStateException("SQL syntax error near users"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().message()).doesNotContain("SQL");
    }
}
