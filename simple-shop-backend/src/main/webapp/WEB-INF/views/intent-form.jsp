<%--
================================================================
 P10-02 提交意向表单页   —— simple-shop 买家端
================================================================
 端点    ：GET  /intent/new   （B11-02，显示表单）
           POST /intent       （B11-03，提交；本页由 POST 的结果重新渲染）
 承接需求：FR-13（提交购买意向）、UX-01；触发 IS-01
 数据来源：无（本页只需商品是否存在/可提交，判定在 Service）
 文案来源：BuyerPrompt（第 10 章 §10.5 的唯一文案来源，不在本页写死中文）

 表单字段（第 9 章 §9.5.1）
   name 姓名     必填，≤50 字符、不含 < >、首尾无空格
   tel  联系电话 必填，7~20 位数字与 -
 ⚠️ 服务端【不信任】前端校验：POST 时由 Service 重新校验（G6-02、AC-17）。

 本页的状态（第 10 章 §10.3.2 ⑦）
   普通            显示空表单
   字段校验失败    fieldError 有值 → 就地提示，输入值【原样回填】(否则用户要重打)
   队列满/非在售   submitError 有值 → 顶部错误条
   提交成功        不在本页停留：Controller forward 到 P10-03

 ⚠️ 输入值回填很重要：校验失败时若不回填，用户填过的姓名电话会丢，
    这是「有表单页但体验像没做」的典型缺陷。

 ⚠️ 本页不放任何「交易流程说明」文字（UX-05 已取消）。
 ⚠️ 二次确认（M10-03）属 UX-01 开发方建议项，不是业务方硬性要求（澄清 Q23）。
    本实现按原型做法用前端 confirm 层；若禁用 JS，直接提交也能成功——
    这是有意的降级：确认弹层是体验优化，不是安全边界。
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
<title>提交购买意向 · simple-shop</title>
<link rel="stylesheet" href="${ctx}/buyer/buyer.css">
</head>

<body>

<header class="topbar">
  <a class="topbar__side topbar__back" href="${ctx}/">返回</a>
  <h1 class="topbar__title">提交购买意向</h1>
  <span class="topbar__side topbar__side--right"></span>
</header>

<main class="wrap stack">

  <%-- ======== ① 表单区 ======== --%>
  <form class="card card__body" method="post" action="${ctx}/intent" id="intentForm">

    <%-- 服务端拒绝提示（队列满 M10-12 / 商品状态已变化 M10-13 / 限流 / 字段类）：
         只在 Controller 送来 submitError 时渲染 --%>
    <c:if test="${not empty submitError}">
      <div class="notice notice--error" style="margin-bottom:14px;" role="alert">
        <span class="notice__icon">✕</span>
        <span><c:out value="${submitError}"/></span>
      </div>
    </c:if>

    <%-- 字段：name 姓名 --%>
    <div class="field">
      <label class="field__label" for="fName">姓名<span class="field__req">*</span></label>
      <input class="input" id="fName" name="name" type="text"
             maxlength="60" autocomplete="name" placeholder="请填写您的姓名"
             value="<c:out value='${formName}'/>">
      <%-- 字段级错误（M10-11 / M10-36）：服务端判定后回填 --%>
      <c:if test="${not empty nameError}">
        <p class="field__error"><c:out value="${nameError}"/></p>
      </c:if>
    </div>

    <%-- 字段：tel 联系电话 --%>
    <div class="field">
      <label class="field__label" for="fTel">联系电话<span class="field__req">*</span></label>
      <input class="input" id="fTel" name="tel" type="tel"
             maxlength="20" inputmode="tel" autocomplete="tel" placeholder="请填写您的联系电话"
             value="<c:out value='${formTel}'/>">
      <c:if test="${not empty telError}">
        <p class="field__error"><c:out value="${telError}"/></p>
      </c:if>
      <p class="field__hint">7~20 位数字，可含「-」</p>
    </div>

    <%-- ======== ② 提交区 ======== --%>
    <div class="actions-row" style="margin-top:18px;">
      <%-- ⚠️ 不用 disabled：可用条件由前端脚本动态控制（见下方 JS）。
           服务端另有独立校验兜底（G6-02），前端置灰只是界面表现。 --%>
      <button type="submit" class="btn btn--primary" id="btnSubmit">提交</button>
      <a class="btn btn--ghost" href="${ctx}/">返回</a>
    </div>

  </form>

</main>


<%-- ======== 二次确认弹层（M10-03，UX-01 建议项，非硬性要求） ======== --%>
<div class="modal-mask" id="confirmMask" hidden>
  <div class="modal" role="dialog" aria-modal="true" aria-labelledby="confirmTitle">
    <h2 class="modal__title" id="confirmTitle">确认提交</h2>
    <p class="modal__body" id="confirmBody"></p>
    <div class="modal__actions">
      <button type="button" class="btn btn--ghost" id="confirmCancel">再改改</button>
      <button type="button" class="btn btn--primary" id="confirmOk">确认提交</button>
    </div>
  </div>
</div>

<script>
/* ⚠️ 本脚本是【体验优化】，不是业务防线：
     - 前端校验只为了少发一次请求；
     - 服务端在 IntentFormController 里重新校验（G6-02、AC-17）；
     - JS 被禁用时表单照样提交，只是少了即时提示与确认弹层。 */
(function () {
  var form   = document.getElementById('intentForm');
  var fName  = document.getElementById('fName');
  var fTel   = document.getElementById('fTel');
  var btn    = document.getElementById('btnSubmit');
  var mask   = document.getElementById('confirmMask');
  var body   = document.getElementById('confirmBody');
  var confirmed = false;

  /* 文案取自模型（Java 侧 BuyerPrompt），不在此处硬编码中文——
     与「文案唯一来源在第 10 章 §10.5」这条口径保持一致 */
  var M_NAME_TEL = '${fn:escapeXml(jsMsgRequired)}';
  var M_NAME     = '${fn:escapeXml(jsMsgName)}';
  var M_TEL      = '${fn:escapeXml(jsMsgTel)}';
  var M_CONFIRM  = '${fn:escapeXml(jsMsgConfirm)}';

  function nameOk(v) {
    if (v === '') return { ok: false, msg: M_NAME_TEL };
    if (v !== v.trim() || v.length > 50 || /[<>]/.test(v)) return { ok: false, msg: M_NAME };
    return { ok: true };
  }
  function telOk(v) {
    if (v === '') return { ok: false, msg: M_NAME_TEL };
    if (!/^[0-9-]{7,20}$/.test(v)) return { ok: false, msg: M_TEL };
    return { ok: true };
  }
  function refresh() {
    var a = nameOk(fName.value), b = telOk(fTel.value);
    btn.disabled = !(a.ok && b.ok);
    return a.ok && b.ok;
  }

  fName.addEventListener('input', refresh);
  fTel.addEventListener('input', refresh);

  /* 提交：先前端校验 → 二次确认 → 放行 */
  form.addEventListener('submit', function (e) {
    if (confirmed) return;                 /* 已确认，直接放行 */
    if (!refresh()) { e.preventDefault(); return; }
    e.preventDefault();
    body.textContent = M_CONFIRM;
    mask.hidden = false;
  });

  document.getElementById('confirmCancel').addEventListener('click', function () {
    mask.hidden = true;
  });
  document.getElementById('confirmOk').addEventListener('click', function () {
    mask.hidden = true;
    confirmed = true;
    /* 防重复提交（NFR-17、E5-12） */
    btn.disabled = true;
    btn.textContent = '提交中…';
    form.submit();
  });

  refresh();
})();
</script>

</body>
</html>
