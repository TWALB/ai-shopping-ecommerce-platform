package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价表（comment 为敏感词，表名加反引号）
 */
@Data
@TableName("`comment`")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long orderItemId;

    private Long productId;

    private Long userId;

    /** 商品评分 1-5 */
    private Integer productScore;

    /** 物流评分 1-5 */
    private Integer logisticsScore;

    private String content;

    private String imageUrls;

    /** 0否 1是 */
    private Integer isAnonymous;

    /** 0待审核 1已展示 2违规删除 */
    private Integer status;

    private String replyContent;

    private LocalDateTime replyTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
