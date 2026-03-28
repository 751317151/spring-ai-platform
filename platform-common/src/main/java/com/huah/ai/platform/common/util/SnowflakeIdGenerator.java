package com.huah.ai.platform.common.util;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

@Component
public class SnowflakeIdGenerator {

    private static final long CUSTOM_EPOCH = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;
    private final SecureRandom secureRandom = new SecureRandom();

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this.datacenterId = resolveDatacenterId();
        this.workerId = resolveWorkerId(datacenterId);
    }

    public synchronized long nextLongId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for "
                    + (lastTimestamp - timestamp) + " ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    public String nextId() {
        return String.valueOf(nextLongId());
    }

    private long waitUntilNextMillis(long previousTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= previousTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private long resolveDatacenterId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null && mac.length >= 2) {
                    long value = ((long) (mac[mac.length - 2] & 0xFF) << 8) | (mac[mac.length - 1] & 0xFFL);
                    return (value >> 6) % (MAX_DATACENTER_ID + 1);
                }
            }
        } catch (Exception ignored) {
        }
        return secureRandom.nextInt((int) MAX_DATACENTER_ID + 1);
    }

    private long resolveWorkerId(long datacenterIdValue) {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            long value = (runtimeName + "-" + datacenterIdValue).hashCode() & Integer.MAX_VALUE;
            return value % (MAX_WORKER_ID + 1);
        } catch (Exception ignored) {
        }
        return secureRandom.nextInt((int) MAX_WORKER_ID + 1);
    }
}
