package com.simpleshop.common.audit;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.simpleshop.common.auth.AuthInterceptor;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.tx.AfterCommit;

/**
 * 关键操作日志。
 *
 * <p>通过独立 logger 输出到单独文件，不写入业务表，也不参与事务：日志失败不影响业务。
 * 在事务内调用时推迟到提交成功后写入，回滚不会留下与数据不一致的审计记录。
 * 记录操作类型、目标对象与操作者，不记录密码、令牌或用户输入的原文。
 */
@Service
public class OperationLogService {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.simpleshop.audit");

    public static final String PUBLISH_GOODS = "PUBLISH_GOODS";
    public static final String UPDATE_STOCK = "UPDATE_STOCK";
    public static final String OFF_SHELF_GOODS = "OFF_SHELF_GOODS";
    public static final String UPDATE_GOODS_CATEGORY = "UPDATE_GOODS_CATEGORY";

    public static final String CREATE_CATEGORY = "CREATE_CATEGORY";
    public static final String RENAME_CATEGORY = "RENAME_CATEGORY";
    public static final String DELETE_CATEGORY = "DELETE_CATEGORY";

    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";

    public static final String CREATE_ORDER = "CREATE_ORDER";
    public static final String ORDER_CONFIRM = "ORDER_CONFIRM";
    public static final String ORDER_PREPARE = "ORDER_PREPARE";
    public static final String ORDER_SHIP = "ORDER_SHIP";
    public static final String ORDER_COMPLETE = "ORDER_COMPLETE";
    public static final String ORDER_CANCEL = "ORDER_CANCEL";

    public static final String ADD_CART = "ADD_CART";
    public static final String REMOVE_CART = "REMOVE_CART";
    public static final String ADD_FAVORITE = "ADD_FAVORITE";
    public static final String REMOVE_FAVORITE = "REMOVE_FAVORITE";

    public static final String TARGET_GOODS = "GOODS";
    public static final String TARGET_CATEGORY = "CATEGORY";
    public static final String TARGET_ACCOUNT = "ACCOUNT";
    public static final String TARGET_ORDER = "ORDER";

    /** 购物车与收藏的目标 ID 用商品 ID：行 ID 是随机 UUID，对排查没有信息量。 */
    public static final String TARGET_CART = "CART";
    public static final String TARGET_FAVORITE = "FAVORITE";

    public void log(String operation, String targetType, String targetId) {
        logDeferred(operation, targetType, targetId, null);
    }

    /** {@code detail} 只放裁决方式之类的枚举值，不得包含密码、令牌或用户输入原文。 */
    public void log(String operation, String targetType, String targetId, String detail) {
        logDeferred(operation, targetType, targetId, detail);
    }

    private void logDeferred(String operation, String targetType, String targetId, String detail) {
        try {
            // 操作者必须在请求线程内捕获；afterCommit 回调执行时请求上下文可能已经变化。
            String operator = currentOperator();
            AfterCommit.run(() -> write(operation, targetType, targetId, operator, detail));
        } catch (RuntimeException ignored) {
            // 审计失败不影响业务
        }
    }

    private static void write(
            String operation, String targetType, String targetId, String operator, String detail) {
        try {
            if (detail == null) {
                AUDIT.info("operation={} targetType={} targetId={} operator={}",
                        operation, targetType, targetId, operator);
            } else {
                AUDIT.info("operation={} targetType={} targetId={} operator={} detail={}",
                        operation, targetType, targetId, operator, detail);
            }
        } catch (RuntimeException ignored) {
            // 审计失败不影响业务
        }
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
