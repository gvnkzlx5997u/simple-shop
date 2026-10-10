package com.simpleshop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.service.SellerAuthService;
import com.simpleshop.service.dto.LoginData;
import com.simpleshop.web.dto.LoginRequest;

/**
 * 会话接口（{@code I11-01} 登录、{@code I11-02} 退出登录）。方案 §4.4。
 *
 * <h2>两个接口共用同一路径，靠 HTTP 方法区分</h2>
 * <table border="1">
 *   <caption>接口一览</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>鉴权</th><th>出参 {@code data}</th></tr>
 *   <tr><td>{@code I11-01}</td><td>{@code POST /api/seller/session}</td><td>✗</td>
 *       <td>{@code {token, expire_at}}</td></tr>
 *   <tr><td>{@code I11-02}</td><td>{@code DELETE /api/seller/session}</td><td>✓</td>
 *       <td>{@code null}</td></tr>
 * </table>
 *
 * <h2>⚠️ 路径常量直接引用 {@link WebMvcConfig#SESSION_PATH}</h2>
 * <p>不写字面量。原因见该常量的注释：本路径是 {@code WebMvcConfig} 里
 * {@code excludePathPatterns} 的<b>同一个值</b>，两边必须一致，
 * 否则登录接口会被鉴权拦截器拒掉，形成「登录需要会话、会话需要登录」的死锁。
 * 用共享常量把「两边不一致」变成编译期不可能。
 *
 * <h2>⚠️ 本类不含任何业务判断</h2>
 * <p>Controller 的职责只有三件：绑定请求、调用 Service、包成 {@link ApiResponse}。
 * 凭证校验在 Service（{@code SellerAuthService.login}），鉴权在拦截器。
 * 这里<b>不得</b>出现 {@code if (account.equals(...))} 之类的判断——
 * 一旦出现，就说明业务规则漏到了接口层，买家端 JSP 复用 Service 时会绕过去。
 *
 * <h2>⚠️ 登录接口不做限流与限次</h2>
 * <p>{@code DEC-25}（无限次限制）、{@code DEC-26}（无验证码）、{@code NFR-03}
 * （失败 100 次后仍可尝试）。这是<b>明确决策</b>，不是遗漏；
 * 若将来要加，必须走变更记录并同步复核 {@code NFR-03}。
 */
@RestController
@RequestMapping(WebMvcConfig.SESSION_PATH)
public class SellerSessionController {

    private final SellerAuthService sellerAuthService;

    public SellerSessionController(SellerAuthService sellerAuthService) {
        this.sellerAuthService = sellerAuthService;
    }

    /**
     * {@code I11-01} 登录。
     *
     * <p>请求体 {@code {account, password}}；成功返回 {@code {token, expire_at}}。
     * 失败一律 {@code 10001}（HTTP <b>200</b>）——账号不存在与口令错误<b>不可区分</b>（{@code M10-23}）。
     *
     * <p>本方法位于 {@code /api/seller/**} 之下但被拦截器<b>排除</b>，因此无需 token——
     * 这正是「登录」的定义。
     *
     * <p>注解 {@link Valid} 只做「报文是否合法」（缺字段 → {@code 50002} + HTTP 400），
     * 业务校验在 Service（方案 §3.6）。
     *
     * @param request 登录请求体
     * @return 统一响应，{@code data} 为会话令牌与过期时刻
     */
    @PostMapping
    public ApiResponse<LoginData> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(sellerAuthService.login(request.getAccount(), request.getPassword()));
    }

    /**
     * {@code I11-02} 退出登录。<b>幂等</b>：无论 token 有效与否都返回 {@code code = 0}。
     *
     * <h2>⚠️ 本方法必须自己解析 token</h2>
     * <p>它与 {@code I11-01} 共用路径，而拦截器按<b>路径</b>排除（{@code excludePathPatterns}
     * 无法区分方法），所以退出登录<b>不会</b>经过 {@code SellerAuthInterceptor}，
     * 请求属性里<b>没有</b>账号。因此这里直接用
     * {@link SellerAuthInterceptor#extractToken(HttpServletRequest)} 取原始 token，
     * 并<b>不能</b>调用 {@link SellerAuthInterceptor#requireAccount(HttpServletRequest)}
     * （那会抛 {@code 10002}／401，与「幂等退出」的口径冲突——见 {@code WebMvcConfig} 的说明）。
     *
     * <p>token 缺失时传 {@code null} 给 Service，{@code SessionStore.remove} 对 {@code null}
     * 是无操作，结果同样是成功。
     *
     * @param request 当前请求（仅用于取 {@code Authorization} 头）
     * @return 统一响应，{@code data} 为 {@code null}（键存在）
     */
    @DeleteMapping
    public ApiResponse<Void> logout(HttpServletRequest request) {
        sellerAuthService.logout(SellerAuthInterceptor.extractToken(request));
        return ApiResponse.ok();
    }
}
