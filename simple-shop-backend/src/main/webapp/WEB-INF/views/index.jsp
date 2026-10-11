<%--
================================================================
 P10-01 当前商品页（首页）   —— simple-shop 买家端
================================================================
 端点    ：GET /                      （B11-01，URL 无参数：A-03、S10-01）
 承接需求：FR-12（浏览）、FR-18（交易中提示）、FR-19（展示规则）、UX-06
 数据来源：BuyerIntentionService#browseCurrentGoods() → GoodsData（无商品时为 null）
 文案来源：BuyerPrompt（第 10 章 §10.5 的唯一文案来源，不在本页写死中文）

 三态（第 10 章 §10.3.1 ⑦ / 第 5 章 §5.4.1）：
   goods = null        → 不显示卡片，显示 M10-02「暂无在售商品」（也覆盖 off_sale）
   goods.status=on_sale→ 正常展示，提交入口可用
   goods.status=frozen → 展示卡片 +「已冻结」标签 + M10-01，提交入口【置灰保留】(10-B)

 ⚠️ 关键口径：冻结商品的【详情仍可浏览】(BR-14)
    不可用的是「提交入口」，不是页面本身。
 ⚠️「凭口令码查询」【始终可用】—— 它是买家查自己意向的唯一入口(FR-20)，
    即使无商品也必须保留，否则已提交过的买家就查不到了。
 ⚠️ 所有商品文本一律走 <c:out>（HTML 转义）——C-15 / NFR-20 的输出转义要求。
================================================================
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>商品详情 · simple-shop</title>
<link rel="stylesheet" href="${ctx}/buyer/buyer.css">
</head>

<body>

<header class="topbar">
  <span class="topbar__side"></span>
  <h1 class="topbar__title">商品详情</h1>
  <span class="topbar__side topbar__side--right"></span>
</header>

<main class="wrap stack">

  <%-- ======== ② 状态提示区 ======== --%>
  <%-- M10-01【必须】：冻结（手动/交易两种来源一律提示本句，AC-17） --%>
  <c:if test="${not empty goods and goods.status eq 'frozen'}">
    <div class="notice notice--warn">
      <span class="notice__icon">⚠</span>
      <span><c:out value="${promptGoodsFrozen}"/></span>
    </div>
  </c:if>

  <%-- ======== ① 商品卡片区 ======== --%>
  <c:if test="${not empty goods}">
    <article class="card card--media-split">

      <%-- 商品图片 pic_url：未上传（null）时显示占位图 --%>
      <div class="card__media">
        <c:choose>
          <c:when test="${not empty goods.picUrl}">
            <img src="${ctx}${goods.picUrl}" alt="<c:out value='${goods.name}'/>">
          </c:when>
          <c:otherwise>
            <span>商品图片占位</span>
          </c:otherwise>
        </c:choose>
      </div>

      <div class="card__body">
        <h2 class="card__title">
          <%-- 商品名称：原文展示（转义） --%>
          <span><c:out value="${goods.name}"/></span>
          <%-- 当前状态：on_sale 不显示标签；frozen 显示「已冻结」 --%>
          <c:if test="${goods.status eq 'frozen'}">
            <span class="tag tag--frozen">已冻结</span>
          </c:if>
        </h2>

        <%-- 商品描述：可选（§3.6），为空则整段不渲染 --%>
        <c:if test="${not empty goods.description}">
          <p class="card__desc"><c:out value="${goods.description}"/></p>
        </c:if>

        <%-- 价格：人民币、两位小数（C-12）。
             goods.price 是服务端下发的【字符串】（已保证 scale=2，11-F / C-12），
             此处只做千分位分组，不再引入浮点运算。 --%>
        <div class="price"><span class="price__unit">¥</span><fmt:formatNumber value="${goods.price}" type="number" pattern="#,##0.00"/></div>
      </div>
    </article>
  </c:if>

  <%-- ======== 空态：无当前商品（也覆盖 off_sale） ======== --%>
  <c:if test="${empty goods}">
    <div class="card empty">
      <div class="empty__icon" aria-hidden="true">▤</div>
      <p class="empty__text"><c:out value="${promptNoGoods}"/></p>
    </div>
  </c:if>

  <%-- ======== ③ 操作区 ======== --%>
  <div class="actions-row">

    <%-- 按钮：提交购买意向（B11-02）
         可用条件 = 商品在售；无当前商品时【不显示本按钮】（⑦「提交入口：不存在」）；
         冻结时按 10-B【置灰禁用并保留】——不隐藏，让买家知道商品还在。
         服务端另有独立校验兜底（G6-02），本处只是界面表现。 --%>
    <c:if test="${not empty goods}">
      <c:choose>
        <c:when test="${goods.status eq 'on_sale'}">
          <a class="btn btn--primary" href="${ctx}/intent/new">提交购买意向</a>
        </c:when>
        <c:otherwise>
          <a class="btn btn--primary" href="${ctx}/intent/new"
             aria-disabled="true" tabindex="-1">提交购买意向</a>
        </c:otherwise>
      </c:choose>
    </c:if>

    <%-- 按钮：凭口令码查询（B11-04）—— 始终可用，与商品状态无关（FR-20） --%>
    <a class="btn btn--ghost" href="${ctx}/query">凭口令码查询</a>

  </div>

</main>

</body>
</html>
