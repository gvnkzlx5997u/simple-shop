package com.simpleshop.service.dto;

/**
 * 买家端 Service 的返回值：要么成功带数据，要么被拒并给出提示码（第 11 章 §11.6.2）。
 *
 * <pre>
 * BuyerResult&lt;SubmitResultData&gt; r = service.submitIntention(...);
 * switch (r) {                                   // Java 21 穷尽 switch，编译期检查
 *     case BuyerResult.Ok&lt;SubmitResultData&gt; ok       -&gt; renderSuccess(ok.data());
 *     case BuyerResult.Rejected&lt;SubmitResultData&gt; no -&gt; renderPrompt(no.prompt());
 * }
 * </pre>
 *
 * <h2>⚠️ 为什么用「密封结果」而不用异常（与卖家端的区别，评审会问）</h2>
 * <table border="1">
 *   <caption>两端的失败表达方式为何不同</caption>
 *   <tr><th></th><th>卖家端（{@code I11-xx}）</th><th>买家端（{@code B11-xx} / 本轮）</th></tr>
 *   <tr><td>契约形态</td><td>HTTP + JSON 业务码</td><td><b>服务端渲染，无传输码</b>（§11.6.2 原文）</td></tr>
 *   <tr><td>失败表达</td><td>抛 {@code BusinessException}，由<b>全局处理器</b>统一翻译成 HTTP</td>
 *       <td><b>返回值</b></td></tr>
 *   <tr><td>理由</td><td>异常能把「业务码 → HTTP 状态」的映射集中到一处</td>
 *       <td>买家端<b>没有</b>那层全局处理器；用返回值可让调用方（下一轮 JSP）
 *           用 <b>Java 21 穷尽 switch</b> 处理，<b>漏写一个分支就编译不过</b>——
 *           比「记得 catch」可靠</td></tr>
 * </table>
 * <p>这也与方案对 {@link PasscodeQueryResult} 的取向一致（「从类型上让『失效时顺便带出买家信息』
 * 无法表达」）——本项目的偏好是<b>把约束做进类型</b>，而不是靠纪律。
 *
 * <h2>⚠️⚠️ 一个必须遵守的规则：所有「被拒」分支都要出现在<b>任何写操作之前</b></h2>
 * <p>返回 {@link Rejected} 是<b>正常返回</b>，不是抛异常——因此 {@code @Transactional} 会
 * <b>正常提交</b>，<b>不会回滚</b>。这与卖家端「异常即回滚」（§3.1）的语义<b>相反</b>。
 * <p>所以实现时必须保证：<b>先做完所有校验与前置判定，再进入写阶段</b>。
 * 若某天有人在写操作之后加了一个拒绝分支，那次写就会<b>被提交</b>——
 * 而这类缺陷不会报错、测试也未必覆盖。各 Service 方法的注释里都复述了这条规则。
 *
 * <h2>便利方法 vs 穷尽 switch</h2>
 * <p>{@link #rejectedPrompt()} 与 {@link #orElseNull()} 是为「确定只有一种结局」的调用处
 * （以及测试）准备的便利方法。⚠️ <b>生产代码优先用 {@code switch}</b>：
 * 那是编译期穷尽的，而便利方法会把「忘了处理被拒」变成运行期的 {@code null}。
 *
 * @param <T> 成功时的数据类型；无数据的操作（撤销、改信息）用 {@code BuyerResult<Void>}
 */
public sealed interface BuyerResult<T> {

    /** 成功。{@code data} 对被拒无意义；无数据的操作为 {@code null}。 */
    record Ok<T>(T data) implements BuyerResult<T> {
    }

    /** 被拒。{@code prompt} 决定界面文案（{@link BuyerPrompt}）。 */
    record Rejected<T>(BuyerPrompt prompt) implements BuyerResult<T> {
    }

    // -------------------------------------------------------------------------
    // 工厂
    // -------------------------------------------------------------------------

    /** 成功并携带数据。 */
    static <T> BuyerResult<T> ok(T data) {
        return new Ok<>(data);
    }

    /** 成功且无数据（撤销、改信息这类「做完就完了」的操作）。 */
    static BuyerResult<Void> ok() {
        return new Ok<>(null);
    }

    /** 被拒并给出提示码。 */
    static <T> BuyerResult<T> rejected(BuyerPrompt prompt) {
        return new Rejected<>(prompt);
    }

    // -------------------------------------------------------------------------
    // 便利方法（⚠️ 生产代码优先用 switch，见类注释）
    // -------------------------------------------------------------------------

    /**
     * 被拒时的提示码；成功时为 {@code null}。
     *
     * <p>⚠️ 返回 {@code null} 表示<b>成功</b>，不是「没有提示」——
     * 这正是便利方法的弱点，故仅建议测试与「已确定结局」的分支使用。
     */
    default BuyerPrompt rejectedPrompt() {
        return this instanceof Rejected<?> rejected ? rejected.prompt() : null;
    }

    /** 成功时的数据；被拒时为 {@code null}。 */
    default T orElseNull() {
        if (this instanceof Ok<?> ok) {
            // 类型安全：Ok<T> 的 data 必然是 T（泛型擦除使其在编译期无法自动推断）
            @SuppressWarnings("unchecked")
            T value = (T) ok.data();
            return value;
        }
        return null;
    }
}
