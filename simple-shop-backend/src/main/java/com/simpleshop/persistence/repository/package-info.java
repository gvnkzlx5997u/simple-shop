/**
 * Spring Data JPA 仓储接口（设计说明书 §7）。
 *
 * <p>本包包含 7 个接口：{@code GoodsRepository}、{@code IntentionRepository}、
 * {@code GoodsHistoryRepository}、{@code IntentionHistoryRepository}、
 * {@code TradeHistoryRepository}、{@code ShopUserRepository}、{@code QueueSequenceRepository}。
 *
 * <p>统一约定（§7.1）：仓储只做取数，不抛业务异常、不判断业务边界；
 * 加锁方法一律以 {@code ...ForUpdate} 结尾（{@code @Lock(PESSIMISTIC_WRITE)} + {@code @Query}）；
 * 分页方法必须显式带排序。
 */
package com.simpleshop.persistence.repository;
