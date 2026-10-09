package com.simpleshop.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.persistence.entity.ShopUser;

/**
 * 卖家账号仓储（设计说明书 §7.7）。主键为 {@code String}。
 *
 * <h2>⚠️ 刻意不提供的方法</h2>
 * <ul>
 *   <li><b>不提供 {@code findByAccountAndPassword}</b>：库中存的是<b>不可逆哈希</b>（{@code C-17}、{@code DEC-06}），
 *       无法用「查哈希」的方式比对；口令比对在 Service 用
 *       {@code PasswordHasher.matches(明文, 库中哈希)} 完成。</li>
 *   <li><b>不提供任何 delete／批量删除</b>：账号数据永久保留（{@code C-18}）。</li>
 * </ul>
 *
 * <p>本表为<b>单账号</b>表（{@code FR-01}），记录数恒为 0 或 1。
 */
public interface ShopUserRepository extends JpaRepository<ShopUser, String> {

    /**
     * 按登录账号取用户。{@code account} 全局唯一（{@code uk_user_account}），故至多 1 条。
     *
     * <p>用于 {@code I11-01} 登录：Service 先取用户，再用
     * {@code PasswordHasher.matches(明文, user.getPassword())} 校验口令。
     *
     * @param account 登录账号
     * @return 账号；不存在时 {@link Optional#empty()}
     */
    Optional<ShopUser> findByAccount(String account);

    /**
     * 账号总数（继承自 {@link JpaRepository}，签名即 {@code long count()}）。
     *
     * <p>用于初始化幂等性判定（{@code DEC-32}）：单账号表应恒为 {@code 0} 或 {@code 1}。
     */
    @Override
    long count();
}
