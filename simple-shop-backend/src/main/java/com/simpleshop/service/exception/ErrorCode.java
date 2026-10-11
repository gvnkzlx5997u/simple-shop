package com.simpleshop.service.exception;

import org.springframework.http.HttpStatus;

/**
 * 卖家端接口的业务错误码全表（第 11 章 §11.3.4、§11.6.1）。
 *
 * <h2>⚠️ 本枚举是错误码的【唯一】来源，实现时逐条对照、不得增删编号</h2>
 * <p>编号不是自创的：{@code 0} 与 {@code 10001} ~ {@code 50002} 均逐条取自
 * 《需求规格说明书》第 11 章 §11.6.1 的「卖家端错误码全表」。新增错误码<b>必须</b>落在
 * 所属域内（{@code 1xxxx} 认证、{@code 2xxxx} 商品、{@code 3xxxx} 意向与交易、
 * {@code 4xxxx} 口令码、{@code 5xxxx} 通用），<b>不得跨域占用</b>。
 * 特别注意：{@code 20011}（历史商品不存在）虽语义上像「资源不存在」，
 * 但§11.6.1 明确它<b>归商品域</b>，<b>不得</b>改用 {@code 4xxxx}。
 *
 * <h2>HTTP 状态码的口径（§11.3.4，注意与常规 REST 直觉相反）</h2>
 * <p><b>业务规则拒绝一律返回 200 + 业务码</b>——「队列非空」「商品非在售」这类结果是
 * <b>业务语义的正常分支</b>，不是传输或协议错误；用 200 + 业务码可使前端统一在
 * {@code code} 分支处理，避免与网络异常混淆。
 * <p>因此本枚举的 {@link #httpStatus()} <b>默认为 200</b>，只有 4 个例外显式声明：
 * <ul>
 *   <li>{@link #SESSION_INVALID} → 401（未携带/无效/超时的 token）</li>
 *   <li>{@link #PARAM_INVALID} → 400（请求报文本身不合法）</li>
 *   <li>{@link #HISTORY_GOODS_NOT_FOUND} → 404</li>
 *   <li>{@link #INTERNAL_ERROR} → 500</li>
 * </ul>
 *
 * <h2>{@link #message()} 的用法（⚠️ 容易用错）</h2>
 * <p>§11.3.3 明确：{@code message} 「<b>仅供日志与排查</b>，<b>前端不得直接展示</b>」——
 * 界面文案由前端按 {@code code} 映射第 10 章《提示文案表》的 {@code M10-xx}（{@code UX-07}）。
 * <p>因此本枚举的 {@code message} <b>一律使用 ASCII 英文</b>（已定 Q-13）：它既然是给开发/运维
 * 看的日志字段，英文更适合 grep 与告警规则；也避免与「前端必须按 code 映射文案」这条硬规则
 * 产生「那我能不能直接显示 message」的歧义。
 * <p>另注：{@link #docs()} 里记着对应的 {@code M10-xx} 编号，<b>只作注释用途</b>，
 * 不参与序列化、不下发给前端（前端自己持有 {@code code → M10-xx} 的映射）。
 */
public enum ErrorCode {

    // -------------------------------------------------------------------------
    // 成功
    // -------------------------------------------------------------------------

    /** 成功。 */
    OK(0, "ok", HttpStatus.OK, "—"),

    // -------------------------------------------------------------------------
    // 1xxxx 认证与会话（P10-05、P10-10）
    // -------------------------------------------------------------------------

    /** 账号或密码错误。<b>统一提示，不区分「账号不存在」与「密码错误」</b>（降低枚举面）。 */
    LOGIN_FAILED(10001, "invalid account or password", HttpStatus.OK, "M10-23"),

    /** 会话失效／未登录／超时。前端跳转登录页。 */
    SESSION_INVALID(10002, "session invalid or expired", HttpStatus.UNAUTHORIZED, "M10-22"),

    /** 原密码不正确。 */
    OLD_PASSWORD_MISMATCH(10003, "old password incorrect", HttpStatus.OK, "M10-34"),

    /** 新密码长度不足 8 位（{@code C-17}）。 */
    NEW_PASSWORD_TOO_SHORT(10004, "new password must be at least 8 characters", HttpStatus.OK, "就地提示"),

    // -------------------------------------------------------------------------
    // 2xxxx 商品（P10-06 ~ P10-09）
    // -------------------------------------------------------------------------

    /** 已存在在售／冻结商品（{@code BR-11}、{@code INV-01}）。 */
    GOODS_ALREADY_EXISTS(20001, "a product is already on sale or frozen", HttpStatus.OK, "M10-24"),

    /** 当前无商品（空态处理）。 */
    NO_CURRENT_GOODS(20002, "no current product", HttpStatus.OK, "空态"),

    /** 商品非在售。 */
    GOODS_NOT_ON_SALE(20003, "product is not on sale", HttpStatus.OK, "按钮不可用"),

    /** 商品非已冻结。 */
    GOODS_NOT_FROZEN(20004, "product is not frozen", HttpStatus.OK, "按钮不可用"),

    /** <b>交易冻结不可解冻</b>（{@code PS-06}、{@code BR-07}）——{@code FIX-01} 的服务端兜底防线。 */
    GOODS_FROZEN_BY_TRADE(20005, "product is frozen by trade; unfreeze is not allowed", HttpStatus.OK, "M10-15"),

    /** <b>队列非空不可下架</b>（{@code BR-09}、{@code DEC-01}）。 */
    QUEUE_NOT_EMPTY(20006, "intention queue is not empty", HttpStatus.OK, "M10-16"),

    /** 名称校验失败（≤50 字符、拒绝 {@code <}/{@code >}、禁首尾空格、不允许换行）。 */
    GOODS_NAME_INVALID(20007, "product name is invalid", HttpStatus.OK, "M10-29"),

    /** 描述校验失败（≤500 字符、拒绝 {@code <}/{@code >}、允许换行）。 */
    GOODS_DESCRIPTION_INVALID(20008, "product description is invalid", HttpStatus.OK, "M10-30"),

    /** 图片格式／大小不合规（JPG/PNG、≤5MB），或 {@code pic_url} 非法。 */
    IMAGE_INVALID(20009, "image must be JPG or PNG and not larger than 5MB", HttpStatus.OK, "M10-31"),

    /** 价格越界（{@code 0 < price ≤ 999999.99}，两位小数）。 */
    PRICE_OUT_OF_RANGE(20010, "price must be greater than 0 and not greater than 999999.99", HttpStatus.OK, "M10-32"),

    /** 历史商品不存在。⚠️ <b>归商品域</b>（{@code 4xxxx} 为口令码域，不得越域占用），HTTP 404。 */
    HISTORY_GOODS_NOT_FOUND(20011, "history product not found", HttpStatus.NOT_FOUND, "错误态"),

    // -------------------------------------------------------------------------
    // 3xxxx 意向与交易（P10-07）
    // -------------------------------------------------------------------------

    /** <b>目标意向非队首</b>（{@code BR-12} 先到先得）——服务端防线，不得仅依赖页面禁用。 */
    NOT_QUEUE_HEAD(30002, "target intention is not the queue head", HttpStatus.OK, "拒绝提示"),

    /** 目标意向状态不符（非 {@code queued}／非 {@code trading}）。 */
    INTENTION_STATUS_MISMATCH(30003, "target intention status mismatch", HttpStatus.OK, "拒绝提示"),

    /** 同一商品已存在 {@code trading} 意向（{@code INV-02}）。 */
    TRADING_ALREADY_EXISTS(30004, "another intention is already trading", HttpStatus.OK, "拒绝提示"),

    /** 归档前置校验失败：仍有非终态意向（{@code INV-05}）。<b>数据库侧无任何兜底</b>。 */
    ARCHIVE_PRECONDITION_FAILED(30005, "cannot archive: some intentions are still active", HttpStatus.OK, "服务端错误提示"),

    /** {@code disposal} 取值非法（非 {@code voided}／{@code requeued}）。由 Service 显式抛出（Q-14）。 */
    INVALID_DISPOSAL(30006, "disposal must be voided or requeued", HttpStatus.OK, "就地提示"),

    /** 失败备注超长（>300 字符）。 */
    FAIL_REASON_TOO_LONG(30007, "fail reason must not exceed 300 characters", HttpStatus.OK, "就地提示"),

    /** 意向不存在。 */
    INTENTION_NOT_FOUND(30008, "intention not found", HttpStatus.OK, "拒绝提示"),

    // -------------------------------------------------------------------------
    // 4xxxx 口令码（P10-04、P10-07）
    // -------------------------------------------------------------------------

    /** 口令码已失效（意向处于终态）。**不展示**，且不得说明原因、不得返回买家信息（{@code BR-29}）。 */
    PASSCODE_EXPIRED(40002, "passcode is no longer valid", HttpStatus.OK, "不展示"),

    // -------------------------------------------------------------------------
    // 5xxxx 通用请求
    // -------------------------------------------------------------------------

    /** 请求过于频繁（限流）。 */
    TOO_MANY_REQUESTS(50001, "too many requests", HttpStatus.OK, "就地提示"),

    /** 参数缺失或格式错误。报文本身不合法，HTTP 400。 */
    PARAM_INVALID(50002, "missing or malformed parameter", HttpStatus.BAD_REQUEST, "就地提示"),

    /** 服务端内部错误。HTTP 500，且<b>不携带业务码语义</b>（契约：500 不返回业务结构）。 */
    INTERNAL_ERROR(50000, "internal server error", HttpStatus.INTERNAL_SERVER_ERROR, "通用错误提示");

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
    private final String docs;

    ErrorCode(int code, String message, HttpStatus httpStatus, String docs) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.docs = docs;
    }

    /** 业务码（下发给前端的 {@code code}）。 */
    public int code() {
        return code;
    }

    /** 服务端调试用简短描述（ASCII 英文）。<b>前端不得直接展示</b>（{@code 11.3.3}）。 */
    public String message() {
        return message;
    }

    /** 该错误码对应的 HTTP 状态码。<b>默认 200</b>，仅 4 个例外（见类注释）。 */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * 对应的第 10 章提示文案编号（{@code M10-xx}）或处置方式。
     *
     * <p><b>仅作注释/排查用途</b>：不参与 JSON 序列化、不下发前端——
     * 前端自己持有 {@code code → M10-xx} 的映射（{@code UX-07}、{@code NFR-09}）。
     */
    public String docs() {
        return docs;
    }
}
