package com.simpleshop.common.audit;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.simpleshop.common.auth.AuthInterceptor;
import com.simpleshop.common.auth.AuthPrincipal;

/**
 * 关键操作日志。
 *
 * <p>通过独立 logger 输出到单独文件，不写入业务表，也不参与事务：日志失败不影响业务。
 * 记录操作类型、目标对象与操作者，不记录密码、令牌或用户输入的原文。
 */
@Service
public class OperationLogService {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.simpleshop.audit");

    public static final String PUBLISH_GOODS = "PUBLISH_GOODS";
    public static final String UPDATE_STOCK = "UPDATE_STOCK";
    public static final String OFF_SHELF_GOODS = "OFF_SHELF_GOODS";
    public static final String RE_LIST_GOODS = "RE_LIST_GOODS";
    public static final String UPDATE_GOODS_CATEGORY = "UPDATE_GOODS_CATEGORY";

    public static final String CREATE_CATEGORY = "CREATE_CATEGORY";
    public static final String RENAME_CATEGORY = "RENAME_CATEGORY";
    public static final String DELETE_CATEGORY = "DELETE_CATEGORY";

    public static final String ENTER_TRADE = "ENTER_TRADE";
    public static final String MARK_TRADE_SUCCESS = "MARK_TRADE_SUCCESS";
    public static final String MARK_TRADE_FAILURE = "MARK_TRADE_FAILURE";

    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";

    public static final String TARGET_GOODS = "GOODS";
    public static final String TARGET_CATEGORY = "CATEGORY";
    public static final String TARGET_INTENTION = "INTENTION";
    public static final String TARGET_ACCOUNT = "ACCOUNT";

    public void log(String operation, String targetType, String targetId) {
        AUDIT.info("operation={} targetType={} targetId={} operator={}",
                operation, targetType, targetId, currentOperator());
    }

    /** {@code detail} 只放裁决方式之类的枚举值，不得包含密码、令牌或用户输入原文。 */
    public void log(String operation, String targetType, String targetId, String detail) {
        AUDIT.info("operation={} targetType={} targetId={} operator={} detail={}",
                operation, targetType, targetId, currentOperator(), detail);
    }

    private String currentOperator() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                Object value = request.getAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE);
                if (value instanceof AuthPrincipal principal) {
                    return principal.role() + ":" + principal.username();
                }
            }
        } catch (RuntimeException ignored) {
            // 脱离请求上下文时降级，审计失败不应影响业务
        }
        return "unknown";
    }
}
