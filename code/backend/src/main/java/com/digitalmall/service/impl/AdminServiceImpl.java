package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.NoticeRequest;
import com.digitalmall.entity.*;
import com.digitalmall.mapper.*;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.AdminService;
import com.digitalmall.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理员服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SysUserMapper sysUserMapper;
    private final ShopMapper shopMapper;
    private final MerchantApplyMapper merchantApplyMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final AfterSaleMapper afterSaleMapper;
    private final NoticeMapper noticeMapper;
    private final OperationLogMapper operationLogMapper;
    private final AdminStatsMapper adminStatsMapper;

    private Long adminId() {
        return UserContext.getUserId();
    }

    // ==================== 用户管理 ====================

    @Override
    public PageResult<UserAdminVO> userPage(String keyword, Integer role, Integer status, long pageNum, long pageSize) {
        Page<UserAdminVO> page = new Page<>(pageNum, pageSize);
        IPage<UserAdminVO> ip = sysUserMapper.selectAdminUserPage(page, keyword, role, status);
        return PageResult.of(ip);
    }

    @Override
    public void userStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态仅支持 0禁用 / 1正常");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (user.getRole() == 2 && user.getId().equals(adminId())) {
            throw new BusinessException(400, "不能禁用自己的账号");
        }
        user.setStatus(status);
        sysUserMapper.updateById(user);
        // 禁用时冻结其店铺
        if (status == 0) {
            Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, id));
            if (shop != null && shop.getStatus() == 1) {
                shop.setStatus(0);
                shopMapper.updateById(shop);
            }
        }
        log("用户", (status == 0 ? "禁用" : "启用") + "用户 " + user.getUsername() + "(ID=" + id + ")");
    }

    @Override
    public void userRole(Long id, Integer role) {
        if (role == null || (role != 0 && role != 1 && role != 2)) {
            throw new BusinessException(400, "角色仅支持 0用户 / 1商家 / 2管理员");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (user.getId().equals(adminId())) {
            throw new BusinessException(400, "不能调整自己的角色");
        }
        user.setRole(role);
        sysUserMapper.updateById(user);
        log("用户", "调整用户 " + user.getUsername() + "(ID=" + id + ") 角色为 " + role);
    }

    // ==================== 入驻审核 ====================

    @Override
    public PageResult<MerchantApplyAdminVO> applyPage(Integer status, long pageNum, long pageSize) {
        Page<MerchantApplyAdminVO> page = new Page<>(pageNum, pageSize);
        IPage<MerchantApplyAdminVO> ip = merchantApplyMapper.selectApplyPage(page, status);
        return PageResult.of(ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(Long id, Boolean agree, String rejectReason) {
        if (agree == null) {
            throw new BusinessException(400, "请指定审核结果");
        }
        MerchantApply apply = merchantApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException(404, "入驻申请不存在");
        }
        if (apply.getStatus() != 0) {
            throw new BusinessException(409, "该申请已审核过");
        }
        apply.setAuditBy(adminId());
        apply.setAuditTime(LocalDateTime.now());
        if (agree) {
            apply.setStatus(1);
            merchantApplyMapper.updateById(apply);
            // 通过：创建店铺 + 用户 role=1
            Shop shop = new Shop();
            shop.setMerchantId(apply.getUserId());
            shop.setShopName(apply.getShopName());
            shop.setShopIntro(apply.getShopIntro());
            shop.setStatus(1);
            shopMapper.insert(shop);
            SysUser user = sysUserMapper.selectById(apply.getUserId());
            if (user != null && user.getRole() != 2) {
                user.setRole(1);
                sysUserMapper.updateById(user);
            }
            log("审核", "通过商家入驻申请 ID=" + id + "（" + apply.getShopName() + "），已开通店铺");
        } else {
            if (rejectReason == null || rejectReason.isBlank()) {
                throw new BusinessException(400, "拒绝时请填写拒绝原因");
            }
            apply.setStatus(2);
            apply.setRejectReason(rejectReason);
            merchantApplyMapper.updateById(apply);
            log("审核", "拒绝商家入驻申请 ID=" + id + "（" + apply.getShopName() + "）");
        }
    }

    // ==================== 商品管控 ====================

    @Override
    public PageResult<AdminProductVO> productPage(Integer status, long pageNum, long pageSize) {
        Page<AdminProductVO> page = new Page<>(pageNum, pageSize);
        IPage<AdminProductVO> ip = productMapper.selectAdminProductPage(page, status);
        return PageResult.of(ip);
    }

    @Override
    public void productStatus(Long id, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(400, "状态仅支持 1上架 / 2下架");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        product.setStatus(status);
        if (status == 1) {
            product.setShelfTime(LocalDateTime.now());
        }
        productMapper.updateById(product);
        log("商品", (status == 1 ? "上架" : "下架") + "商品 " + product.getProductName() + "(ID=" + id + ")");
    }

    // ==================== 订单 / 售后 ====================

    @Override
    public PageResult<com.digitalmall.vo.OrderVO> orderPage(Integer status, String orderNo, long pageNum, long pageSize) {
        Page<com.digitalmall.vo.OrderVO> page = new Page<>(pageNum, pageSize);
        IPage<com.digitalmall.vo.OrderVO> ip = orderMapper.selectAdminOrderPage(page, status, orderNo);
        return PageResult.of(ip);
    }

    @Override
    public PageResult<AfterSaleAdminVO> afterSalePage(Integer status, long pageNum, long pageSize) {
        Page<AfterSaleAdminVO> page = new Page<>(pageNum, pageSize);
        IPage<AfterSaleAdminVO> ip = afterSaleMapper.selectAfterSalePage(page, status);
        return PageResult.of(ip);
    }

    // ==================== 公告 ====================

    @Override
    public void addNotice(NoticeRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(400, "请填写公告标题");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException(400, "请填写公告内容");
        }
        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType() == null ? 0 : request.getType());
        notice.setStatus(1);
        notice.setPublishTime(LocalDateTime.now());
        notice.setPublisherId(adminId());
        noticeMapper.insert(notice);
        log("公告", "发布公告「" + request.getTitle() + "」");
    }

    @Override
    public PageResult<NoticeVO> noticePage(Integer status, long pageNum, long pageSize) {
        Page<NoticeVO> page = new Page<>(pageNum, pageSize);
        IPage<NoticeVO> ip = noticeMapper.selectNoticePage(page, status);
        return PageResult.of(ip);
    }

    @Override
    public void deleteNotice(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        noticeMapper.deleteById(id);
        log("公告", "删除公告「" + notice.getTitle() + "」(ID=" + id + ")");
    }

    // ==================== 统计 ====================

    @Override
    public StatsOverviewVO statsOverview() {
        Map<String, Object> counts = adminStatsMapper.overviewCounts();
        Map<String, Object> today = adminStatsMapper.todayStats(LocalDate.now());
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setUserCount(((Number) counts.get("user_count")).longValue());
        vo.setMerchantCount(((Number) counts.get("merchant_count")).longValue());
        vo.setProductCount(((Number) counts.get("product_count")).longValue());
        vo.setOrderCount(((Number) counts.get("order_count")).longValue());
        vo.setTotalAmount(adminStatsMapper.totalAmount());
        vo.setTodayOrderCount(((Number) today.get("order_count")).longValue());
        vo.setTodayAmount((BigDecimal) today.get("amount"));
        return vo;
    }

    @Override
    public List<TrendPointVO> statsTrend(Integer days) {
        int d = (days == null || (days != 7 && days != 30)) ? 7 : days;
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(d - 1L);
        List<Map<String, Object>> rows = adminStatsMapper.trend(start);

        // 补齐缺失日期（无交易的日期补 0）
        Map<LocalDate, Map<String, Object>> byDate = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(LocalDate.parse((String) row.get("date")), row);
        }
        List<TrendPointVO> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            TrendPointVO point = new TrendPointVO();
            point.setDate(date);
            Map<String, Object> row = byDate.get(date);
            if (row != null) {
                point.setOrderCount(((Number) row.get("order_count")).longValue());
                point.setAmount((BigDecimal) row.get("amount"));
            } else {
                point.setOrderCount(0L);
                point.setAmount(BigDecimal.ZERO);
            }
            result.add(point);
        }
        return result;
    }

    // ==================== 操作日志 ====================

    @Override
    public PageResult<OperationLogVO> operationLogPage(String module, String username, long pageNum, long pageSize) {
        Page<OperationLogVO> page = new Page<>(pageNum, pageSize);
        IPage<OperationLogVO> ip = operationLogMapper.selectLogPage(page, module, username);
        return PageResult.of(ip);
    }

    /** 写入操作日志 */
    private void log(String module, String action) {
        try {
            OperationLog log = new OperationLog();
            log.setUserId(adminId());
            log.setModule(module);
            log.setAction(action);
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                log.setIp(attrs.getRequest().getRemoteAddr());
            }
            operationLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不影响主流程
        }
    }
}
