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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.trebol.api.models.AppError;
import org.trebol.common.exceptions.BadInputException;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Catches some known exceptions, commonly declared at the controller level.<br/>
 * Then it sends custom responses to consumers of the REST API.<br/>
 *
 * The following exceptions are supported and mapped to certain status codes:
 * <ul>
 * <li>{@link jakarta.persistence.EntityNotFoundException} -> 404 NOT FOUND</li>
 * <li>{@link jakarta.persistence.EntityExistsException} -> 400 BAD REQUEST</li>
 * <li>{@link org.trebol.common.exceptions.BadInputException} -> 400 REQUEST</li>
 * <li>{@link org.springframework.web.bind.MethodArgumentNotValidException} -> 400 BAD REQUEST</li>
 * <li>{@link org.springframework.dao.DataIntegrityViolationException} -> 409 CONFLICT (e.g. a double submit)</li>
 * <li>Temporary database problems (no free connection, failover, timeouts) -> 503 SERVICE UNAVAILABLE
 * with a Retry-After header</li>
 * </ul>
 *
 * @see org.springframework.http.HttpStatus
 */
@RestControllerAdvice
public class ExceptionsControllerAdvice {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionsControllerAdvice.class);
    /** Seconds a client should wait before retrying after a 503. */
    static final String RETRY_AFTER_SECONDS = "2";

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(EntityNotFoundException.class)
    public AppError handleException(EntityNotFoundException ex) {
        return AppError.builder()
            .code("NOTFOUND_01")
            .message("The requested resource could not be found")
            .canRetry(false)
            .build();
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(EntityExistsException.class)
    public AppError handleException(EntityExistsException ex) {
        return AppError.builder()
            .code("EXISTS_01")
            .message("There is one such resource with the same identifying properties that already exists")
            .canRetry(false)
            .build();
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(BadInputException.class)
    public AppError handleException(BadInputException ex) {
        return AppError.builder()
            .code("REJECTED_01")
            .message("The request body did not meet the required criteria")
            .canRetry(true)
            .build();
    }

    /**
     * The request conflicts with data that is already stored, most often because the same request
     * arrived twice at the same moment (a double click): both passed the "does it exist?" check,
     * and the database's unique constraint rejected the second one. This is a client-side
     * condition, so it is answered with 409 instead of a 500 that would look like a server fault.
     */
    @ResponseStatus(CONFLICT)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public AppError handleException(DataIntegrityViolationException ex) {
        logger.warn("Request rejected by a data constraint: {}", ex.getMostSpecificCause().getMessage());
        return AppError.builder()
            .code("CONFLICT_01")
            .message("The request conflicts with data that already exists; it may have been submitted twice")
            .canRetry(false)
            .build();
    }

    /**
     * The database could not be reached in time: no free connection in the pool (busy), RDS Proxy
     * or the database briefly unavailable (e.g. a Multi-AZ failover), a lock or query timeout.
     * Nothing was saved, so the client may safely try again: 503 with a Retry-After hint.
     */
    @ExceptionHandler({
        CannotCreateTransactionException.class,
        DataAccessResourceFailureException.class,
        TransientDataAccessException.class
    })
    public ResponseEntity<AppError> handleTemporaryDatabaseProblem(Exception ex) {
        logger.warn("Database temporarily unavailable, answered 503: {}", ex.getMessage());
        AppError body = AppError.builder()
            .code("BUSY_01")
            .message("The service is busy; please retry shortly")
            .canRetry(true)
            .build();
        return ResponseEntity.status(SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
            .body(body);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AppError handleException(MethodArgumentNotValidException ex) {
        StringBuilder stringBuilder = new StringBuilder();
        List<ObjectError> allErrors = ex.getBindingResult().getAllErrors();
        int errorCollectionSize = allErrors.size();
        for (int i = 0; i < errorCollectionSize; i++) {
            ObjectError error = allErrors.get(i);
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            stringBuilder.append(String.format("%s: %s", fieldName, errorMessage));
            if (errorCollectionSize > i+1) {
                stringBuilder.append(String.format("%n"));
            }
        }
        return AppError.builder()
            .code("REJECTED_02")
            .message("The request body did not meet the basic required criteria for acceptance")
            .detailMessage(stringBuilder.toString())
            .build();
    }
}
