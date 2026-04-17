-- distributed_semaphore_release.lua
-- KEYS[1] = semaphore key
-- Returns: current count after release

local key = KEYS[1]
local current = tonumber(redis.call('GET', key) or '0')

if current > 0 then
    return redis.call('DECR', key)
else
    return 0
end
