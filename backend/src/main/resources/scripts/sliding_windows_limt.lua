---
--- 滑动窗口限流器
--- DateTime: 2026/11/3 13:53
---KEY[1]: 限流器名称
---ARGV[1]: 限流窗口大小
---ARGV[2]: 限流窗口最大请求数
---ARGV[3]: 当前时间戳
---ARGV[4]: 本次请求的唯一标识

local key = KEYS[1]
local window_ms = tonumber(ARGV[1])
local max_count = tonumber(ARGV[2])
local now_ms    = tonumber(ARGV[3])
local member    = ARGV[4]

local window_start     = now_ms - window_ms
-- 清理窗口外的过期请求（score < window_start）
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
-- 统计当前窗口内请求数量
local current_count = redis.call('ZCARD', key)
if current_count <= max_count then
    redis.call('ZADD',key,now_ms,member)
    redis.call('PEXPIRE', key, window_ms + 1000)
    return 1
else
    return 0
end

