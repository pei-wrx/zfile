package com.pei.zfile.common.util;

public class RedisConstant {
    public static final String REFRESH_TOKEN_KEY = "login:refreshToken:";
    public static final Long REFRESH_TOKEN_TTL = 7L;

    public static final String ACCESS_TOKEN_KEY = "login:accessToken:";
    public static final Long ACCESS_TOKEN_TTL = 30L;

    public static final String TOKEN_BLACKLIST_KEY = "token:blacklist:";

    public static final String SHARE_TOKEN_KEY = "token:shareToken:";
    public static final Long SHARE_TOKEN_TTL = 1800L;

}