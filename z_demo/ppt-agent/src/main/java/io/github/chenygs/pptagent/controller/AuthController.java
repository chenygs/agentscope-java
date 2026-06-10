package io.github.chenygs.pptagent.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import io.github.chenygs.pptagent.user.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口 — 登录、登出、获取用户信息
 *
 * <p>Vben Admin 5.x 默认会调用以下接口：
 * <ul>
 *   <li>POST /api/auth/login - 登录</li>
 *   <li>POST /api/auth/logout - 登出</li>
 *   <li>GET  /api/user/info - 获取用户信息</li>
 *   <li>GET  /api/auth/codes - 获取权限码</li>
 *   <li>GET  /api/menu/all - 获取菜单</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 登录请求 DTO */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /** 登录响应 DTO（兼容 Vben 5.x 格式） */
    @Data
    public static class LoginResponse {
        private String accessToken;
        private String[] roles;
        private String realName;
        private String username;
        private Long userId;
    }

    /** 通用响应包装（兼容 Vben 5.x 的 { code, data, message } 格式） */
    @Data
    public static class R<T> {
        private int code;
        private T data;
        private String message;

        public static <T> R<T> ok(T data) {
            R<T> r = new R<>();
            r.code = 0;
            r.data = data;
            r.message = "ok";
            return r;
        }

        public static <T> R<T> error(int code, String msg) {
            R<T> r = new R<>();
            r.code = code;
            r.message = msg;
            return r;
        }
    }

    /** 登录 */
    @PostMapping("/api/auth/login")
    public R<LoginResponse> login(@RequestBody LoginRequest req) {
        try {
            SaTokenInfo info = userService.login(req.getUsername(), req.getPassword());
            Map<String, Object> userInfo = userService.getCurrentUserInfo();

            LoginResponse resp = new LoginResponse();
            resp.setAccessToken(info.getTokenValue());
            resp.setRoles(new String[]{"super"});
            resp.setRealName((String) userInfo.get("realName"));
            resp.setUsername((String) userInfo.get("username"));
            resp.setUserId((Long) userInfo.get("id"));
            return R.ok(resp);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.error(401, e.getMessage());
        }
    }

    /** 登出 */
    @PostMapping("/api/auth/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.ok(null);
    }

    /** 获取当前用户信息 */
    @GetMapping("/api/user/info")
    public R<Map<String, Object>> userInfo() {
        return R.ok(userService.getCurrentUserInfo());
    }

    /** 获取权限码（Vben 5.x 调用） */
    @GetMapping("/api/auth/codes")
    public R<String[]> codes() {
        return R.ok(new String[]{
                "AC_100100", "AC_100110", "AC_100120", "AC_100010"
        });
    }

    /** 获取动态菜单（Vben 5.x 调用） */
    @GetMapping("/api/menu/all")
    public R<Object[]> menuAll() {
        // 简化：返回一个 PPT Agent 主菜单
        Map<String, Object> pptMenu = new HashMap<>();
        pptMenu.put("component", "/ppt-agent/index");
        pptMenu.put("meta", Map.of(
                "icon", "lucide:presentation",
                "title", "PPT 智能体",
                "affixTab", true
        ));
        pptMenu.put("name", "PptAgent");
        pptMenu.put("path", "/ppt-agent");
        return R.ok(new Object[]{pptMenu});
    }

    /** Refresh token (Vben 调用) */
    @PostMapping("/api/auth/refresh")
    public R<Map<String, Object>> refresh() {
        Map<String, Object> data = new HashMap<>();
        data.put("data", StpUtil.getTokenValue());
        data.put("status", 0);
        return R.ok(data);
    }
}