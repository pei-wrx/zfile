-- 限流的原子操作
local current = redis.call('incr', KEYS[1])   -- 对键执行自增，返回递增后的值
if current == 1 then
    -- 第一次递增时设置过期时间，ARGV[1]为过期时长
    redis.call('pexpire', KEYS[1], ARGV[1])
end
return current  -- 返回当前计数值