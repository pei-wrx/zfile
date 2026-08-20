package com.pei.zfile.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class IpUtil {

    /**
     * 获取当前请求的客户端 IP。
     * 必须在主线程（同步）中调用，不能在 @Async 方法里用。
     * 无请求上下文时（如定时任务、MQ 消费等）返回 null。
     */
    public static String getClientIp() {
        // 从 ThreadLocal 里拿当前请求的属性
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;   // 没有请求上下文（定时任务等场景）
        }
        HttpServletRequest request = attrs.getRequest();

        // 优先取代理转发的真实 IP（如果项目前面挂了 Nginx/网关）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // 多个代理会拼成 "a,b,c"，取第一个就是客户端真实 IP
            int comma = ip.indexOf(',');
            return (comma > 0) ? ip.substring(0, comma).trim() : ip.trim();
        }
        // 没有代理头就直接取 Socket 对端地址
        return request.getRemoteAddr();
    }
}