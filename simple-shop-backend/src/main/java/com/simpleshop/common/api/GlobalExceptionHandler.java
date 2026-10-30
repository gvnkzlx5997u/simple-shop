package com.simpleshop.common.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import com.fasterxml.jackson.databind.JsonMappingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常翻译：把业务异常、参数错误、上传超限与未预期异常统一成 {@link ApiResponse}。
 *
 * <p>参数错误统一返回 HTTP 400 + 50002；能从异常中定位字段时，附带 {@code errors} 列表
 * （字段名使用请求体的 JSON 键，嵌套字段用点号分隔）。错误响应不携带堆栈或内部细节；
 * 未预期异常只记录服务端日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code, exception.getMessage()));
    }

    /** {@code @Valid} 请求体校验失败；字段级错误逐个列出。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(BindException exception) {
        List<FieldErrorData> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        log.debug("rejected invalid request body: {}", errors);
        return badRequest(errors);
    }

    /** 方法参数级校验失败（{@code @Validated} 与约束注解组合时）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        List<FieldErrorData> errors = exception.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        log.debug("rejected invalid method argument: {}", errors);
        return badRequest(errors);
    }

    /** JSON 无法解析：空 body、类型不匹配、未知枚举值等；尽量给出出错字段。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        log.debug("rejected malformed request body: {}", exception.getMessage());
        String field = malformedField(exception);
        return badRequest(field == null
                ? List.of()
                : List.of(new FieldErrorData(field, "malformed or missing value")));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        log.debug("rejected malformed request: {}", exception.getMessage());
        FieldErrorData error = singleFieldError(exception);
        return badRequest(error == null ? List.of() : List.of(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        log.warn("rejected oversized upload: {}", exception.getMessage());
        return ResponseEntity.status(ErrorCode.MEDIA_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.MEDIA_INVALID));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.httpStatus())
                .body(ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("unexpected error: method={} path={}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(List<FieldErrorData> errors) {
        ApiResponse<Void> body = errors.isEmpty()
                ? ApiResponse.error(ErrorCode.PARAM_INVALID)
                : ApiResponse.invalid(errors);
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus()).body(body);
    }

    private static FieldErrorData fieldError(FieldError error) {
        return new FieldErrorData(jsonName(error.getField()), error.getDefaultMessage());
    }

    private static FieldErrorData fieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int separator = path.indexOf('.');
        String field = separator >= 0 ? path.substring(separator + 1) : path;
        return new FieldErrorData(jsonName(field), violation.getMessage());
    }

    /** 从单个参数的异常中提取字段；无法定位时返回 {@code null}。 */
    private static FieldErrorData singleFieldError(Exception exception) {
        if (exception instanceof MissingServletRequestParameterException missing) {
            return new FieldErrorData(jsonName(missing.getParameterName()), "is required");
        }
        if (exception instanceof MissingPathVariableException missing) {
            return new FieldErrorData(jsonName(missing.getVariableName()), "is required");
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            return new FieldErrorData(jsonName(mismatch.getName()), "has an invalid value");
        }
        if (exception instanceof MissingServletRequestPartException missing) {
            return new FieldErrorData(jsonName(missing.getRequestPartName()), "is required");
        }
        return null;
    }

    /** 从 Jackson 的路径信息中取最后一个字段名；空 body 等场景返回 {@code null}。 */
    private static String malformedField(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof JsonMappingException mapping) {
                List<JsonMappingException.Reference> path = mapping.getPath();
                for (int index = path.size() - 1; index >= 0; index--) {
                    String name = path.get(index).getFieldName();
                    if (name != null && !name.isBlank()) {
                        return jsonName(name);
                    }
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return null;
    }

    /** Java 属性名 → 请求体 JSON 键（camelCase → snake_case）。 */
    private static String jsonName(String field) {
        StringBuilder result = new StringBuilder(field.length() + 4);
        for (int index = 0; index < field.length(); index++) {
            char current = field.charAt(index);
            if (Character.isUpperCase(current)) {
                if (index > 0 && field.charAt(index - 1) != '.') {
                    result.append('_');
                }
                result.append(Character.toLowerCase(current));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
