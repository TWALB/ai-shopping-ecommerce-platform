package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户管理列表项
 */
@Data
public class UserAdminVO {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    /** 0普通用户 1商家 2管理员 */
    private Integer role;

    /** 0禁用 1正常 */
    private Integer status;

    /** 所属店铺名（商家） */
    private String shopName;

    private LocalDateTime createdTime;
}
