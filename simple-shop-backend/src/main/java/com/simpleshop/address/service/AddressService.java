package com.simpleshop.address.service;

import com.simpleshop.address.dto.AddressData;
import com.simpleshop.address.dto.AddressListData;
import com.simpleshop.address.dto.AddressRequest;

/**
 * 买家地址簿：最多 10 条，每个买家最多一条默认。
 *
 * <p>默认地址的两个来源：第一条自动成为默认；之后只能通过「设为默认」切换，
 * 编辑地址不会顺手改默认——否则用户改个电话就把默认换掉，很难察觉。
 */
public interface AddressService {

    AddressListData list(String buyerId);

    AddressData create(String buyerId, AddressRequest request);

    AddressData update(String buyerId, String addressId, AddressRequest request);

    /** 删除；删的是默认地址时，由剩余里最新创建的一条补位。 */
    void delete(String buyerId, String addressId);

    AddressData setDefault(String buyerId, String addressId);
}
