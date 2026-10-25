package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.testing.DataLayerTestBase;

/**
 * 库内时间 = UTC 墙钟的两道门禁：值级（写入后裸查列比对）与结构级（绑定注解不漏标）。
 */
class StorageTimeZoneTest extends DataLayerTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("落库时间是 UTC 墙钟：裸查 datetime 列与 DatabaseTimeProvider 相差不超过 2 分钟")
    void storedDatetimeIsUtcWallClock() {
        BuyerAccount buyer = BuyerAccount.register(
                unique("buyer-"), PasswordHasher.hash("Buyer@1234"), "13800000000", null);
        buyerAccounts.saveAndFlush(buyer);

        // 绕开 Hibernate 读取裸列：若驱动层再发生时区折算（按会话时区渲染），
        // 裸值会比真实 UTC 慢一个时区差（JVM 与会话相差 8 小时时即慢 8 小时）。
        LocalDateTime rawCreateAt = jdbcTemplate.queryForObject(
                "select create_at from simpleshop_buyer_account where id = ?",
                LocalDateTime.class, buyer.getId());
        LocalDateTime utcNow = DatabaseTimeProvider.utcNow();

        assertThat(rawCreateAt)
                .isAfter(utcNow.minusMinutes(2))
                .isBefore(utcNow.plusMinutes(2));
    }

    @Test
    @DisplayName("实体全部 LocalDateTime 字段都标注字面量绑定注解")
    void everyEntityLocalDateTimeFieldBindsLiterally() {
        List<String> missing = new ArrayList<>();
        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            for (Attribute<?, ?> attribute : entity.getAttributes()) {
                if (!LocalDateTime.class.equals(attribute.getJavaType())
                        || !(attribute.getJavaMember() instanceof Field field)) {
                    continue;
                }
                JdbcTypeCode code = field.getAnnotation(JdbcTypeCode.class);
                if (code == null || code.value() != SqlTypes.LOCAL_DATE_TIME) {
                    missing.add(entity.getName() + "." + field.getName());
                }
            }
        }
        // 漏标注的字段退回 setTimestamp 绑定：按 JVM 时区解释、按 MySQL 会话时区渲染，
        // 库内时间会静默偏移一个时区差，且 ddl-auto=validate 与业务自洽的读写都无法发现。
        assertThat(missing).isEmpty();
    }
}
