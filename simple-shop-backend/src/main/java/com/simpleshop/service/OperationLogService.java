package com.simpleshop.service;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.simpleshop.web.SellerAuthInterceptor;

/**
 * 关键操作日志（{@code G6-01}、{@code NFR-12}、{@code DEC-14}）。
 *
 * <h2>必须记录的 8 类操作（{@code NFR-12} 原文，逐条对应）</h2>
 * <ol>
 *   <li>冻结商品</li>
 *   <li>解冻商品</li>
 *   <li>手动下架</li>
 *   <li>进入交易</li>
 *   <li>标记交易结果</li>
 *   <li>作废／重新排队</li>
 *   <li><b>查看意向口令码</b>（✅ 已定 Q-4/Q-9：<b>应当记录</b>）</li>
 *   <li>修改密码</li>
 * </ol>
 *
 * <h2>每条记录的内容（{@code NFR-12} 可测指标）</h2>
 * <p>操作时间（<b>UTC</b>）、操作类型、目标对象。时间取自日志框架的
 * UTC 时间戳（见 {@code logback-spring.xml} 的 {@code %d{..., UTC}}），
 * <b>不用</b> {@code LocalDateTime.now()}（会取本机时区，与数据层的 UTC 口径冲突）。
 *
 * <h2>⚠️ 三条硬约束</h2>
 * <ol>
 *   <li><b>不记录口令码明文</b>（{@code 12-H}、已定 Q-4/Q-9）：{@code VIEW_PASSCODE} 只记
 *       「谁在何时查看了<b>哪条意向</b>」的 {@code intentionId}，<b>绝不</b>记 token 值。
 *       {@code Intention.toString()} 已刻意省略 token，但本类<b>仍不</b>接受 token 参数——
 *       从签名上杜绝误传。</li>
 *   <li><b>不记录密码</b>（明文或哈希）：{@code CHANGE_PASSWORD} 只记账号。</li>
 *   <li><b>不纳入数据库表结构</b>（{@code 9-B}、{@code 12-G}）：由<b>应用日志</b>实现，
 *       独立 logger 名 + 独立文件 appender，保留 <b>3 个月</b>（{@code C-18}、{@code 12-P7}）。</li>
 * </ol>
 *
 * <h2>⚠️ 日志格式</h2>
 * <p>用 {@code key=value} 的紧致格式（而非中文长句）：
 * 便于 {@code grep}、便于将来接日志平台解析，也避免中文在日志文件编码不一致时变成乱码。
 *
 * <h2>关于失败不影响业务</h2>
 * <p>{@link #log} <b>不抛异常</b>、不参与事务：审计日志写入失败（如磁盘满）
 * <b>不应</b>导致业务操作回滚——否则会因为日志组件把一个正常的交易操作变成 500。
 * 日志框架自身在写盘失败时会输出到 status 通道，不向上冒泡。
 */
@Service
public class OperationLogService {

    /**
     * 审计专用 logger 名。{@code logback-spring.xml} 为它单独配置了文件 appender，
     * <b>不要改动本常量</b>（改了会导致审计日志落回普通控制台 appender，3 个月留存配置随之失效）。
     */
    private static final Logger AUDIT = LoggerFactory.getLogger("com.simpleshop.audit");

    // -------------------------------------------------------------------------
    // 操作类型常量（= NFR-12 的 8 类）
    // -------------------------------------------------------------------------

    public static final String FREEZE_GOODS = "FREEZE_GOODS";
    public static final String UNFREEZE_GOODS = "UNFREEZE_GOODS";
    public static final String OFFLINE_GOODS = "OFFLINE_GOODS";

    /**
     * 发布商品。
     *
     * <p>⚠️ <b>不在</b> {@code NFR-12} 的 8 类必列清单里，是<b>额外</b>记录的
     * （方案 §3.3.2 步骤 6 的明确要求）。{@code G6-01} 要求「关键操作」都要留痕，
     * 而发布是整个商品生命周期的起点——出问题时「谁在什么时候发布了这条」是第一问。
     * <p>相应地，「8 类必列操作各有记录」的验收口径以 {@code NFR-12} 的 8 类为准，
     * <b>不是</b>「日志里恰好只有 8 类」；登录／退出则确实<b>不</b>记录
     * （它们是会话生命周期事件，不是业务状态变更）。
     */
    public static final String PUBLISH_GOODS = "PUBLISH_GOODS";

    public static final String ENTER_TRADE = "ENTER_TRADE";
    public static final String MARK_TRADE_SUCCESS = "MARK_TRADE_SUCCESS";
    public static final String MARK_TRADE_FAILURE = "MARK_TRADE_FAILURE";
    /** 「作废／重新排队」的裁决结果单独作为操作类型，便于按裁决方式统计。 */
    public static final String DISPOSAL_VOIDED = "DISPOSAL_VOIDED";
    public static final String DISPOSAL_REQUEUED = "DISPOSAL_REQUEUED";
    public static final String VIEW_PASSCODE = "VIEW_PASSCODE";
    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";

    /** 目标对象类型。 */
    public static final String TARGET_GOODS = "GOODS";
    public static final String TARGET_INTENTION = "INTENTION";
    public static final String TARGET_ACCOUNT = "ACCOUNT";

    /**
     * 记录一条关键操作日志。
     *
     * @param operation  操作类型，用本类的 {@code *_GOODS} / {@code MARK_*} 等常量
     * @param targetType 目标对象类型，用 {@code TARGET_*} 常量
     * @param targetId   目标对象 ID；<b>⚠️ 对于「查看口令码」，传意向 ID 而非口令码</b>
     */
    public void log(String operation, String targetType, String targetId) {
        AUDIT.info("operation={} targetType={} targetId={} operator={}",
                operation, targetType, targetId, currentAccount());
    }

    /**
     * 带补充说明的记录（例如「标记失败」附带裁决方式）。
     *
     * <p>⚠️ {@code detail} <b>不得</b>包含口令码、密码或完整请求体；
     * 若需记录失败备注，只记长度或是否有值，不记内容（备注虽买家不可见，但仍是用户输入）。
     */
    public void log(String operation, String targetType, String targetId, String detail) {
        AUDIT.info("operation={} targetType={} targetId={} operator={} detail={}",
                operation, targetType, targetId, currentAccount(), detail);
    }

    /**
     * 取当前请求的已登录账号。
     *
     * <p>从请求属性读取（由 {@code SellerAuthInterceptor} 在校验通过后写入）。
     * 取不到时返回 {@code "unknown"} 而<b>不抛异常</b>——日志不该因为拿不到上下文而失败。
     *
     * <p>⚠️ 若将来有<b>非 HTTP 触发</b>的关键操作（定时任务等），本方法会返回 {@code unknown}；
     * 那时应改为显式传入账号，而不是让日志悄悄丢掉操作者。
     */
    private String currentAccount() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                Object account = request.getAttribute(SellerAuthInterceptor.ATTR_ACCOUNT);
                if (account != null) {
                    return account.toString();
                }
            }
        } catch (RuntimeException ignored) {
            // 脱离请求上下文（如单元测试直接调 Service）时不应影响业务，降级为 unknown
        }
        return "unknown";
    }
}
