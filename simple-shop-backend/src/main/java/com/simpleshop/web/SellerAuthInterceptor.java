package com.simpleshop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;
import com.simpleshop.session.SessionStore;

/**
 * 卖家端鉴权拦截器：校验 {@code Authorization: Bearer <token>}（{@code 11.3.2}）。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>token 缺失 / 无效 / 已超时 → 抛 {@link ErrorCode#SESSION_INVALID}（{@code 10002}），
 *       由 {@code GlobalExceptionHandler} 输出 <b>HTTP 401</b> + 业务码 {@code 10002}，
 *       前端据此跳转登录页（{@code P10-05} 状态表现）。</li>
 *   <li>校验通过 → 把账号写入请求属性 {@link #ATTR_ACCOUNT}，供 Service 使用
 *       （例如 {@code I11-03} 改密需要知道是哪个账号）；
 *       并<b>滑动刷新</b>会话的 {@code lastAccessAt}（{@code SessionStore.validate} 内部完成）。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么在拦截器抛异常而不是直接写响应</h2>
 * <p>直接 {@code response.setStatus(401)} + 手写 JSON 会绕过 {@link ApiResponse} 的统一结构，
 * 导致鉴权失败的响应体形状与其它错误不一致——前端就得为「401 特例」多写一套解析。
 * 抛 {@link BusinessException} 让 {@code GlobalExceptionHandler} 统一加工，
 * 保证<b>所有</b>错误响应都是 {@code {code, message, data}} 的形状。
 *
 * <h2>⚠️ 不做什么</h2>
 * <ul>
 *   <li><b>不</b>用 Spring Security（见方案 §5：本项目只有一种凭证、一个账号，
 *       引入 Security 会带来过滤器链、CSRF、默认登录页等需要额外解释与关闭的配置，收益为零）。</li>
 *   <li><b>不</b>使用 {@code HttpSession}/{@code JSESSIONID}——契约要求前端用
 *       {@code Authorization: Bearer}（{@code 11.3.2}），用容器会话会引入 cookie 语义与 CSRF 面
 *       （方案 §5.3 第 5 条）。</li>
 *   <li><b>不</b>拦截买家端端点——买家端<b>不需要鉴权</b>（凭证是口令码，
 *       {@code O-01}、{@code BR-32}）。{@code WebMvcConfig} 只对 {@code /api/seller/**} 注册本拦截器。</li>
 * </ul>
 *
 * <h2>⚠️ 日志边界</h2>
 * <p>鉴权失败<b>不记录</b> token 内容（即使是无效 token）——它可能是用户真实凭证的笔误，
 * 写进日志就构成了泄漏面。只记录「哪个 URI、哪个方法、缺不缺失」。
 */
@Component
public class SellerAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SellerAuthInterceptor.class);

    /** 请求属性名：校验通过后的账号，供 Controller/Service 读取。 */
    public static final String ATTR_ACCOUNT = "com.simpleshop.session.account";

    /** Authorization 头的标准前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionStore sessionStore;

    public SellerAuthInterceptor(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractToken(request);
        if (token == null) {
            // 不记录 header 内容（见类注释的日志边界）
            log.warn("seller api called without bearer token: method={} path={}",
                    request.getMethod(), request.getRequestURI());
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        // validate 内部完成「过期判定 + 原子滑动刷新」；返回空即无效/过期
        return sessionStore.validate(token)
                .map(session -> {
                    request.setAttribute(ATTR_ACCOUNT, session.account());
                    return true;
                })
                .orElseThrow(() -> {
                    log.warn("seller api called with invalid or expired token: method={} path={}",
                            request.getMethod(), request.getRequestURI());
                    return new BusinessException(ErrorCode.SESSION_INVALID);
                });
    }

    /**
     * 取当前请求的<b>已认证账号</b>，取不到即视为会话失效（<b>fail closed</b>）。
     *
     * <p>供需要知道「是谁在操作」的 Controller 使用（目前是 {@code I11-03} 改密；
     * {@code I11-05}~{@code I11-14} 不需要——单账号系统，操作者恒为同一人）。
     *
     * <h2>⚠️ 为什么是「抛异常」而不是「返回 null」</h2>
     * <p>返回 {@code null} 会让调用方有两种走法：要么自己判空（写漏了就把 {@code null}
     * 当成账号用下去，最终变成一条 {@code where account = null} 的查询和一个
     * 语焉不详的「账号不存在」），要么用 {@code Objects.requireNonNull}（变成 500）。
     * 两者都不是想要的。<b>取不到账号只可能是「会话无效」</b>，
     * 那就直接给出契约里那个码（{@code 10002}，HTTP 401），让前端跳登录页。
     *
     * <h2>本方法与 {@code OperationLogService.currentAccount()} 的分工</h2>
     * <p>两者读的是<b>同一个</b>请求属性（{@link #ATTR_ACCOUNT}），但失败策略<b>刻意相反</b>：
     * <ul>
     *   <li>本方法：<b>fail closed</b> → 抛 {@code 10002}。用于「账号是业务输入」的场景，
     *       绝不能拿 {@code null} 当账号用。</li>
     *   <li>{@code OperationLogService}：<b>降级</b> → 记 {@code operator=unknown}。
     *       日志是旁路，不该因为拿不到上下文而让业务失败。</li>
     * </ul>
     *
     * <p><b>⚠️ 实际不可达</b>：本拦截器覆盖整个 {@code /api/seller/**}，能走到 Controller
     * 就说明属性已被写入。它是<b>防御性断言</b>——保证「万一属性没写」时是
     * 「明确拒绝」而不是「静默按 null 账号执行」。
     *
     * @param request 当前请求
     * @return 已认证账号
     * @throws BusinessException {@code 10002}（HTTP 401）——请求属性中没有账号
     */
    public static String requireAccount(HttpServletRequest request) {
        Object account = request.getAttribute(ATTR_ACCOUNT);
        if (account == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        return account.toString();
    }

    /**
     * 从 {@code Authorization} 头解析 Bearer token。
     *
     * <p>前缀比较<b>忽略大小写</b>（{@code bearer}/{@code Bearer} 都接受）——
     * RFC 7235 规定 scheme 大小写不敏感，宽松接受不会带来风险，却能避免一类无谓的前端联调问题。
     *
     * @return 解析出的 token；头缺失、格式不符或 token 为空白时返回 {@code null}
     */
    static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.length() <= BEARER_PREFIX.length()) {
            return null;
        }
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
