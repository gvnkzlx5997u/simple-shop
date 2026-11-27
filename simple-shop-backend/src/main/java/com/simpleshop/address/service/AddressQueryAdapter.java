package com.simpleshop.address.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.address.api.AddressPort;
import com.simpleshop.address.api.AddressSnapshot;
import com.simpleshop.address.repository.AddressRepository;

/**
 * 地址只读端口实现：给下单提供收货快照。
 *
 * <p>不加锁：下单只读一次地址，读到的就是当时那一份；地址随后被改被删都不影响订单，
 * 因为订单存的是快照而不是引用。
 */
@Component
public class AddressQueryAdapter implements AddressPort {

    private final AddressRepository addresses;

    public AddressQueryAdapter(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AddressSnapshot> findOwned(String buyerId, String addressId) {
        if (buyerId == null || buyerId.isBlank() || addressId == null || addressId.isBlank()) {
            return Optional.empty();
        }
        return addresses.findByIdAndBuyerId(addressId.trim(), buyerId.trim())
                .map(address -> new AddressSnapshot(
                        address.getId(),
                        address.getReceiverName(),
                        address.getReceiverPhone(),
                        address.getDetailAddress()));
    }
}
