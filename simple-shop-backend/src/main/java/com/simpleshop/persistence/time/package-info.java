/**
 * 时间口径支撑（设计说明书 §4.8、§10.5）。
 *
 * <p>{@link com.simpleshop.persistence.time.DatabaseTimeProvider} 是全项目唯一的
 * {@code datetime} 取值来源，统一提供 {@code LocalDateTime.now(ZoneOffset.UTC)}。
 *
 * <p><b>禁止</b>在项目任何位置直接调用 {@code LocalDateTime.now()} 或 {@code new Date()} 入库。
 */
package com.simpleshop.persistence.time;
