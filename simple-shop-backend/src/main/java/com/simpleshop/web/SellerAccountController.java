package com.simpleshop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.service.SellerAuthService;
import com.simpleshop.web.dto.ChangePasswordRequest;

/**
 * 账号接口（{@code I11-03} 修改密码）。方案 §4.4。
 *
 * <table border="1">
 *   <caption>接口一览</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>鉴权</th><th>出参 {@code data}</th></tr>
 *   <tr><td>{@code I11-03}</td><td>{@code PUT /api/seller/account/password}</td><td>✓</td>
 *       <td>{@code null}</td></tr>
 * </table>
 *
 * <h2>⚠️ 路径是 {@code /api/seller/account/password}</h2>
 * <p><b>不是</b> {@code /api/seller/password}，也<b>不是</b> {@code /api/seller/seller/password}
 * （方案 §4.4 的「易错点」表专门列了这一条）。用 {@code PUT} 而不是 {@code POST}：
 * 契约如此，且改密是「替换已有凭证」的幂等语义。
 *
 * <h2>⚠️ 成功后的连锁效果：本次请求的 token 也会失效</h2>
 * <p>改密成功 → 该账号<b>全部</b>会话立即失效（{@code DEC-17}、{@code NFR-04}），
 * <b>包含</b>调用本次请求的那条 token。这是契约明确要求的（{@code I11-03} 行），
 * 前端据此跳 {@code P10-05}（重新登录）。
 * <p>副作用是：前端<b>不能</b>在改密成功后再用旧 token 调任何接口，
 * 否则会拿到 {@code 10002}／401。这一点须写进接口交接说明。
 *
 * <h2>⚠️ 与「手动冻结／解冻」同形但语义不同</h2>
 * <p>本接口是本轮唯一需要知道「当前账号是谁」的卖家接口——
 * 其余接口（{@code I11-04} ~ {@code I11-16}）操作的都是「当前商品」，
 * 与操作者身份无关（系统只有 1 个账号，{@code FR-01}、{@code O-10}）。
 */
@RestController
public class SellerAccountController {

    /** 修改密码的路径。方案 §4.4 的固定契约值，变更须同步接口交接说明与前端。 */
    public static final String PASSWORD_PATH = "/api/seller/account/password";

    private final SellerAuthService sellerAuthService;

    public SellerAccountController(SellerAuthService sellerAuthService) {
        this.sellerAuthService = sellerAuthService;
    }

    /**
     * {@code I11-03} 修改密码。
     *
     * <p>请求体 {@code {old_password, new_password}}；成功返回 {@code data = null}。
     *
     * @param body        改密请求体（{@code @Valid} 只拦「报文不合法」→ {@code 50002}；
     *                    新密码长度下限由 Service 判定为 {@code 10004}，理由见
     *                    {@code ChangePasswordRequest} 的类注释）
     * @param httpRequest 当前请求，用于取拦截器写入的已认证账号
     * @return 统一响应，{@code data} 为 {@code null}（键存在）
     * @throws com.simpleshop.service.exception.BusinessException
     *         {@code 10002}（未带/无效 token，由拦截器或 {@code requireAccount} 抛出）、
     *         {@code 10003}（原密码不正确）、{@code 10004}（新密码不足 8 位）
     */
    @PutMapping(PASSWORD_PATH)
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                           HttpServletRequest httpRequest) {
        // 账号来自拦截器校验 token 后写入的请求属性——接口层不自行解析 token，
        // 保证「鉴权只有一处实现」（见 SellerAuthService 的类注释）。
        String account = SellerAuthInterceptor.requireAccount(httpRequest);
        sellerAuthService.changePassword(account, body.getOldPassword(), body.getNewPassword());
        return ApiResponse.ok();
    }
}
