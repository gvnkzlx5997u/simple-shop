package com.simpleshop.web;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;

/**
 * 全局异常处理：把所有异常统一翻译为 {@link ApiResponse} + 契约约定的 HTTP 状态码
 * （第 11 章 §11.3.3、§11.3.4、§11.6.1）。
 *
 * <h2>分工</h2>
 * <ul>
 *   <li><b>业务拒绝</b>（{@link BusinessException}）→ 用异常携带的 {@link ErrorCode}，
 *       HTTP 状态取 {@link ErrorCode#httpStatus()}（<b>默认 200</b>）。</li>
 *   <li><b>报文/参数问题</b>（JSON 解析失败、必填缺失、类型不匹配、校验不通过、
 *       <b>媒体类型协商失败</b>）→ {@link ErrorCode#PARAM_INVALID}（{@code 50002}，HTTP <b>400</b>）。</li>
 *   <li><b>其它未预料异常</b> → {@link ErrorCode#INTERNAL_ERROR}（{@code 50000}，HTTP 500），
 *       <b>只记日志、不把异常细节下发</b>（避免泄漏内部结构）。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么手写而不是靠 Spring 默认行为</h2>
 * <p>Spring 默认的错误响应结构是 {@code {timestamp, status, error, path}}，
 * <b>不是</b>契约要求的 {@code {code, message, data}}。若不做这层翻译，前端无法按 {@code code} 分支，
 * 而 {@code code} 是「前端按错误码映射界面文案」机制（{@code UX-07}、{@code NFR-09}）的前提。
 *
 * <h2>⚠️ {@code detail} 的边界（安全）</h2>
 * <p>本类会把「哪个字段不合法」这类信息放进 {@code message}（例如校验失败时的字段名）。
 * 但<b>绝不</b>把请求体、口令码、密码或异常堆栈写入响应体：
 * {@code message} 会进日志、也可能被前端打印，泄漏面必须控制住（{@code NFR-12}）。
 * 堆栈只进服务端日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 业务拒绝
    // -------------------------------------------------------------------------

    /**
     * 业务规则拒绝。HTTP 状态默认 200（{@code 11.3.4}），仅会话失效/资源不存在/服务端错误例外。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        // 业务拒绝属正常分支：打 warn 且只记方法+路径+错误码，不记请求体（可能有口令码/密码）
        log.warn("business rejected: code={} path={} method={} reason={}",
                code.code(), request.getRequestURI(), request.getMethod(), ex.getMessage());
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code, ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 报文 / 参数问题 → 50002（HTTP 400）
    // -------------------------------------------------------------------------

    /**
     * 请求体校验不通过（{@code @Valid} + {@code jakarta.validation} 注解）。
     *
     * <p>把<b>字段名</b>放进 message，便于前端就地提示；但<b>不</b>回显用户填的值。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        String detail = describeFieldErrors(ex.getBindingResult().getFieldErrors());
        log.warn("request body validation failed: path={} detail={}", request.getRequestURI(), detail);
        return badRequest(detail);
    }

    /** 表单/查询参数绑定校验不通过（非 {@code @RequestBody} 场景）。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex, HttpServletRequest request) {
        String detail = describeFieldErrors(ex.getBindingResult().getFieldErrors());
        log.warn("parameter binding failed: path={} detail={}", request.getRequestURI(), detail);
        return badRequest(detail);
    }

    /**
     * 请求体无法解析：JSON 语法错误，<b>或字段类型/枚举值不匹配</b>，
     * 或（因 {@code JacksonConfig} 保留 {@code FAIL_ON_UNKNOWN_PROPERTIES}）
     * 出现契约未定义的字段。
     *
     * <p>⚠️ 这里<b>不</b>把 Jackson 的原始消息直接下发——它可能包含类名与完整字段路径。
     * 只给一句通用描述，细节进日志。
     *
     * <p>⚠️ 日志侧也只记<b>分类与位置</b>，<b>不</b>记 Jackson 的原始消息——
     * 理由见 {@link #causeCategory(Throwable)}。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        log.warn("request body unreadable: path={} cause={}", request.getRequestURI(),
                causeCategory(ex.getMostSpecificCause()));
        return badRequest("malformed request body");
    }

    /** 必填查询参数缺失。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        log.warn("missing request parameter: path={} param={}", request.getRequestURI(), ex.getParameterName());
        return badRequest("missing parameter: " + ex.getParameterName());
    }

    /** 参数类型不匹配（例如 {@code page=abc}）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        log.warn("parameter type mismatch: path={} name={}", request.getRequestURI(), ex.getName());
        return badRequest("invalid parameter: " + ex.getName());
    }

    /**
     * <b>Controller 方法参数</b>上的约束注解不通过（如 {@code @Min(1) page}、{@code @NotBlank} 的
     * {@code @RequestParam}）→ {@code 50002}（HTTP <b>400</b>）。
     *
     * <h2>⚠️ 本处理器是 S6 实测发现的——不加它，这些请求会返回 <b>500</b></h2>
     * <p>Spring Framework 6.1 起，Controller 方法参数上的 {@code jakarta.validation} 约束由
     * <b>内建的方法校验</b>执行（<b>不需要</b>在类上加 {@code @Validated}），失败时抛
     * {@link HandlerMethodValidationException}。注意它<b>自己就带着 400 的语义</b>
     * （异常消息即 {@code 400 BAD_REQUEST "Validation failure"}）——Spring 默认也会把它渲染成 400。
     *
     * <p>但本项目有一个<b>兜底的</b> {@code @ExceptionHandler(Exception.class)}（契约要求所有未预料
     * 异常都变成 {@code 50000}+500）。Spring 选择处理器时按<b>最具体</b>匹配，
     * 而 {@code HandlerMethodValidationException} 当时<b>不在</b>任何分支里，
     * 于是它落到兜底分支，被翻译成 <b>500 + 50000</b>：
     * <pre>
     * S6 实测（未加本处理器时）：
     *   GET /api/seller/product/intentions?page=0        → 500（应为 400 + 50002）
     *   GET /api/seller/intention/passcode?intention_id= → 500（应为 400 + 50002）
     * </pre>
     * 这与 S3 发现的「媒体类型协商失败」是<b>同一类缺陷</b>：
     * <b>客户端把请求写错，却被报成服务端内部错误</b>——既误导排查方向，
     * 又污染 500 的监控告警（{@code 11.3.4} 对 400 的定义正是「参数格式错误」，它就属于 400）。
     *
     * <h2>为什么归到 {@code 50002} 而不是保留 400 的通用结构</h2>
     * <p>契约的 {@code 11.3.4} 规定 400 场景要携带业务码 {@code 50002}（「参数缺失或格式错误」）。
     * 前端按 {@code code} 映射文案（{@code UX-07}、{@code NFR-09}），
     * 因此必须给出它已持有映射的那个码，而不是一个裸 400。
     *
     * <p>⚠️ 与上面 {@code MethodArgumentTypeMismatch}（{@code page=abc}）的分工：
     * 那一条是<b>连类型都转不出来</b>，这一条是<b>转出来了但取值越界</b>。
     * 两者对前端而言是同一件事（报文里的参数不可接受），故返回同一个码。
     *
     * @see GlobalExceptionHandler 类注释的「报文/参数问题」一节
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex,
                                                                    HttpServletRequest request) {
        String detail = describeParameterErrors(ex);
        log.warn("method parameter validation failed: path={} detail={}", request.getRequestURI(), detail);
        return badRequest(detail);
    }

    // -------------------------------------------------------------------------
    // 文件上传 → 20009
    // -------------------------------------------------------------------------

    /**
     * 上传文件超过 {@code spring.servlet.multipart.max-file-size}（{@code C-14} 的 5MB）。
     *
     * <p>⚠️ 这个异常由 Spring 在<b>进入 Controller 之前</b>抛出，因此 Controller 里
     * <b>没有机会</b>处理它。若不在此处捕获，用户会看到 500 而不是契约要求的
     * {@code 20009}（{@code M10-31}「仅支持 JPG／PNG，单张不超过 5MB」）。
     *
     * <p>注意：这只是<b>第一道</b>尺寸防线。Service 仍须独立校验真实内容与大小
     * （{@code NFR-11} 要求「绕过前端直传 >5MB 仍被拒」成立）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex,
                                                            HttpServletRequest request) {
        log.warn("upload too large: path={} cause={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.IMAGE_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.IMAGE_INVALID));
    }

    /** 缺少 multipart 的 {@code file} 部分（字段名不是契约要求的 {@code file}）。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex,
                                                               HttpServletRequest request) {
        log.warn("missing multipart part: path={} part={}", request.getRequestURI(), ex.getRequestPartName());
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "missing part: " + ex.getRequestPartName()));
    }

    // -------------------------------------------------------------------------
    // 路由问题 → 20011 / 50002
    // -------------------------------------------------------------------------

    /**
     * 静态资源或路径不存在。
     *
     * <p>Spring Boot 3.2+ 对「无匹配 handler」抛 {@link NoResourceFoundException}（而非
     * {@code NoHandlerFoundException}）。契约里「路径不存在」应返回 404；
     * 但<b>资源不存在属于商品域的那个 404</b> 是 {@link ErrorCode#HISTORY_GOODS_NOT_FOUND}，
     * 那由 Service 主动抛出，<b>不</b>走这里。此处只处理「真的没有这个 URL」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex,
                                                              HttpServletRequest request) {
        log.warn("no resource: path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "not found: " + request.getRequestURI()));
    }

    /** 同上，兼容开启了 {@code throw-exception-if-no-handler-found} 的配置。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex,
                                                             HttpServletRequest request) {
        log.warn("no handler: path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "not found: " + request.getRequestURI()));
    }

    /** HTTP 方法不支持（例如对 {@code /api/seller/session} 用了 GET）。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        log.warn("method not supported: path={} method={}", request.getRequestURI(), ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "method not allowed: " + ex.getMethod()));
    }

    /**
     * 报文的媒体类型协商失败 → {@code 50002}（HTTP <b>400</b>）。
     *
     * <h2>为什么必须处理（不处理会变成 500）</h2>
     * <p>若请求带了 {@code Content-Type: text/plain} 却提交 JSON 体，Spring 会在
     * <b>进入 Controller 之前</b>抛 {@link HttpMediaTypeNotSupportedException}；
     * 若客户端用 {@code Accept} 要求服务端无法产出的类型，则抛
     * {@link HttpMediaTypeNotAcceptableException}。两者都不在上面任何分支里，
     * 会落到兜底的 {@code Exception} → <b>500 + 50000</b>。
     * 那会把「客户端写错了 Content-Type」这种最常见的联调错误显示成「服务端内部错误」，
     * 既误导排查方向，也会污染 500 的监控告警。
     *
     * <h2>为什么映射到 400 + {@code 50002}，而不返回语义更贴切的 415／406</h2>
     * <p>因为契约 §11.3.4 的 HTTP 状态码表是<b>枚举式</b>的，只列了
     * 200／400／401／403／404／500 六个，<b>没有</b> 415 与 406。
     * 而 §11.3.4 对 400 的定义正是「报文本身不合法（JSON 解析失败、必填缺失、参数格式错误）」——
     * 「声明了错误的媒体类型」属于同一类。因此归到 400 + {@code 50002} 更符合契约，
     * 也让前端能拿到一个它已持有文案映射的码。
     *
     * <p>注意这与下面 {@code 405}（方法不支持）的处理<b>并不矛盾</b>：
     * 媒体类型错误与「JSON 解析失败」是同一类（报文不可消费），故同归 400；
     * 而方法不匹配属于<b>路由</b>层面，且契约对该场景完全未作规定，
     * 保留 REST 惯例的 405 更有信息量。前端也不会偶然触发它（每个端点的动词是固定的）。
     */
    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMediaType(Exception ex, HttpServletRequest request) {
        log.warn("media type negotiation failed: path={} method={} type={} cause={}",
                request.getRequestURI(), request.getMethod(), ex.getClass().getSimpleName(), ex.getMessage());
        return badRequest("unsupported media type");
    }

    // -------------------------------------------------------------------------
    // 兜底 → 50000（HTTP 500）
    // -------------------------------------------------------------------------

    /**
     * 未预料的异常。<b>只记日志，不把细节下发</b>。
     *
     * <p>契约（{@code 11.3.4}）规定 500 <b>不携带业务码语义</b>、返回通用错误结构。
     * 本实现仍使用 {@link ApiResponse} 的形状（保持一致、便于前端统一解析），
     * 但 {@code code} 为 {@code 50000}、{@code message} 为固定英文短语，
     * <b>不含</b>异常类名、堆栈或 SQL 片段。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 未预料异常属 error：记完整堆栈，这是排查的唯一线索
        log.error("unexpected error: path={} method={}", request.getRequestURI(), request.getMethod(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    private static ResponseEntity<ApiResponse<Void>> badRequest(String detail) {
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, detail));
    }

    /**
     * 把请求体解析失败的原因压缩成<b>不含请求体内容</b>的短描述。
     *
     * <h2>⚠️ 为什么不直接用 {@code JsonProcessingException.getMessage()}</h2>
     * <p>Jackson 的解析异常消息是<b>从请求体原文里生成的</b>，会回显其中的片段。实测例子：
     * <pre>
     * 请求体 {"account":"seller","password":Abcd@1234}   ← 值少了引号
     * 原始消息 Unrecognized token 'Abcd': was expecting (JSON String, Number, ...)
     * </pre>
     * 也就是说，<b>只要客户端把报文写坏，口令明文就可能被回显进服务端日志</b>。
     * 这直接违反本项目的硬规则：{@code 12-H}、{@code NFR-12}、已定 Q-4/Q-9
     * （「日志不记录口令码明文」，修改密码还会写审计日志）。
     * 而解析失败发生在「字段语义尚未建立」的阶段，<b>无法按字段选择性脱敏</b>——
     * 唯一的办法就是不记录原始消息。
     *
     * <h2>保留了什么（够用且不泄漏）</h2>
     * <ul>
     *   <li><b>异常类别</b>：{@code UnrecognizedPropertyException}／{@code JsonEOFException}／
     *       {@code MismatchedInputException} 等，足以区分「多了字段／JSON 写坏了／类型不对」；</li>
     *   <li><b>行号列号</b>（Jackson 提供时）：定位报文写坏的位置，比一句自然语言更准；</li>
     *   <li><b>未知字段名</b>：这是 {@code FAIL_ON_UNKNOWN_PROPERTIES} 最常见的触发场景，
     *       而<b>字段名是元数据、不是用户数据</b>，可以安全记录，也正是排查时最需要的信息。</li>
     * </ul>
     */
    private static String causeCategory(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        // 未知字段：只记字段名（元数据），不记它旁边的任何值
        if (cause instanceof UnrecognizedPropertyException unrecognized) {
            return "UnrecognizedPropertyException(property=" + unrecognized.getPropertyName() + ")";
        }
        if (cause instanceof JsonProcessingException json) {
            JsonLocation location = json.getLocation();
            String position = location == null
                    ? ""
                    : "(line=" + location.getLineNr() + ", column=" + location.getColumnNr() + ")";
            return json.getClass().getSimpleName() + position;
        }
        return cause.getClass().getSimpleName();
    }

    /**
     * 把「方法参数校验失败」汇成一行<b>参数名</b>清单。
     *
     * <p>⚠️ 与 {@link #describeFieldErrors(List)} 同一原则：<b>只取参数名，不取用户填的值</b>。
     * 被拒绝的值可能正是口令码这类敏感输入，写进 message 就等于进了日志。
     *
     * <p>参数名可能为 {@code null}（编译时未保留参数名信息）。那时退化为通用描述——
     * 本项目开启了 {@code -parameters}，正常情况下拿得到名字。
     */
    private static String describeParameterErrors(HandlerMethodValidationException ex) {
        List<String> names = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = wireName(result.getMethodParameter());
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.isEmpty()
                ? ErrorCode.PARAM_INVALID.message()
                : "invalid parameters: " + String.join(", ", names);
    }

    /**
     * 取参数<b>在请求报文里</b>的名字（如 {@code page_size}），而不是 Java 形参名（{@code pageSize}）。
     *
     * <h2>⚠️ 为什么要多这一步</h2>
     * <p>{@code MethodParameter#getParameterName()} 给的是 <b>Java 形参名</b>。而契约里
     * 参数名是 <b>{@code snake_case}</b>（{@code 9-E}），二者在本项目里刻意不同
     * （{@code @RequestParam(name = "page_size") int pageSize}）。
     * <p>实测对比（真实端口，改了本方法之前）：
     * <pre>
     * GET .../intentions?page_size=abc   → {"message":"invalid parameter: page_size"}   ← Spring 自己的处理器
     * GET .../intentions?page_size=101   → {"message":"invalid parameters: pageSize"}   ← 本类，不一致
     * </pre>
     * 同一个类里两种叫法会让联调时多一次心算；而且 {@code message} 是给人 grep 的，
     * 用客户端看得见的名字才有用（{@code message} 仍不构成契约，见类注释）。
     *
     * @param parameter 校验失败的形参
     * @return 报文里的参数名；无 {@code @RequestParam} 声明名时退回 Java 形参名
     */
    private static String wireName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String declared = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
            if (!declared.isEmpty()) {
                return declared;
            }
        }
        return parameter.getParameterName();
    }

    /**
     * 把字段校验错误汇成一行「字段名」清单。
     *
     * <p>⚠️ <b>只取字段名，不取用户填的值</b>——被校验拒绝的值可能正是
     * 口令码、密码这类敏感输入，写进 message 就等于进了日志。
     */
    private static String describeFieldErrors(List<FieldError> errors) {
        if (errors.isEmpty()) {
            return ErrorCode.PARAM_INVALID.message();
        }
        StringBuilder sb = new StringBuilder("invalid fields: ");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(errors.get(i).getField());
        }
        return sb.toString();
    }
}
