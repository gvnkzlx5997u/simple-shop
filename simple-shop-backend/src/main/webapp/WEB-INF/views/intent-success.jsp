<%--
================================================================
 P10-03 提交成功页   —— simple-shop 买家端
================================================================
 入口    ：⚠️ 只能由 POST /intent 成功后【在同一个请求里】渲染；
           没有独立 URL，不能从地址栏直接访问（K5-02、BR-24）
 承接需求：FR-14（获取口令码）、UX-04（【必须】）、BR-24
 数据来源：请求属性 token（SubmitResultData.token）、saveTip（BuyerPrompt.SAVE_PASSCODE）
 文案来源：BuyerPrompt（第 10 章 §10.5 的唯一文案来源）

 本页有三处【业务方硬性要求】的落点（澄清 Q23 ①、UX-04），验收会逐条核对：
   ① 口令码【直接展示】（原文，不做任何遮挡）
   ② 保存提醒（M10-04）【必须】有
   ③ 口令码【仅展示这一次】：离开或刷新后不再显示，且【无任何回看入口】

 ⚠️⚠️ 口令码【绝不能出现在 URL 里】——否则可被刷新/收藏/分享，
     等同提供了一个回看入口，直接违反 BR-24 / K5-02。
     本页的做法：口令码只存在于【请求属性】，页面本身没有地址。
     ⇒ 因此本页【没有 GET 映射】，任何试图用 GET 打开它的请求都是 404。

 ⚠️ 本页无空态、无错误态。若 token 为空（异常情况：有人直接渲染了本视图），
    页面仍不会「假装成功」——见下方 <c:choose> 的兜底分支。
================================================================
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>提交成功 · simple-shop</title>
<link rel="stylesheet" href="${ctx}/buyer/buyer.css">
</head>

<body>

<header class="topbar">
  <span class="topbar__side"></span>
  <h1 class="topbar__title">提交成功</h1>
  <span class="topbar__side topbar__side--right"></span>
</header>

<main class="wrap stack">

  <c:choose>

    <%-- ======== 正常路径：拿到口令码 ======== --%>
    <c:when test="${not empty token}">

      <%-- ① 成功提示区 --%>
      <div class="notice notice--ok">
        <span class="notice__icon">✓</span>
        <span>购买意向已提交成功</span>
      </div>

      <%-- ② 口令码展示区（本页核心）—— 原文展示，不遮挡、不截断 --%>
      <section class="token-box">
        <p class="token-cap">您的口令码</p>
        <p class="token-code" id="tokenCode"><c:out value="${token}"/></p>
      </section>

      <%-- ③ 保存提醒区 —— M10-04【必须】。
           文案来自 BuyerPrompt.SAVE_PASSCODE（含占位符 XXXXXXXXXXXX），
           此处把占位符替换成真实口令码后整句展示，保持「同一句文案」的形态。 --%>
      <div class="notice notice--warn">
        <span class="notice__icon">⚠</span>
        <span><c:out value="${fn:replace(saveTip, 'XXXXXXXXXXXX', token)}"/></span>
      </div>

      <%-- ④ 后续操作区 --%>
      <div class="actions-row">
        <button type="button" class="btn btn--primary" id="btnCopy">复制口令码</button>
      </div>
      <div class="actions-row">
        <a class="btn btn--ghost" href="${ctx}/">我已保存</a>
        <a class="btn btn--ghost" href="${ctx}/query">凭口令码查询</a>
      </div>

      <p class="muted center" style="margin-top:6px;">
        离开本页后，口令码将不再显示，系统也不会另行发送。
      </p>

      <%-- 口令码明文交给脚本用于「复制」按钮。
           ⚠️ 用 fn:escapeXml 转义后嵌进 JS 字符串，防止文案意外含引号时截断脚本。 --%>
      <script>
      (function () {
        var TOKEN = '${fn:escapeXml(token)}';
        var btn = document.getElementById('btnCopy');
        if (!btn) return;

        function flash(text) {
          if (btn.dataset.lock) return;
          btn.dataset.lock = '1';
          var old = btn.textContent;
          btn.textContent = text;
          setTimeout(function () { btn.textContent = old; delete btn.dataset.lock; }, 1600);
        }

        /* 复制：优先用异步剪贴板 API（需 https 或 localhost），
           失败则退回临时 textarea + execCommand——
           本应用常以 http://localhost 跑，navigator.clipboard 可用；
           但若将来部署到 http 域名下，剪贴板 API 会被浏览器禁用，故必须有兜底。 */
        function copy() {
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(TOKEN).then(function () { flash('已复制 ✓'); }, fallback);
          } else {
            fallback();
          }
        }
        function fallback() {
          var ta = document.createElement('textarea');
          ta.value = TOKEN;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          try { document.execCommand('copy'); flash('已复制 ✓'); }
          catch (e) { flash('复制失败，请手动复制'); }
          document.body.removeChild(ta);
        }
        btn.addEventListener('click', copy);
      })();
      </script>

    </c:when>

    <%-- ======== 兜底：没有口令码就不假装成功 ========
         正常流程走不到这里（成功页只由 POST 成功分支渲染）。
         但如果有人直接渲染了本视图（比如以后有人给它配了 @GetMapping），
         页面会走到这一支：不显示「提交成功」、不显示空口令码框，
         而是明确说明并给出返回入口。
         ⇒ 宁可显示「没有可展示的口令码」，也不要显示一个空的口令码框、
           那会让用户以为「口令码是空的」。 --%>
    <c:otherwise>
      <div class="notice notice--warn">
        <span class="notice__icon">⚠</span>
        <span>没有可展示的口令码：本页仅在提交成功后显示一次，不能通过地址直接访问。</span>
      </div>
      <div class="actions-row">
        <a class="btn btn--primary" href="${ctx}/">返回首页</a>
      </div>
    </c:otherwise>

  </c:choose>

</main>

</body>
</html>
