if (redis.call("get",KEYS[1]) == ARGV[1]) then
    redis.call('psetex', KEYS[1], ARGV[3], ARGV[2])
    return 1
else
    return 0
end

-- 刷新JWT
-- KEYS[1] = 保存在redis里的key
-- ARGV[1] = 旧的refresh token
-- ARGV[2] = 新的refresh token
-- ARGV[3] = 过期时间
