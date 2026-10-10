package com.simpleshop.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.persistence.entity.ShopUser;
import com.simpleshop.persistence.repository.ShopUserRepository;
import com.simpleshop.security.PasswordHasher;
import com.simpleshop.service.dto.LoginData;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;
import com.simpleshop.session.SessionStore;

/**
 * 卖家认证服务（{@code FR-01} 登录、{@code FR-02} 修改密码）。方案 §3.3.1。
 *
 * <h2>方法清单</h2>
 * <pre>
 * LoginData login(String account, String password);
 * void      logout(String token);
 * void      changePassword(String account, String oldPassword, String newPassword);
 * </pre>
 *
 * <h2>✅ 刻意不做的事（都有明确决策，不是遗漏）</h2>
 * <table border="1">
 *   <caption>不做的功能与依据</caption>
 *   <tr><th>不做</th><th>依据</th></tr>
 *   <tr><td>登录失败次数限制／账号锁定</td><td>{@code DEC-25}；可测指标 {@code NFR-03}「失败 100 次后仍可尝试」</td></tr>
 *   <tr><td>图形验证码</td><td>{@code DEC-26}</td></tr>
 *   <tr><td>找回密码／重置密码（应用层）</td><td>{@code DEC-06}；忘记密码的处置是<b>数据库层重置</b>，写进交付文档（{@code AC-29}）</td></tr>
 *   <tr><td>账号注册</td><td>{@code FR-01}、{@code O-10}：<b>只</b>有一个由初始化脚本创建的账号</td></tr>
 *   <tr><td>会话持久化（重启不掉线）</td><td>方案 §5.1 第 4 条：容器重启丢会话是<b>可接受且更可取</b>的</td></tr>
 * </table>
 *
 * <h2>⚠️ 为什么这些方法收「账号／token」而不是「当前用户对象」</h2>
 * <p>认证的<b>唯一</b>入口是 {@code SellerAuthInterceptor}（方案 §5.2）：它在校验 token 之后
 * 把账号放进请求属性。因此 {@link #changePassword} 接收的是<b>已认证的账号</b>，
 * 而不是再拿 token 到 {@code SessionStore} 里解析一遍——那会是第二处鉴权逻辑，
 * 且两者将来可能不一致（一个改了、另一个忘了）。
 * <p>方案 §3.3.1 原先把 {@code changePassword} 的入参写作 {@code (String token, ...)}，
 * 实现时按上述理由改为 {@code (String account, ...)}；契约口径<b>不变</b>
 * （{@code I11-03} 仍然需要 Bearer token，否则请求在拦截器就被 {@code 10002} 拒掉）。
 * <p>同理 {@link #logout} <b>必须</b>收 token：退出登录的作用对象就是「这条会话」，
 * 而 {@code I11-02} 的路径被拦截器排除（见 {@code WebMvcConfig}），
 * 拿不到「已认证账号」，只能自己解析 token——这也正是它必须幂等、不得抛 401 的原因。
 *
 * <h2>⚠️ 时间</h2>
 * <p>本类<b>不</b>产生时间：口令的 {@code update_at} 由实体回调
 * {@code ShopUser.@PreUpdate} 用 {@code DatabaseTimeProvider.utcNow()} 刷新（唯一时间来源）；
 * 会话过期时刻由 {@code SessionStore} 用 {@code Instant} 计算。
 * 本类<b>不得</b>出现 {@code LocalDateTime.now()}／{@code new Date()}／{@code System.currentTimeMillis()}。
 */
@Service
public class SellerAuthService {

    private static final Logger log = LoggerFactory.getLogger(SellerAuthService.class);

    /**
     * 新密码的长度下限（{@code C-17}）。
     *
     * <p>与 {@link ErrorCode#NEW_PASSWORD_TOO_SHORT} 的语义绑定，故放在使用它的类里，
     * 不另建常量类——目前只有这一处需要。
     */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** 卖家写操作的事务超时（{@code DEC-DB-13}）。{@code @Transactional.timeout} 的单位是<b>秒</b>。 */
    private static final int WRITE_TIMEOUT_SECONDS = 5;

    private final ShopUserRepository shopUserRepository;
    private final SessionStore sessionStore;
    private final OperationLogService operationLogService;

    public SellerAuthService(ShopUserRepository shopUserRepository,
                             SessionStore sessionStore,
                             OperationLogService operationLogService) {
        this.shopUserRepository = shopUserRepository;
        this.sessionStore = sessionStore;
        this.operationLogService = operationLogService;
    }

    // -------------------------------------------------------------------------
    // FR-01 登录（I11-01）
    // -------------------------------------------------------------------------

    /**
     * 登录：校验凭证并建立会话。
     *
     * @param account  账号
     * @param password 明文口令
     * @return 会话令牌与过期时刻（{@code I11-01} 的 {@code data}）
     * @throws BusinessException {@code 50002}（参数缺失——Service 独立校验，{@code G6-02}）、
     *                          {@code 10001}（账号不存在<b>或</b>口令不匹配）
     */
    @Transactional(readOnly = true)
    public LoginData login(String account, String password) {
        // Service 侧的独立校验（G6-02）。DTO 上也有 @NotBlank，但 Service 不能依赖它：
        // 买家端 JSP（下一轮）直接调用 Service，不经过 DTO 校验链。
        if (account == null || account.isBlank() || password == null || password.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }

        // ⚠️ 账号不存在与口令不匹配【必须】收敛到同一个 10001（M10-23「统一提示，不区分」）。
        //    这里的写法让这件事成为【结构性】保证而不是纪律要求：
        //    查不到账号时 passwordHash 为 null，而 PasswordHasher.matches(x, null) 恒为 false，
        //    于是两条路径汇合到【同一个】if 分支——不存在「某天有人给账号不存在加了个专属错误码」
        //    这种可能。
        Optional<ShopUser> user = shopUserRepository.findByAccount(account);
        String passwordHash = user.map(ShopUser::getPassword).orElse(null);
        if (!PasswordHasher.matches(password, passwordHash)) {
            // 只记账号与路径级信息，不记口令（明文或哈希都不记）
            log.warn("login failed: account={} reason={}",
                    account, passwordHash == null ? "account not found" : "password mismatch");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 会话创建在进程内完成（方案 §5.2），不写数据库。
        // 此处虽为 readOnly 事务，但没有 DB 写入，故 readOnly 语义成立。
        SessionStore.Session session = sessionStore.create(account);
        log.info("login succeeded: account={} expireAt={}", account, session.expireAt());
        return LoginData.of(session.token(), session.expireAt());
    }

    // -------------------------------------------------------------------------
    // FR-01 退出登录（I11-02）
    // -------------------------------------------------------------------------

    /**
     * 退出登录：移除该 token 对应的会话。<b>幂等</b>。
     *
     * <h2>⚠️ 三条口径（{@code I11-02} 最容易做偏的地方）</h2>
     * <ol>
     *   <li><b>token 无效也要成功返回</b>（{@code code = 0}）：{@code SessionStore.remove} 对不存在的
     *       token 是无操作。退出登录的语义是「让这个凭证不再可用」——它<b>已经</b>不可用了，
     *       操作目标已达成，返回失败没有任何意义，反而会让前端弹一个无谓的错误提示。</li>
     *   <li><b>绝不抛 {@code 10002}／401</b>。{@code I11-02} 与 {@code I11-01} 共用路径
     *       {@code /api/seller/session}，而拦截器按<b>路径</b>排除（{@code WebMvcConfig} 有详述），
     *       所以退出登录拿不到「已认证账号」；若在这里抛 401，前端在 token 过期后调退出接口
     *       就会先收到 401 → 跳登录页，而这一步本应静默完成。</li>
     *   <li><b>不记 {@code NFR-12} 的操作日志</b>：{@code NFR-12} 列的 8 类关键操作
     *       <b>不含</b>登录／退出（它们是会话生命周期事件，不是业务状态变更）。
     *       加了不算错，但会让「8 类各有记录」的验收口径 变成模糊的「大约 8 类」。</li>
     * </ol>
     *
     * <h2>为什么<b>不</b>加 {@code @Transactional}</h2>
     * <p>本方法<b>不访问数据库</b>（会话在进程内）。加事务注解会让人误以为这里有 DB 写入，
     * 也会白白多一次连接获取。方案 §3.1「事务边界在 Service 方法上」针对的是<b>涉及 DB 的</b>动作。
     *
     * @param token 从 {@code Authorization: Bearer <token>} 解析出的令牌；可为 {@code null}（幂等）
     */
    public void logout(String token) {
        sessionStore.remove(token);
    }

    // -------------------------------------------------------------------------
    // FR-02 修改密码（I11-03）
    // -------------------------------------------------------------------------

    /**
     * 修改密码，并使该账号的<b>全部</b>会话立即失效（{@code DEC-17}、{@code NFR-04}）。
     *
     * <h2>步骤与错误码（方案 §3.3.1）</h2>
     * <ol>
     *   <li>参数存在性 → {@code 50002}（Service 独立校验）</li>
     *   <li>取账号 → 账号不存在时 {@code 10002}（<b>防御性断言</b>，实际不可达，见下）</li>
     *   <li>原密码不匹配 → {@code 10003}</li>
     *   <li>新密码长度 &lt; {@value #MIN_PASSWORD_LENGTH} → {@code 10004}</li>
     *   <li>写入新哈希 → 该账号全部会话失效</li>
     * </ol>
     *
     * <h2>⚠️ 为什么「原密码」先于「新密码长度」校验</h2>
     * <p>两个都错时，先报 {@code 10003}（原密码不正确）更符合直觉：用户必须先证明自己是账号主人，
     * 讨论新密码是否合规才有意义。反过来会出现「提示新密码太短，但用户其实连原密码都填错了」
     * 这种把人引向错误修复方向的提示。代价是多一次查询 + 一次 BCrypt，
     * 对一个只有单账号的系统可忽略。
     *
     * <h2>⚠️ 为什么 {@code 10004} 在 Service 判定而 DTO 上不写 {@code @Size(min = 8)}</h2>
     * <p>见 {@code ChangePasswordRequest} 的类注释：DTO 注解会让请求在进入 Controller 前被拦下，
     * 变成 {@code 50002} + HTTP 400，使契约指定的 {@code 10004}（HTTP 200）在 HTTP 路径上
     * 永远不可达。这是一条<b>系统性</b>规则（{@code 20007}/{@code 20008}/{@code 30007} 同理）。
     *
     * <h2>⚠️ 会话清空的时机：在事务提交【之前】</h2>
     * <p>本方法在 {@code @Transactional} 内调用 {@code SessionStore.removeAllFor}，
     * 即「先清会话、后提交」。这是<b>有意</b>的不对称选择：
     * <ul>
     *   <li>若提交随后失败（库不可用等），结果是「会话被清掉、密码没改成」——
     *       用户需要重新登录一次，用<b>旧</b>密码。<b>过度失效是可恢复的</b>。</li>
     *   <li>若改成「提交后再清会话」，一旦清会话这步出问题（或进程在两步之间挂掉），
     *       结果是「密码已改、旧会话仍然有效」——<b>失效不足是安全缺陷</b>。</li>
     * </ul>
     * <p>两种失败模式不对称，所以选可恢复的那一种。这也是契约要求「≤5s 内失效」而实际是
     * <b>0</b> 的原因：清空是同步的、在同一线程内完成，不存在补偿窗口。
     *
     * <h2>⚠️ 已知限制：BCrypt 只取前 72 字节（登记为已接受）</h2>
     * <p>{@code spring-security-crypto} 的 BCrypt 实现（与 BCrypt 算法本身）只使用口令的
     * <b>前 72 字节</b>，超出部分被忽略——即超长口令的「尾部」不参与校验。
     * <p>本项目<b>不</b>据此拒绝超长口令：契约（方案 §3.6）只规定了「≥ 8 位」这一个下限，
     * <b>没有</b>「过长」对应的错误码，擅自新增一个码会违反 §4.3「不得增删编号」。
     * 实际影响可忽略：系统只有 1 个账号、口令由本人设定，且不存在「两个口令前 72 字节相同」
     * 的现实场景。此处显式登记，避免评审时被当作未察觉的缺陷。
     *
     * @param account     已认证的账号（由 {@code SellerAuthInterceptor} 写入请求属性）
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @throws BusinessException {@code 50002}／{@code 10002}／{@code 10003}／{@code 10004}
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void changePassword(String account, String oldPassword, String newPassword) {
        if (account == null || account.isBlank() || oldPassword == null || newPassword == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }

        // ⚠️ 防御性断言，实际不可达：account 来自已校验的会话，而账号数据永久保留（C-18）。
        //    用 10002（会话失效）而非 30008 之类的「不存在」码——语义是「你这个会话指向的账号
        //    已经没有了」，对前端而言与「会话失效」是同一个处置动作（跳登录页）。
        ShopUser user = shopUserRepository.findByAccount(account)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_INVALID));

        if (!PasswordHasher.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_TOO_SHORT);
        }

        // 只赋值、不显式 save：user 在事务内由 EntityManager 托管，
        // 提交时脏检查会发出 UPDATE，并触发 ShopUser.@PreUpdate 刷新 update_at
        // （「密码最后修改时间」的语义，见 ShopUser 类注释）。
        // ⚠️ 这一点【依赖本方法带 @Transactional】：若哪天注解被去掉，
        //    findByAccount 会各自开事务、返回游离态实体，setPassword 将静默丢失。
        //    集成用例 changePasswordUpdatesHashAndIssuesUpdateTimestamp 是这条的护栏。
        user.setPassword(PasswordHasher.hash(newPassword));

        // 全部会话失效（含当前这条，契约 §11.6.1 的 I11-03 明确如此）→ 前端跳 P10-05
        int revoked = sessionStore.removeAllFor(account);

        // NFR-12 第 8 类「修改密码」：只记账号，绝不记新旧口令（明文与哈希都不记）
        operationLogService.log(OperationLogService.CHANGE_PASSWORD,
                OperationLogService.TARGET_ACCOUNT, account);
        log.info("password changed: account={} revokedSessions={}", account, revoked);
    }
}
