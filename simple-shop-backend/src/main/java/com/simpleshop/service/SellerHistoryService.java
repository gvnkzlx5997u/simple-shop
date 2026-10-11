package com.simpleshop.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.persistence.entity.GoodsHistory;
import com.simpleshop.persistence.entity.IntentionHistory;
import com.simpleshop.persistence.repository.GoodsHistoryRepository;
import com.simpleshop.persistence.repository.IntentionHistoryRepository;
import com.simpleshop.persistence.repository.TradeHistoryRepository;
import com.simpleshop.service.dto.HistoryGoodsDetailData;
import com.simpleshop.service.dto.HistoryGoodsItemData;
import com.simpleshop.service.dto.HistoryIntentionData;
import com.simpleshop.service.dto.HistoryPageData;
import com.simpleshop.service.dto.TradeData;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;

/**
 * 卖家端历史商品服务（{@code FR-11}）。方案 §3.3.4。
 *
 * <pre>
 * HistoryPageData        listHistory(page, pageSize);      // FR-11 → I11-15
 * HistoryGoodsDetailData getHistoryDetail(goodsHistoryId); // FR-11 → I11-16
 * </pre>
 *
 * <h2>本类只读——一个 {@code if} 之外没有任何状态变更</h2>
 * <p>两个方法都是 {@code @Transactional(readOnly = true)}：历史表<b>只追加、从不修改</b>
 * （归档是唯一写入方，见 {@link ArchiveService}）。因此这里没有加锁矩阵、没有事务超时设置。
 *
 * <h2>⚠️ 两条相反的排序口径，都在这里出现</h2>
 * <table border="1">
 *   <caption>两个接口的排序</caption>
 *   <tr><th>接口</th><th>排序</th><th>怎么实现</th></tr>
 *   <tr><td>{@code I11-15} 历史列表</td><td>{@code trade_end} <b>倒序</b></td>
 *       <td><b>仓储方法名内建</b>（{@code findAllByOrderByTradeEndDesc}）——⚠️ 调用方<b>不得</b>再传
 *           {@code Sort}，传了会覆盖掉方法名里的排序</td></tr>
 *   <tr><td>{@code I11-16} 意向名单</td><td>{@code create_at} <b>升序</b></td>
 *       <td><b>同样是方法名内建</b>（{@code findByGoodsHistoryIdOrderByCreateAtAsc}）</td></tr>
 * </table>
 * <p>这两个仓储方法都不接受 {@code Sort}/{@code Pageable} 的排序参数，
 * 所以「传错排序」在本类里不容易发生；真正容易写错的是 {@code I11-10}
 * （{@code findByGoodsId} <b>必须</b>显式传排序，见 {@code SellerIntentionService}）。
 *
 * <h2>⚠️ 已知的 N+1 点（不是缺陷，是登记过的取舍）</h2>
 * <p>{@link #getHistoryDetail} 对每条历史意向各查一次流水
 * （{@code TradeHistoryRepository} 只提供按<b>单个</b> {@code intentionId} 的查询）——
 * 即 <b>N 次 SQL</b>。方案 §3.3.4 已登记该取舍：历史商品都是「小规模经营」留下的，
 * 每件商品的意向数与失败次数通常是个位数，避免了改动数据层交付物（附录 B）。
 * <p>方案留的两条后路同样保留：① 代码里点明这个 N+1（本段）；
 * ② <b>在 §8.4 的性能用例里实测</b>（属 S10）——若 P95 超 500ms，
 * 再在数据层<b>新增</b>一个批量查询方法（新增只读查询方法不违反附录 B，只需评审时说明理由）。
 */
@Service
public class SellerHistoryService {

    /** 分页默认值与上限（{@code 10-E}、§3.6）；与 {@code SellerIntentionService} 同一口径。 */
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 100;

    private final GoodsHistoryRepository goodsHistoryRepository;
    private final IntentionHistoryRepository intentionHistoryRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    public SellerHistoryService(GoodsHistoryRepository goodsHistoryRepository,
                                IntentionHistoryRepository intentionHistoryRepository,
                                TradeHistoryRepository tradeHistoryRepository) {
        this.goodsHistoryRepository = goodsHistoryRepository;
        this.intentionHistoryRepository = intentionHistoryRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
    }

    // -------------------------------------------------------------------------
    // FR-11 历史商品列表（I11-15）
    // -------------------------------------------------------------------------

    /**
     * 历史商品列表（分页，按交易结束时间<b>倒序</b>）。
     *
     * <h2>⚠️ 这个方法<b>没有</b>「无数据」的特殊返回值</h2>
     * <p>与 {@code I11-04}／{@code I11-10} 不同：历史表为空是<b>正常初始状态</b>（还没卖过东西），
     * 返回 {@code total = 0、items = []} 即可，前端走「暂无历史」空态。
     * 这里<b>不</b>返回 {@code null}——{@code data} 为 {@code null} 在本项目里一直是
     * 「当前无商品」的专用表达（{@code I11-04}／{@code I11-10}），混用会让前端多一个分支。
     *
     * <h2>⚠️ 不支持任何筛选（{@code FR-11}）</h2>
     * <p>契约只给了 {@code page}／{@code page_size} 两个入参。加筛选（按结果、按时间范围）
     * 属于发明需求。
     *
     * @param page     页码（<b>从 1 起</b>）
     * @param pageSize 每页条数（1~100，默认 10）
     * @return 分页结果；空表时 {@code total = 0、items = []}
     * @throws BusinessException {@code 50002}——分页参数越界
     */
    @Transactional(readOnly = true)
    public HistoryPageData listHistory(int page, int pageSize) {
        validatePaging(page, pageSize);

        // ⚠️ 不传 Sort：排序已内建在方法名里，传了会覆盖它（见类注释的两条相反口径）
        Page<GoodsHistory> found = goodsHistoryRepository.findAllByOrderByTradeEndDesc(
                PageRequest.of(page - 1, pageSize));

        return new HistoryPageData(
                found.getTotalElements(),
                page,
                pageSize,
                found.getContent().stream().map(HistoryGoodsItemData::from).toList());
    }

    // -------------------------------------------------------------------------
    // FR-11 历史商品详情（I11-16）
    // -------------------------------------------------------------------------

    /**
     * 历史商品详情：商品 9 字段 + 意向名单（每条含 {@code trades[]}）。
     *
     * <h2>{@code 20011} 是 HTTP <b>404</b>，且归<b>商品域</b></h2>
     * <p>{@code §11.6.1} 明确「历史商品不存在」用 {@code 20011}（{@code 2xxxx}），
     * <b>不得</b>越域占用 {@code 4xxxx}（那是口令码域）。它是全项目<b>唯一</b>一个
     * 「资源不存在」语义的 404（除「路径根本不存在」）。
     * <p>⚠️ 这也是本类唯一一个「可能失败」的分支——{@code I11-16} 是唯一带路径参数的接口，
     * 客户端可以传任意 ID。
     *
     * <h2>⚠️ {@code intentions[]} 必须含 {@code trades[]}</h2>
     * <p>澄清 Q15／{@code BR-22}：只返回最终结果不满足业务方要求——「失败过两次最后成交」的意向
     * 必须能逐次看到每一次失败。{@code trade_count} 直接取 {@code trades.size()}（见
     * {@link HistoryIntentionData#of}）。
     *
     * @param goodsHistoryId 历史商品 ID（= 原商品 ID）
     * @return 详情
     * @throws BusinessException {@code 20011}（HTTP 404）——该历史商品不存在
     */
    @Transactional(readOnly = true)
    public HistoryGoodsDetailData getHistoryDetail(String goodsHistoryId) {
        GoodsHistory goods = goodsHistoryRepository.findById(goodsHistoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HISTORY_GOODS_NOT_FOUND));

        List<IntentionHistory> intentions =
                intentionHistoryRepository.findByGoodsHistoryIdOrderByCreateAtAsc(goodsHistoryId);

        // ⚠️ 这里的 N+1：每条意向一次查询（见类注释的「已知的 N+1 点」）。
        //    不用 countGroupedByIntentionIdIn 拼 trade_count——那样反而要多一次查询，
        //    且它给不出每一次的时间与失败类型。
        List<HistoryIntentionData> items = new ArrayList<>(intentions.size());
        for (IntentionHistory intention : intentions) {
            List<TradeData> trades = tradeHistoryRepository
                    .findByIntentionIdOrderByTradeStartAsc(intention.getId())
                    .stream()
                    .map(TradeData::from)
                    .toList();
            items.add(HistoryIntentionData.of(intention, trades));
        }

        return HistoryGoodsDetailData.of(goods, items);
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 分页参数校验（{@code §3.6}：{@code page ≥ 1}、{@code 1 ≤ page_size ≤ 100}）。
     *
     * <p>与 {@code SellerIntentionService#validatePaging} 同口径：Controller 上也有 {@code @Min}／
     * {@code @Max}，两处都写是刻意的（Service 也会被直接调用，且两者返回<b>同一个</b> {@code 50002}）。
     */
    private void validatePaging(int page, int pageSize) {
        if (page < DEFAULT_PAGE || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }
}
