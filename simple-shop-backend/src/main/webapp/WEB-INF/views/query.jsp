<%--
================================================================
 P10-04 口令码查询与操作页   —— simple-shop 买家端
================================================================
 端点    ：GET  /query           （B11-04，显示查询表单）
           POST /query           （B11-05，查询；本页由结果重新渲染）
           POST /query/update    （B11-06，修改姓名/电话）
           POST /query/revoke    （B11-07，撤销意向）
 承接需求：FR-15（查询）、FR-16（改信息）、FR-17（撤销）、
           FR-20（唯一查询入口）、FR-22（三态）、UX-02、UX-03
 数据来源：请求属性 queryState / rank / name / tel / token / feedback
 文案来源：BuyerPrompt（第 10 章 §10.5 的唯一文案来源）

 ⚠️⚠️ 三条铁律（第 10 章 §10.3.4 ⑦）
   ① 失效提示【不得说明原因】、【不得返回买家信息】（B5-01、BR-29）
      ⇒ 本页对 expired 态只渲染一句 M10-06，不渲染姓名/电话，也不提及「已撤销/已完成」。
        这一条由【类型】保证：Service 返回的 PasscodeQueryResult.Expired 是零字段 record，
        想带出原因都表达不出来（见该类注释）。
   ② 口令码失效【只看意向是否终态，与商品状态无关】（B5-02、BR-26、INV-07）
   ③ 口令码查询【不做错误次数限制】（DEC-12、NFR-15）

 ⚠️「格式校验」（C-16，M10-28）与「有效性校验」（三态，BR-28）是【两件事】：
    格式不符 ⇒ 前端提示 M10-28，【不发起查询】；
    格式正确 ⇒ 才 POST 给服务端查三态。

 ⚠️ 改信息页必须【预填当前值】：留空表示「该项不改」。
    若不预填，用户想只改电话时必须重打姓名，否则空串会被判「请填写姓名与联系电话」。

 ⚠️ 本页【不写 sessionStorage】、不写任何原型专用代码——查询结果全部来自服务端。
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
<title>凭口令码查询 · simple-shop</title>
<link rel="stylesheet" href="${ctx}/buyer/buyer.css">
</head>

<body>

<header class="topbar">
  <a class="topbar__side topbar__back" href="${ctx}/">返回</a>
  <h1 class="topbar__title">凭口令码查询</h1>
  <span class="topbar__side topbar__side--right"></span>
</header>

<main class="wrap stack">

  <%-- ======== ① 口令码输入区 ======== --%>
  <form class="card card__body" method="post" action="${ctx}/query" id="queryForm">
    <div class="field">
      <label class="field__label" for="fToken">口令码<span class="field__req">*</span></label>
      <input class="input input--code" id="fToken" name="token" type="text"
             maxlength="20" autocomplete="off" spellcheck="false"
             placeholder="请输入 12 位口令码"
             value="<c:out value='${formToken}'/>">
      <%-- 格式错误（M10-28）：服务端也会判一次（防止绕过前端脚本） --%>
      <c:if test="${not empty tokenError}">
        <p class="field__error"><c:out value="${tokenError}"/></p>
      </c:if>
      <p class="field__hint">12 位大写字母与数字，输入后会自动转为大写</p>
    </div>
    <div class="actions-row">
      <button type="submit" class="btn btn--primary">查询</button>
    </div>
  </form>

  <%-- ======== 操作结果反馈：分两个位置渲染 ========
       ① 修改成功（仍在 active 态）：放在「当前状态」卡片与「修改信息」表单之间——
          用户刚操作完视线在表单区，提示放页首容易被忽略（2026-10-11 用户实测反馈）。
       ② 撤销成功（转 expired 态）：操作区整体收起，提示放结果卡片下方。
       两处互斥（queryState 不同），不会重复渲染。 --%>

  <%-- ======== ② 结果展示区 ======== --%>
  <c:choose>

    <%-- ---- 错误口令（M10-05）/ 失效口令（M10-06）----
         ⚠️ 两者【都】只渲染一句话，不渲染姓名、电话、状态、原因。
            这是 BR-29 的落地，不是「忘了写」。 --%>
    <c:when test="${queryState eq 'notFound'}">
      <section class="result result--error">
        <p class="result__head">查询结果</p>
        <p class="result__main"><c:out value="${queryMessage}"/></p>
      </section>
    </c:when>

    <c:when test="${queryState eq 'expired'}">
      <section class="result result--error">
        <p class="result__head">查询结果</p>
        <%-- ⚠️ M10-06 原文只有六个字：「该口令码已失效」。
             此处【不】追加「可能已被撤销或已完成」这类解释——BR-29 明文禁止说明原因。 --%>
        <p class="result__main"><c:out value="${queryMessage}"/></p>
      </section>
    </c:when>

    <%-- ---- 有效口令 ---- --%>
    <c:when test="${queryState eq 'active'}">
      <section class="result result--ok">
        <p class="result__head">
          <c:choose>
            <c:when test="${trading}">当前状态：交易中</c:when>
            <c:otherwise>当前状态：排队中</c:otherwise>
          </c:choose>
        </p>
        <p class="result__main">
          <c:choose>
            <%-- M10-08：trading 不占位次，故不显示位次 --%>
            <c:when test="${trading}"><c:out value="${queryMessage}"/></c:when>
            <%-- M10-07：文案含占位符 N，此处用服务端算出的位次替换 --%>
            <c:otherwise>
              <c:out value="${fn:replace(queryMessage, 'N', rank)}"/>
            </c:otherwise>
          </c:choose>
        </p>
        <div class="kv">
          <div class="kv__row"><span class="kv__k">姓名</span><span class="kv__v"><c:out value="${buyerName}"/></span></div>
          <div class="kv__row"><span class="kv__k">联系电话</span><span class="kv__v"><c:out value="${buyerTel}"/></span></div>
        </div>
      </section>

      <%-- ① 修改成功的反馈：状态卡片与修改信息表单之间（必看得见） --%>
      <c:if test="${not empty feedback}">
        <div class="notice notice--ok" role="status">
          <span class="notice__icon">✓</span>
          <span><c:out value="${feedback}"/></span>
        </div>
      </c:if>

      <%-- ======== ③ 操作区（仅在「有效口令」时展开） ======== --%>
      <section class="stack">

        <%-- 修改信息（FR-16、UX-03）--%>
        <div class="card card__body">
          <h2 class="sec-title">修改信息</h2>
          <div class="notice notice--info" style="margin-bottom:14px;">
            <span class="notice__icon">ⓘ</span>
            <span><c:out value="${editHint}"/></span>
          </div>

          <form method="post" action="${ctx}/query/update" id="updateForm">
            <%-- 口令码随表单一起提交，用于定位是哪条意向 --%>
            <input type="hidden" name="token" value="<c:out value='${token}'/>">

            <div class="field">
              <label class="field__label" for="fNewName">新姓名</label>
              <%-- ⚠️ 预填当前值：留空 = 不改；不预填的话用户只改电话时会被判「请填写姓名」 --%>
              <input class="input" id="fNewName" name="name" type="text" maxlength="60"
                     autocomplete="off" value="<c:out value='${buyerName}'/>">
              <c:if test="${not empty updateNameError}">
                <p class="field__error"><c:out value="${updateNameError}"/></p>
              </c:if>
            </div>

            <div class="field">
              <label class="field__label" for="fNewTel">新联系电话</label>
              <input class="input" id="fNewTel" name="tel" type="tel" maxlength="20"
                     inputmode="tel" autocomplete="off" value="<c:out value='${buyerTel}'/>">
              <c:if test="${not empty updateTelError}">
                <p class="field__error"><c:out value="${updateTelError}"/></p>
              </c:if>
            </div>

            <p class="muted" style="margin:12px 0 0;">可只修改其中一项，留空表示不修改。</p>

            <div class="actions-row" style="margin-top:14px;">
              <button type="submit" class="btn btn--primary">修改信息</button>
            </div>
          </form>
        </div>

        <%-- 撤销意向（FR-17）--%>
        <div class="card card__body">
          <h2 class="sec-title">撤销意向</h2>
          <p class="muted" style="margin:0 0 12px;">
            撤销仅对「排队中」的意向开放；进入交易后无法撤销。
          </p>

          <c:choose>
            <%-- 交易中：按 10-C【禁用并提示，不隐藏】——
                 隐藏按钮会让用户以为「没有这个功能」，禁用+说明才解释了为什么不能点 --%>
            <c:when test="${trading}">
              <form method="post" action="${ctx}/query/revoke">
                <input type="hidden" name="token" value="<c:out value='${token}'/>">
                <button type="submit" class="btn btn--danger" disabled aria-disabled="true">撤销意向</button>
              </form>
              <p class="field__error" style="margin-top:10px;"><c:out value="${revokeTip}"/></p>
            </c:when>
            <%-- 排队中：可撤销，点击弹二次确认（M10-09，UX-02 建议项） --%>
            <c:otherwise>
              <form method="post" action="${ctx}/query/revoke" id="revokeForm">
                <input type="hidden" name="token" value="<c:out value='${token}'/>">
                <button type="submit" class="btn btn--danger" id="btnRevoke">撤销意向</button>
              </form>
            </c:otherwise>
          </c:choose>
        </div>

      </section>
    </c:when>

    <%-- ---- 初始态 / 其他：什么都不显示（页面只剩输入区）---- --%>
    <c:otherwise>
      <%-- 无结果区。此处刻意留空：初次进入时显示一个空的「查询结果」框
           会让用户以为查询已经执行过、只是没查到。 --%>
    </c:otherwise>

  </c:choose>

  <%-- ② 撤销成功的反馈：此时是 expired 态（操作区已收起），
       提示放结果卡片下方——失效态页面很短，下方即视线落点。
       仅 expired 态渲染，与①互斥不重复。 --%>
  <c:if test="${queryState eq 'expired' and not empty feedback}">
    <div class="notice notice--ok" role="status">
      <span class="notice__icon">✓</span>
      <span><c:out value="${feedback}"/></span>
    </div>
  </c:if>

</main>


<%-- ======== 撤销二次确认（M10-09，UX-02 建议项） ======== --%>
<div class="modal-mask" id="confirmMask" hidden>
  <div class="modal" role="dialog" aria-modal="true" aria-labelledby="confirmTitle">
    <h2 class="modal__title" id="confirmTitle">确认撤销</h2>
    <p class="modal__body" id="confirmBody"></p>
    <div class="modal__actions">
      <button type="button" class="btn btn--ghost" id="confirmCancel">取消</button>
      <button type="button" class="btn btn--danger" id="confirmOk">确定撤销</button>
    </div>
  </div>
</div>

<script>
(function () {
  var fToken = document.getElementById('fToken');
  if (!fToken) return;

  var M_MALFORMED = '${fn:escapeXml(jsMsgMalformed)}';
  var M_CONFIRM_REVOKE = '${fn:escapeXml(jsMsgRevokeConfirm)}';

  /* ---------- 10-G：输入侧去首尾空格 + 自动转大写 ----------
     ⚠️ 这只是【输入体验】。服务端 PasscodeService.isWellFormed 内部也会先归一化，
        所以两边的判定结果一致，不会出现「前端说格式对、后端说格式错」。 */
  fToken.addEventListener('input', function () {
    var v = fToken.value.replace(/\s+/g, '').toUpperCase();
    if (v !== fToken.value) fToken.value = v;
  });

  /* ---------- 格式前置校验：格式不符【不发起查询】（§10.3.4 ④）---------- */
  var queryForm = document.getElementById('queryForm');
  if (queryForm) {
    queryForm.addEventListener('submit', function (e) {
      var v = fToken.value.trim().toUpperCase();
      fToken.value = v;
      if (!/^[A-Z0-9]{12}$/.test(v)) {
        e.preventDefault();
        var box = document.getElementById('tokenFormatError');
        if (!box) {
          box = document.createElement('p');
          box.className = 'field__error';
          box.id = 'tokenFormatError';
          fToken.parentNode.appendChild(box);
        }
        box.textContent = M_MALFORMED;
        fToken.classList.add('is-error');
      }
    });
  }

  /* ---------- 撤销二次确认 ---------- */
  var revokeForm = document.getElementById('revokeForm');
  if (revokeForm) {
    var mask = document.getElementById('confirmMask');
    var body = document.getElementById('confirmBody');
    var okBtn = document.getElementById('confirmOk');
    var confirmed = false;

    revokeForm.addEventListener('submit', function (e) {
      if (confirmed) return;
      e.preventDefault();
      body.textContent = M_CONFIRM_REVOKE;
      mask.hidden = false;
    });
    document.getElementById('confirmCancel').addEventListener('click', function () {
      mask.hidden = true;
    });
    okBtn.addEventListener('click', function () {
      mask.hidden = true;
      confirmed = true;
      okBtn.disabled = true;
      okBtn.textContent = '撤销中…';
      revokeForm.submit();
    });
  }
})();
</script>

</body>
</html>
