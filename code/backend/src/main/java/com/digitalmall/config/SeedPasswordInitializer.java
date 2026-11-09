package com.digitalmall.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.entity.SysUser;
import com.digitalmall.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动自检：修复「种子账号密码为 SQL 占位符」导致的静默登录失败。
 *
 * 背景：BCryptPasswordEncoder.matches() 对非法密文格式返回 false 而非抛异常
 * （只打一条 warning），因此占位符未替换时，现象只是"用户名或密码错误"，
 * 排查成本极高，且会连锁导致：admin 登不进 → 商家入驻无法审核 → 商家端全线 403。
 *
 * 行为：仅对下面列出的演示账号生效；若密码不是 BCrypt 格式则重置并告警。
 *      其他账号一律只告警、不修改。
 *
 * ⚠️ 生产环境应删除本类，或加 @Profile("!prod") 限制生效环境。
 *
 * 注意：登录校验使用 BCryptPasswordEncoder.matches()（AuthServiceImpl），
 * 不要改用 BCrypt.checkpw()——后者遇到非法密文（如中文占位符）会抛
 * IllegalArgumentException 被 GlobalExceptionHandler 兜底成 500，
 * 把"静默失败"升级成"每次登录 500"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedPasswordInitializer implements ApplicationRunner {

    /** 演示账号 → 约定明文口令（仅用于修复占位符，非生产用途） */
    private static final Map<String, String> DEMO_ACCOUNTS = Map.of(
            "admin", "admin123",
            "merchant1", "merchant123"
    );

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        List<SysUser> seeds = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getUsername, DEMO_ACCOUNTS.keySet()));
        if (seeds.isEmpty()) {
            log.warn("【启动自检】未找到种子账号 admin / merchant1，请确认已执行建表 SQL 与 测试商品数据.sql");
            return;
        }

        int fixed = 0;
        for (SysUser user : seeds) {
            if (isBcrypt(user.getPassword())) {
                continue;
            }
            String raw = DEMO_ACCOUNTS.get(user.getUsername());
            user.setPassword(encoder.encode(raw));
            sysUserMapper.updateById(user);
            fixed++;
            log.warn("【启动自检】账号 {} 的密码不是 BCrypt 密文（多为 SQL 占位符未替换），"
                    + "已自动重置为 {}，请登录后立即修改", user.getUsername(), raw);
        }
        if (fixed > 0) {
            log.warn("==================================================================");
            log.warn("【启动自检】共修复 {} 个种子账号密码。如为首次启动，请忽略本告警。", fixed);
            log.warn("==================================================================");
        }

        // 其他账号：非 BCrypt 格式只告警，不擅自修改
        Long abnormal = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .notIn(SysUser::getUsername, DEMO_ACCOUNTS.keySet())
                .and(w -> w.isNotNull(SysUser::getPassword).ne(SysUser::getPassword, ""))
                .notLikeRight(SysUser::getPassword, "$2a$")
                .notLikeRight(SysUser::getPassword, "$2b$")
                .notLikeRight(SysUser::getPassword, "$2y$"));
        if (abnormal != null && abnormal > 0) {
            log.warn("【启动自检】检测到 {} 个非种子账号的密码不是 BCrypt 密文，请人工核查（未自动修改）", abnormal);
        }
    }

    private boolean isBcrypt(String password) {
        return password != null
                && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}
