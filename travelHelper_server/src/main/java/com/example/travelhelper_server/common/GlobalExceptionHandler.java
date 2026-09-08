package com.example.travelhelper_server.common;

import com.example.travelhelper_server.vo.Result;
import org.springframework.validation.FieldError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;
//拦截控制器异常
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)    //处理参数异常
    public ResponseEntity<Result<Void>> handleException(MethodArgumentNotValidException e){
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(","));
        return ResponseEntity.badRequest().body(Result.fail(400,message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatusException(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Result.fail(e.getStatusCode().value(), e.getReason()));
    }
}
