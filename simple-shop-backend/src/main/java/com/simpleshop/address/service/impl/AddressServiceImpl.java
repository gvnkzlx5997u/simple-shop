package com.simpleshop.address.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.address.dto.AddressData;
import com.simpleshop.address.dto.AddressListData;
import com.simpleshop.address.dto.AddressRequest;
import com.simpleshop.address.entity.Address;
import com.simpleshop.address.repository.AddressRepository;
import com.simpleshop.address.service.AddressService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.text.UtcIso8601;

/**
 * 地址簿实现。三处与默认地址有关的细节：
 *
 * <ul>
 *   <li><b>新建一律先落成非默认</b>，再由 {@code ensureDefault} 在锁内补默认。直接以「是否第一条」
 *       决定默认，会在并发下让两个请求都自认第一条、撞唯一索引；先插后补则天然串行。</li>
 *   <li><b>设默认先清旧的、刷库、再置新的</b>。同一事务里一次 flush 的话，Hibernate 的更新顺序
 *       不可控，可能先写新默认而撞唯一索引。</li>
 *   <li><b>删默认后取最新一条补位</b>，而不是「随便挑一条」：用户刚建的那条通常就是他想用的。</li>
 * </ul>
 *
 * <p>这里不写审计：地址是买家自助维护的个人资料，改动频繁且不影响交易与库存；
 * 订单只保存下单时的快照，地址后来怎么改都追溯不到订单上。
 */
@Service
public class AddressServiceImpl implements AddressService {

    /** 上限 10 条：够用且能在一屏内管理，不做分页。 */
    private static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;

    public AddressServiceImpl(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    @Transactional(readOnly = true)
    public AddressListData list(String buyerId) {
        List<AddressData> items = addresses
                .findByBuyerIdOrderByDefaultFlagDescCreateAtDescIdDesc(buyerId)
                .stream()
                .map(AddressServiceImpl::data)
                .toList();
        return new AddressListData(items);
    }

    @Override
    @Transactional
    public AddressData create(String buyerId, AddressRequest request) {
        if (addresses.countByBuyerId(buyerId) >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED,
                    "at most " + MAX_ADDRESSES + " addresses are allowed");
        }

        Address created = addresses.saveAndFlush(Address.create(
                buyerId,
                request.receiverName(),
                request.receiverPhone(),
                request.detailAddress(),
                false));
        ensureDefault(buyerId, created);
        return data(created);
    }

    @Override
    @Transactional
    public AddressData update(String buyerId, String addressId, AddressRequest request) {
        Address address = lockOwned(buyerId, addressId);
        address.update(
                request.receiverName(), request.receiverPhone(), request.detailAddress());
        return data(addresses.saveAndFlush(address));
    }

    @Override
    @Transactional
    public void delete(String buyerId, String addressId) {
        Address address = lockOwned(buyerId, addressId);
        boolean wasDefault = address.isDefault();
        addresses.delete(address);
        addresses.flush();

        if (wasDefault) {
            addresses.findFirstByBuyerIdOrderByCreateAtDescIdDesc(buyerId)
                    .ifPresent(latest -> {
                        latest.markDefault();
                        addresses.saveAndFlush(latest);
                    });
        }
    }

    @Override
    @Transactional
    public AddressData setDefault(String buyerId, String addressId) {
        List<Address> owned = addresses.findAllWithLockByBuyerId(buyerId);
        Address target = owned.stream()
                .filter(address -> address.getId().equals(trim(addressId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        // 先清掉旧默认并刷库，再置新的：同一次 flush 里更新顺序不可控，可能先写新默认而撞唯一索引。
        boolean cleared = false;
        for (Address address : owned) {
            if (address.isDefault() && !address.getId().equals(target.getId())) {
                address.clearDefault();
                cleared = true;
            }
        }
        if (cleared) {
            addresses.flush();
        }
        target.markDefault();
        return data(addresses.saveAndFlush(target));
    }

    /**
     * 该买家还没有默认地址时，把这条设成默认。
     *
     * <p>锁住买家全部地址后再判断：并发新建时只有第一个能看到「没有默认」，其余拿到锁时
     * 已经有默认了，直接跳过。唯一索引因此不会被撞，也不需要靠重试兜底。
     */
    private void ensureDefault(String buyerId, Address created) {
        List<Address> owned = addresses.findAllWithLockByBuyerId(buyerId);
        boolean hasDefault = owned.stream().anyMatch(Address::isDefault);
        if (!hasDefault) {
            created.markDefault();
            addresses.saveAndFlush(created);
        }
    }

    private Address lockOwned(String buyerId, String addressId) {
        Address address = addresses.findWithLockById(trim(addressId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        if (!address.belongsTo(buyerId)) {
            // 他人地址一律按不存在处理，不给探测空间。
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static AddressData data(Address address) {
        return new AddressData(
                address.getId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getDetailAddress(),
                address.isDefault(),
                UtcIso8601.of(address.getCreateAt()),
                UtcIso8601.of(address.getUpdateAt()));
    }
}
