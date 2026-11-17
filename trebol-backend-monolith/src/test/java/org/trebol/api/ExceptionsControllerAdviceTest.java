/*
 * Copyright (c) 2020-2024 The Trebol eCommerce Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished
 * to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.trebol.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.trebol.api.models.AppError;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionsControllerAdviceTest {
    final ExceptionsControllerAdvice advice = new ExceptionsControllerAdvice();

    @Test
    void duplicate_data_is_a_conflict_that_should_not_be_retried() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "could not execute statement",
            new SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key 'UK_PRODUCT_NAME'"));

        AppError result = advice.handleException(ex);

        assertEquals("CONFLICT_01", result.getCode());
        assertFalse(result.isCanRetry());
    }

    @Test
    void no_free_database_connection_is_a_retryable_503() {
        assertRetryable503(new CannotCreateTransactionException(
            "Could not open JPA EntityManager for transaction"));
    }

    @Test
    void database_unreachable_is_a_retryable_503() {
        assertRetryable503(new DataAccessResourceFailureException(
            "Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available]"));
    }

    @Test
    void transient_database_errors_are_a_retryable_503() {
        assertRetryable503(new QueryTimeoutException("Query timed out"));
    }

    private void assertRetryable503(Exception ex) {
        ResponseEntity<AppError> result = advice.handleTemporaryDatabaseProblem(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        assertEquals(ExceptionsControllerAdvice.RETRY_AFTER_SECONDS,
            result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertNotNull(result.getBody());
        assertEquals("BUSY_01", result.getBody().getCode());
        assertTrue(result.getBody().isCanRetry());
    }
}
