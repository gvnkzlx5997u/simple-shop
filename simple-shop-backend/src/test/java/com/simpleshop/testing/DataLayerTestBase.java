package com.simpleshop.testing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.address.repository.AddressRepository;
import com.simpleshop.account.repository.BuyerAccountRepository;
import com.simpleshop.account.repository.SellerAccountRepository;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.category.repository.CategoryL1Repository;
import com.simpleshop.category.repository.CategoryL2Repository;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.inventory.entity.GoodsStock;
import com.simpleshop.inventory.repository.GoodsStockRepository;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.entity.ReceiverSnapshot;
import com.simpleshop.order.repository.OrderItemRepository;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.support.OrderNoGenerator;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class DataLayerTestBase {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired protected SellerAccountRepository sellerAccounts;
    @Autowired protected BuyerAccountRepository buyerAccounts;
    @Autowired protected AddressRepository addresses;
    @Autowired protected CategoryL1Repository categoryL1s;
    @Autowired protected CategoryL2Repository categoryL2s;
    @Autowired protected GoodsRepository goodsRepository;
    @Autowired protected GoodsImageRepository goodsImages;
    @Autowired protected GoodsContentBlockRepository contentBlocks;
    @Autowired protected GoodsStockRepository stockItems;
    @Autowired protected OrderRepository orders;
    @Autowired protected OrderItemRepository orderItems;
    @Autowired protected OrderNoGenerator orderNoGenerator;
    @Autowired protected EntityManager entityManager;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected SessionStore sessions;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID();
    }

    protected CategoryL2 createCategory() {
        CategoryL1 l1 = categoryL1s.saveAndFlush(CategoryL1.create(unique("L1-")));
        return categoryL2s.saveAndFlush(CategoryL2.create(l1, unique("L2-")));
    }

    protected BuyerAccount createBuyer() {
        BuyerAccount buyer = BuyerAccount.register(
                unique("buyer-"), PasswordHasher.hash("Buyer@1234"), "13800000000");
        return buyerAccounts.saveAndFlush(buyer);
    }

    /** 下单要选地址：直接落一条，不走还没实现的地址接口。 */
    protected Address createAddress(BuyerAccount buyer) {
        return addresses.saveAndFlush(Address.create(
                buyer.getId(), "张三", "13800000000", "北京市朝阳区某路 1 号", true));
    }

    protected Goods createGoods(CategoryL2 category) {
        Goods goods = goodsRepository.saveAndFlush(
                Goods.publish(unique("商品-"), new BigDecimal("99.99"), category));
        stockItems.saveAndFlush(GoodsStock.initialize(goods.getId(), 3));
        return goods;
    }

    protected void setStock(Goods goods, int available) {
        GoodsStock stock = stockItems.findById(goods.getId())
                .orElseThrow(() -> new IllegalStateException("stock row missing"));
        stock.setAvailable(available);
        stockItems.saveAndFlush(stock);
    }

    /** 直接按领域方法改状态，用于准备「已下架」的夹具。 */
    protected Goods offShelf(Goods goods, OffShelfReason reason) {
        goods.offShelf(reason);
        return goodsRepository.saveAndFlush(goods);
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /** 建一笔待支付订单：单商品、指定数量，用于订单相关测试的夹具。 */
    protected Order createOrder(BuyerAccount buyer, Goods goods, int quantity) {
        Order order = Order.place(
                orderNoGenerator.next(),
                buyer.getId(),
                receiver(),
                List.of(new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), quantity)));
        return orders.saveAndFlush(order);
    }

    /** 订单夹具用的收货快照：订单只认快照，与地址簿无关。 */
    protected ReceiverSnapshot receiver() {
        return new ReceiverSnapshot("张三", "13800000000", "北京市朝阳区某路 1 号");
    }
}
