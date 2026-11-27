package com.simpleshop.order.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.address.api.AddressPort;
import com.simpleshop.address.api.AddressSnapshot;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.security.RateLimiter;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSalePort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.inventory.api.StockPort;
import com.simpleshop.inventory.api.StockShortage;
import com.simpleshop.order.dto.OrderPaymentData;
import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.PayOrderRequest;
import com.simpleshop.order.dto.PlaceOrderItemRequest;
import com.simpleshop.order.dto.PlaceOrderRequest;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.entity.ReceiverSnapshot;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.BuyerOrderService;
import com.simpleshop.order.service.OrderAssembler;
import com.simpleshop.order.support.OrderNoGenerator;
import com.simpleshop.order.support.OrderRefund;
import com.simpleshop.order.support.PaymentDeadline;
import com.simpleshop.payment.api.PaymentChannel;
import com.simpleshop.payment.api.PaymentPort;
import com.simpleshop.payment.api.PaymentRequest;
import com.simpleshop.payment.api.PaymentResult;

/**
 * 下单：先锁商品行再校验，避免「校验通过后商品被下架」。
 *
 * <p>事务顺序固定为：取地址快照 → 校验明细形状 → 按 ID 升序锁商品行 → 校验在售 → 读库存 →
 * 取下单快照 → 算总额并落库。库存只读不锁：没有预占，真正的扣减发生在买家确认收货时。
 *
 * <p>拒绝口径：任一明细不满足就整单拒绝、不写任何数据，并按「为什么」返回已有业务码——
 * 商品不存在 20011、商品已下架 20019、库存不足 20015。订单域自己的状态类错误码
 * （订单不存在、状态不允许、不可取消、完成缺货）留给后续状态迁移使用。
 *
 * <p>同一订单内重复商品按参数错误拒绝而不是合并：正常入口是购物车勾选，一个商品只会
 * 提交一行；出现两行同商品说明调用方把明细拼错了，属于调用错误而不是业务分支。
 */
@Service
public class BuyerOrderServiceImpl implements BuyerOrderService {

    private final OrderRepository orders;
    private final GoodsQueryPort goodsQuery;
    private final StockPort stockPort;
    private final GoodsSalePort goodsSale;
    private final AddressPort addresses;
    private final PaymentPort payments;
    private final PaymentDeadline deadlines;
    private final OrderRefund refunds;
    private final OrderNoGenerator orderNumbers;
    private final OperationLogService audit;
    private final RateLimiter rateLimiter;
    private final OrderAssembler assembler;

    public BuyerOrderServiceImpl(
            OrderRepository orders,
            GoodsQueryPort goodsQuery,
            StockPort stockPort,
            GoodsSalePort goodsSale,
            AddressPort addresses,
            PaymentPort payments,
            PaymentDeadline deadlines,
            OrderRefund refunds,
            OrderNoGenerator orderNumbers,
            OperationLogService audit,
            RateLimiter rateLimiter,
            OrderAssembler assembler) {
        this.orders = orders;
        this.goodsQuery = goodsQuery;
        this.stockPort = stockPort;
        this.goodsSale = goodsSale;
        this.addresses = addresses;
        this.payments = payments;
        this.deadlines = deadlines;
        this.refunds = refunds;
        this.orderNumbers = orderNumbers;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.assembler = assembler;
    }

    @Transactional
    public OrderPlacedData place(String buyerId, PlaceOrderRequest request) {
        // 限流是保护措施，不是业务规则：只给一个很高的兜底阈值，按买家计。
        if (!rateLimiter.tryAcquire(RateLimiter.Scope.ORDER, buyerId)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        List<PlaceOrderItemRequest> requested = request == null || request.items() == null
                ? List.of()
                : request.items();
        requireWellFormed(requested);

        ReceiverSnapshot receiver = resolveReceiver(buyerId, request.addressId());
        List<OrderLine> lines = resolveLines(requested);
        Order order = orders.saveAndFlush(
                Order.place(orderNumbers.next(), buyerId, receiver, lines));
        audit.log(OperationLogService.CREATE_ORDER,
                OperationLogService.TARGET_ORDER, order.getId());
        return assembler.placed(order);
    }

    /**
     * 支付：先锁订单行，再校验状态与时限，最后才调支付包。
     *
     * <p>顺序不能反：先锁再判断，重复提交的第二个请求会看到已支付；先调渠道再判断，
     * 就可能对同一笔订单扣两次钱。超时判断放在调用渠道之前——已经过期的订单不该再试。
     *
     * <p>失败只回错误码，订单字段一个都不写：`payment_channel` 与 `paid_at` 必须同生同灭。
     */
    @Transactional
    public OrderPaymentData pay(String buyerId, String orderId, PayOrderRequest request) {
        PaymentChannel channel = parseChannel(request == null ? null : request.paymentChannel());
        Order order = orders.findWithLockById(orderId == null ? "" : orderId.trim())
                .filter(candidate -> candidate.belongsTo(buyerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.canPay()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_ALLOWED,
                    "order " + order.getOrderNo() + " is not pending payment");
        }
        if (deadlines.isExpired(order)) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_ALLOWED,
                    "payment window of order " + order.getOrderNo() + " has expired");
        }

        PaymentResult result = payments.charge(new PaymentRequest(
                order.getId(), order.getOrderNo(), order.getTotalAmount(), channel));

        return switch (result.status()) {
            case SUCCEEDED -> {
                order.pay(result.channel(), result.paidAt());
                orders.saveAndFlush(order);
                audit.log(OperationLogService.ORDER_PAY,
                        OperationLogService.TARGET_ORDER, order.getId());
                yield assembler.payment(order, null);
            }
            // 真实渠道重定向：订单不动，把跳转地址交给前端；本期 Mock 不会走到这里。
            case PENDING -> assembler.payment(order, result.redirectUrl());
            case FAILED -> throw new BusinessException(ErrorCode.PAYMENT_FAILED,
                    "payment failed for order " + order.getOrderNo() + ", please retry");
        };
    }

    /** 渠道用字符串收再转枚举：非法值落到 83001，而不是变成参数格式错误。 */
    private static PaymentChannel parseChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_INVALID);
        }
        try {
            return PaymentChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_INVALID);
        }
    }

    /**
     * 确认收货：成交，订单进入交易完成。这是唯一的完成入口。
     *
     * <p>库存只在这一刻变化：在售商品按明细数量扣减，已下架商品跳过——商品下架后仍要允许
     * 买家把货收下，库存账因此可能不反映这笔成交，这是需求确定的唯一例外。
     *
     * <p>锁顺序固定：订单行 → 商品行（ID 升序）→ 库存行（ID 升序），与下单、卖家改库存一致。
     * 先锁订单再判断，重复确认的第二个请求会看到已交易完成，不会重复扣库存。
     */
    @Transactional
    public OrderStatusData confirmReceipt(String buyerId, String orderId) {
        Order order = orders.findWithLockById(orderId == null ? "" : orderId.trim())
                .filter(candidate -> candidate.belongsTo(buyerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.canConfirmReceipt()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not shipped");
        }

        Map<String, Integer> required = requiredQuantities(order);
        Map<String, GoodsSnapshot> snapshots = goodsQuery.lockForOrder(required.keySet()).stream()
                .collect(Collectors.toMap(GoodsSnapshot::id, Function.identity()));

        Map<String, Integer> toDeduct = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> line : required.entrySet()) {
            GoodsSnapshot snapshot = snapshots.get(line.getKey());
            if (snapshot == null) {
                throw new BusinessException(ErrorCode.GOODS_NOT_FOUND,
                        "goods not found: " + line.getKey());
            }
            if (snapshot.status() == GoodsStatus.ON_SALE) {
                toDeduct.put(line.getKey(), line.getValue());
            }
        }

        if (!toDeduct.isEmpty()) {
            List<StockShortage> shortages = stockPort.deductAll(toDeduct);
            if (!shortages.isEmpty()) {
                // 整单拒绝：一行都没扣，订单仍停在已发货，卖家补货后买家可以再确认。
                throw new BusinessException(
                        ErrorCode.ORDER_STOCK_SHORTAGE, shortageMessage(shortages));
            }
            goodsSale.markSoldOut(toDeduct.keySet());
        }

        order.confirmReceipt();
        orders.saveAndFlush(order);
        audit.log(OperationLogService.ORDER_CONFIRM_RECEIPT,
                OperationLogService.TARGET_ORDER, order.getId());
        return assembler.status(order);
    }

    @Transactional
    public OrderStatusData cancel(String buyerId, String orderId) {
        // 先按归属过滤再判断状态：别人的订单一律当作不存在，避免探测订单是否存在。
        Order order = orders.findWithLockById(orderId)
                .filter(candidate -> candidate.belongsTo(buyerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 重复取消按成功返回，只是为了防抖：按钮连点或网络重试不该报错。
        if (order.getStatus() == OrderStatus.CANCELED) {
            return assembler.status(order);
        }
        if (!order.canCancelByBuyer()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE,
                    "order " + order.getOrderNo() + " cannot be canceled by the buyer in status "
                            + order.getStatus());
        }
        // 先退款再改状态：退款失败时订单原样不动，买家还能重试。
        refunds.refundIfPaid(order);
        order.cancelByBuyer();
        return persistCancellation(order);
    }

    /**
     * 收货地址必须是买家自己地址簿里的条目。
     *
     * <p>「不存在」与「别人的地址」都按同一个业务码拒绝，避免用地址 ID 探测他人数据；
     * 拿不到地址就不许下单，而不是允许空地址——没有收货人就没有履约对象。
     */
    private ReceiverSnapshot resolveReceiver(String buyerId, String addressId) {
        AddressSnapshot address = addresses.findOwned(buyerId, addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_REQUIRED));
        return new ReceiverSnapshot(
                address.receiverName(), address.receiverPhone(), address.detailAddress());
    }

    /** 按商品合并数量：同一订单内不允许同商品两行，合并只是为了口径统一。 */
    private static Map<String, Integer> requiredQuantities(Order order) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            required.merge(item.getGoodsId(), item.getQuantity(), Integer::sum);
        }
        return required;
    }

    /** 只报商品与数量缺口，不带名称、单价等完整订单项信息。 */
    private static String shortageMessage(List<StockShortage> shortages) {
        return "insufficient stock: " + shortages.stream()
                .map(shortage -> shortage.goodsId() + " needs " + shortage.required()
                        + ", has " + shortage.available())
                .collect(Collectors.joining("; "));
    }

    private OrderStatusData persistCancellation(Order order) {
        orders.saveAndFlush(order);
        audit.log(OperationLogService.ORDER_CANCEL,
                OperationLogService.TARGET_ORDER, order.getId());
        return assembler.status(order);
    }

    /**
     * 形状校验：空明细、重复商品、数量非法都在碰数据库之前拒绝。
     *
     * <p>数量小于 1 本来就会被请求校验拦下，这里再挡一次，是为了让内部调用
     * 也拿不到非法明细。
     */
    private void requireWellFormed(List<PlaceOrderItemRequest> requested) {
        if (requested.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID, "an order must contain at least one item");
        }

        Set<String> seen = new HashSet<>();
        for (PlaceOrderItemRequest item : requested) {
            if (item == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "order item must not be null");
            }
            String goodsId = item.goodsId() == null ? "" : item.goodsId().trim();
            if (!seen.add(goodsId)) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID, "duplicate goods in one order: " + goodsId);
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID, "quantity must be at least 1 for goods " + goodsId);
            }
        }
    }

    /** 锁商品行、校验在售与库存，并把通过的明细转成下单快照。 */
    private List<OrderLine> resolveLines(List<PlaceOrderItemRequest> requested) {
        List<String> goodsIds = requested.stream()
                .map(item -> item.goodsId().trim())
                .toList();
        Map<String, GoodsSnapshot> snapshots = goodsQuery.lockForOrder(goodsIds).stream()
                .collect(Collectors.toMap(GoodsSnapshot::id, Function.identity()));

        Map<String, Integer> stock = stockPort.availableAll(snapshots.keySet());

        List<OrderLine> lines = new ArrayList<>(requested.size());
        for (PlaceOrderItemRequest item : requested) {
            String goodsId = item.goodsId().trim();
            GoodsSnapshot snapshot = snapshots.get(goodsId);
            if (snapshot == null) {
                throw new BusinessException(
                        ErrorCode.GOODS_NOT_FOUND, "goods not found: " + goodsId);
            }
            if (snapshot.status() != GoodsStatus.ON_SALE) {
                throw new BusinessException(
                        ErrorCode.GOODS_NOT_ON_SALE, "goods is not on sale: " + goodsId);
            }
            if (item.quantity() > stock.getOrDefault(goodsId, 0)) {
                throw new BusinessException(
                        ErrorCode.STOCK_INVALID, "insufficient stock for goods " + goodsId);
            }
            lines.add(new OrderLine(goodsId, snapshot.name(), snapshot.price(), item.quantity()));
        }
        return lines;
    }
}
