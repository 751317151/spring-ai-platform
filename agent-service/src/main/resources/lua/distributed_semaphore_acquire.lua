-- distributed_semaphore_acquire.lua
-- KEYS[1] = semaphore key (e.g., "ai:semaphore:agent:rd")
-- ARGV[1] = max concurrency
-- ARGV[2] = TTL in seconds (auto-expire safety net)
-- Returns: 1 if acquired, 0 if rejected

local key = KEYS[1]
local maxConcurrency = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', key) or '0')

if current < maxConcurrency then
    redis.call('INCR', key)
    redis.call('EXPIRE', key, ttl)
    return 1
else
    return 0
end
