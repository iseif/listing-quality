package dev.iseif.listingquality.enrichment.controller;

import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Locale;

@RestControllerAdvice
public class EnrichmentExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(EnrichmentExceptionHandler.class);

  @ExceptionHandler(ModelExecutionException.class)
  ProblemDetail handleModelFailure(
      ModelExecutionException exception,
      HttpServletRequest request) {
    log.warn(
        "Enrichment model execution failed: provider={}, category={}, fallbackEligible={}",
        exception.providerId(), exception.category(), exception.eligible());
    if (exception.category() == ModelFailureCategory.AUTHENTICATION_FAILED
        || exception.category() == ModelFailureCategory.CONFIGURATION_ERROR) {
      return problem(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Enrichment configuration error",
          "ENRICHMENT_CONFIGURATION_ERROR",
          "Book enrichment is not configured correctly.",
          request);
    }
    if (exception.category() == ModelFailureCategory.SAFETY_REFUSAL
        || exception.category() == ModelFailureCategory.INVALID_MODEL_OUTPUT) {
      return invalidResponse(request);
    }
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Enrichment provider unavailable",
        "ENRICHMENT_PROVIDER_UNAVAILABLE",
        "Book enrichment is temporarily unavailable. Please try again.",
        request);
  }

  @ExceptionHandler(InvalidBookEnrichmentResponseException.class)
  ProblemDetail handleInvalidResponse(
      InvalidBookEnrichmentResponseException exception,
      HttpServletRequest request) {
    log.warn(
        "Enrichment output failed grounding validation: failure={}, field={}",
        exception.failure(), exception.field());
    return invalidResponse(request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Unexpected enrichment API error: type={}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Unexpected error",
        "INTERNAL_ERROR",
        "An unexpected error occurred. Please try again later.",
        request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(exception, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail detail) {
      setCodeAndType(detail, deriveCode(statusCode));
      if (request instanceof ServletWebRequest servletRequest) {
        detail.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
      }
    }
    return response;
  }

  private ProblemDetail invalidResponse(HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_GATEWAY,
        "Enrichment response invalid",
        "ENRICHMENT_RESPONSE_INVALID",
        "We could not produce a safely grounded book enrichment. Please try again.",
        request);
  }

  private ProblemDetail problem(
      HttpStatus status,
      String title,
      String code,
      String detail,
      HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    setCodeAndType(problem, code);
    return problem;
  }

  private void setCodeAndType(ProblemDetail problem, String code) {
    problem.setProperty("code", code);
    problem.setType(URI.create("urn:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
  }

  private String deriveCode(HttpStatusCode statusCode) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    return status == null ? "ERROR" : status.name();
  }
}
