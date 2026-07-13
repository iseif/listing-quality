package dev.iseif.listingquality.controller;

import dev.iseif.listingquality.service.exception.AiProviderException;
import dev.iseif.listingquality.service.exception.AiProviderUnavailableException;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import jakarta.servlet.http.HttpServletRequest;
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
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(AiProviderUnavailableException.class)
  ProblemDetail handleUnavailable(
      AiProviderUnavailableException exception,
      HttpServletRequest request) {
    log.warn("AI provider is temporarily unavailable", exception);
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AI provider unavailable",
        "AI_PROVIDER_UNAVAILABLE",
        "Listing review is temporarily unavailable. Please try again.",
        request);
  }

  @ExceptionHandler(AiProviderException.class)
  ProblemDetail handleProviderFailure(AiProviderException exception, HttpServletRequest request) {
    log.error("Listing review provider request failed", exception);
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AI provider unavailable",
        "AI_PROVIDER_UNAVAILABLE",
        "Listing review is temporarily unavailable. Please try again.",
        request);
  }

  @ExceptionHandler(InvalidAiResponseException.class)
  ProblemDetail handleInvalidAiResponse(
      InvalidAiResponseException exception,
      HttpServletRequest request) {
    log.warn("AI provider returned an invalid listing review", exception);
    return problem(
        HttpStatus.BAD_GATEWAY,
        "Listing review unavailable",
        "AI_RESPONSE_INVALID",
        "We could not complete the listing review. Please try again.",
        request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Unexpected API error", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Unexpected error",
        "INTERNAL_ERROR",
        "An unexpected error occurred. Please try again later.",
        request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(exception, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
      setCodeAndType(problemDetail, deriveCode(statusCode));
      if (request instanceof ServletWebRequest servletWebRequest) {
        problemDetail.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
      }
    }
    return response;
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
    return status != null ? status.name() : "ERROR";
  }
}
