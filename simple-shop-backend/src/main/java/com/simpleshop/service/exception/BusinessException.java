package com.simpleshop.service.exception;

/**
 * 业务规则拒绝异常。
 *
 * <h2>用途</h2>
 * <p>Service 层在「前置条件不满足」时抛出本异常，由 {@code GlobalExceptionHandler} 翻译为
 * 契约约定的 HTTP 状态码 + 业务 {@code code}（见 {@link ErrorCode}）。
 *
 * <h2>为什么是 {@link RuntimeException}</h2>
 * <p>① Spring 的 {@code @Transactional} <b>默认只对运行时异常回滚</b>，受检异常需要额外声明
 * {@code rollbackFor}，容易漏；② 业务拒绝在 Service 签名上逐个 {@code throws} 会污染所有调用方，
 * 而这些拒绝本质是「正常业务分支」，不是需要调用方补救的异常。
 *
 * <h2>⚠️ 三条使用约定</h2>
 * <ol>
 *   <li><b>{@code message} 只用英文、且只写「为什么被拒」</b>——它是日志字段，
 *       <b>前端不得直接展示</b>（{@code 11.3.3}），界面文案由前端按 {@code code} 映射
 *       {@code M10-xx}（{@code UX-07}）。<b>不要</b>把变量值（如某个商品名）拼进 message：
 *       它们会进日志，而日志有泄漏面（口径见 {@code NFR-12}）。</li>
 *   <li><b>业务拒绝默认 HTTP 200</b>（{@code 11.3.4}、已决 {@code 11-B}）——由
 *       {@link ErrorCode#httpStatus()} 决定，<b>不需要</b>在本异常上再指定状态码。</li>
 *   <li><b>不要用本异常表达「参数格式错误」之外的程序缺陷</b>：真正未预料的异常应当原样抛给
 *       容器（最终映射为 {@code 50000}），否则会把缺陷伪装成「业务拒绝」，排查时误导。</li>
 * </ol>
 *
 * <h2>关于 {@link #fillInStackTrace()} 的取舍（本类刻意不重写）</h2>
 * <p>理论上业务拒绝属于「可预期分支」，不需要栈帧，重写 {@code fillInStackTrace()} 返回
 * {@code this} 可以省掉一次栈采集。但本项目<b>不这么做</b>：
 * Service 层的业务拒绝往往需要排查「是哪个调用路径触发的」（例如 {@code 20006} 队列非空
 * 究竟来自手动下架还是标记成功），<b>保留栈帧对排查的价值大于那点开销</b>。
 * 本系统是单卖家小流量场景，性能不是瓶颈。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * 以标准错误码构造，{@code message} 取 {@link ErrorCode#message()}。
     *
     * <p>绝大多数场景用这个构造器即可，保证日志措辞与错误码表一致。
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 以标准错误码构造，但覆盖默认描述。
     *
     * <p>仅用于「同一个错误码需要补充上下文」的情形，例如
     * {@link ErrorCode#PARAM_INVALID} 需要指出是哪个字段。⚠️ 补充内容<b>不得包含</b>
     * 口令码、密码或其它敏感值（见类注释第 1 条）。
     */
    public BusinessException(ErrorCode errorCode, String messageOverride) {
        super(messageOverride);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
