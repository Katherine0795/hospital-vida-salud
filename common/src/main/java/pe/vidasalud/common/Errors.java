package pe.vidasalud.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> business(ResponseStatusException e) {
    return response(e.getStatusCode(), e.getReason());
  }

  @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
  public ResponseEntity<ProblemDetail> input(Exception e) {
    return response(HttpStatus.BAD_REQUEST, "Datos invalidos o campos obligatorios ausentes");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> duplicate(Exception e) {
    return response(HttpStatus.CONFLICT, "Documento, agenda o referencia duplicada");
  }

  private ResponseEntity<ProblemDetail> response(HttpStatusCode code, String reason) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(code, reason == null ? "Solicitud rechazada" : reason);
    return ResponseEntity.status(code).body(p);
  }

  public static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso inexistente");
  }

  public static ResponseStatusException conflict(String s) {
    return new ResponseStatusException(HttpStatus.CONFLICT, s);
  }

  public static ResponseStatusException invalid(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }
}
