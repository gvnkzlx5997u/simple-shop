package com.simpleshop.testing;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 把某个 logger 的输出捕获到内存，供测试直接断言日志内容（{@code NFR-12} 的操作日志用例）。
 *
 * <p>实现为 {@link AutoCloseable}，用 try-with-resources 保证即使断言失败也会摘掉 appender——
 * 否则一个失败的用例会把 appender 留在 logger 上，后续用例的日志被重复收集，
 * 制造出与失败点无关的连锁失败。
 *
 * <h2>⚠️ 为什么在内存里捕获，而不是去读 {@code logs/audit.log}</h2>
 * <ul>
 *   <li>文件里可能留有<b>上一次运行</b>的内容，断言会假阳性；</li>
 *   <li>多线程/多用例交错写同一个文件，无法判断某条记录属于哪个用例；</li>
 *   <li>滚动策略（按天/按大小）会让「读到什么」依赖运行时长。</li>
 * </ul>
 * <p>文件 appender 本身是否正常工作，由「真实端口启动一次并检查文件确实被写入」来验证
 * （见方案 §8.7／§8.8 的验证证据），那属于部署级验证，不该混进单元用例。
 */
public final class AuditLogCapture implements AutoCloseable {

    /** 审计 logger 名，与 {@code OperationLogService} 及 {@code logback-spring.xml} 一致。 */
    public static final String AUDIT_LOGGER_NAME = "com.simpleshop.audit";

    private final String loggerName;
    private final ListAppender<ILoggingEvent> appender;

    private AuditLogCapture(String loggerName) {
        this.loggerName = loggerName;
        this.appender = new ListAppender<>();
        appender.start();
        logbackLogger(loggerName).addAppender(appender);
    }

    /** 捕获审计日志（{@code com.simpleshop.audit}）。 */
    public static AuditLogCapture audit() {
        return new AuditLogCapture(AUDIT_LOGGER_NAME);
    }

    /** 捕获任意 logger（例如断言异常处理器不会回显请求体）。 */
    public static AuditLogCapture of(Class<?> owner) {
        return new AuditLogCapture(owner.getName());
    }

    /** 已捕获事件的格式化消息（{@code %msg} 而非整行模板）。 */
    public List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** 把全部消息拼成一段文本，便于做「包含/不包含」类断言。 */
    public String joined() {
        return String.join("\n", messages());
    }

    /** 已捕获的条数。 */
    public int count() {
        return appender.list.size();
    }

    @Override
    public void close() {
        logbackLogger(loggerName).detachAppender(appender);
    }

    /**
     * {@code LoggerFactory} 声明返回 {@code org.slf4j.Logger}，运行时实现是 logback 的；
     * 这里做一次显式收窄，并把「不是 logback」这种情况变成清晰的失败而不是 {@code ClassCastException}。
     */
    private static Logger logbackLogger(String name) {
        org.slf4j.Logger logger = LoggerFactory.getLogger(name);
        if (!(logger instanceof Logger logback)) {
            throw new IllegalStateException("测试依赖 logback 的 ListAppender，但 SLF4J 绑定不是 logback: "
                    + logger.getClass().getName());
        }
        return logback;
    }
}
