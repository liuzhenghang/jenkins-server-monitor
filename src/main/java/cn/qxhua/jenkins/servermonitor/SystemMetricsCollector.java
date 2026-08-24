/*
 * The MIT License
 *
 * Copyright (c) 2026 QXHua
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package cn.qxhua.jenkins.servermonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

/** Collects host metrics using the JDK management interfaces available to Jenkins. */
public final class SystemMetricsCollector {
    private static final String PROC_MEMINFO = "/proc/meminfo";
    private static final String PROC_NET_DEV = "/proc/net/dev";
    private static final String PROC_DISKSTATS = "/proc/diskstats";
    private static final Object RATE_LOCK = new Object();
    private static ResourceCounters previousCounters;
    private static long previousCountersNanos;
    private static final java.lang.management.OperatingSystemMXBean JAVA_OS_BEAN =
            ManagementFactory.getOperatingSystemMXBean();
    private static final com.sun.management.OperatingSystemMXBean SUN_OS_BEAN =
            JAVA_OS_BEAN instanceof com.sun.management.OperatingSystemMXBean
                    ? (com.sun.management.OperatingSystemMXBean) JAVA_OS_BEAN
                    : null;
    private static final RuntimeMXBean RUNTIME_BEAN = ManagementFactory.getRuntimeMXBean();

    private SystemMetricsCollector() {
        // Utility class.
    }

    public static SystemMetricsSnapshot collect(File diskPath) {
        double cpuPercent = readCpuPercent();
        MemoryReading memory = readMemory();

        long diskTotal = safeTotalSpace(diskPath);
        long diskFree = safeUsableSpace(diskPath);
        double diskUsedPercent = calculateUsedPercent(diskTotal, diskFree);

        double loadAverage = safeLoadAverage();
        long uptimeSeconds = Math.max(0L, RUNTIME_BEAN.getUptime() / 1000L);
        ResourceRates rates = readResourceRates();

        return new SystemMetricsSnapshot(
                cpuPercent,
                memory.totalBytes,
                memory.availableBytes,
                memory.usedPercent,
                diskTotal,
                diskFree,
                diskUsedPercent,
                loadAverage,
                uptimeSeconds,
                System.currentTimeMillis(),
                rates.networkUploadBytesPerSecond,
                rates.networkDownloadBytesPerSecond,
                rates.diskReadBytesPerSecond,
                rates.diskWriteBytesPerSecond,
                rates.diskIops,
                rates.diskIoLatencyMillis);
    }

    private static ResourceRates readResourceRates() {
        ResourceCounters current = new ResourceCounters(readNetworkCounters(), readDiskCounters());
        long now = System.nanoTime();
        synchronized (RATE_LOCK) {
            if (previousCounters == null || previousCountersNanos == 0L) {
                previousCounters = current;
                previousCountersNanos = now;
                return ResourceRates.ZERO;
            }

            double seconds = (now - previousCountersNanos) / 1_000_000_000.0D;
            ResourceRates result = seconds > 0.05D
                    ? ResourceRates.from(previousCounters, current, seconds)
                    : ResourceRates.ZERO;
            previousCounters = current;
            previousCountersNanos = now;
            return result;
        }
    }

    private static NetworkCounters readNetworkCounters() {
        long received = 0L;
        long transmitted = 0L;
        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_NET_DEV))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String interfaceName = line.substring(0, colon).trim();
                if ("lo".equals(interfaceName)) continue;
                String[] fields = line.substring(colon + 1).trim().split("\\s+");
                if (fields.length < 9) continue;
                received += Long.parseLong(fields[0]);
                transmitted += Long.parseLong(fields[8]);
            }
            return new NetworkCounters(received, transmitted);
        } catch (IOException | NumberFormatException | SecurityException ignored) {
            return NetworkCounters.ZERO;
        }
    }

    private static DiskCounters readDiskCounters() {
        long reads = 0L;
        long readSectors = 0L;
        long writes = 0L;
        long writeSectors = 0L;
        long weightedIoMillis = 0L;
        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_DISKSTATS))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 14 || !isWholeDisk(fields[2])) continue;
                reads += Long.parseLong(fields[3]);
                readSectors += Long.parseLong(fields[5]);
                writes += Long.parseLong(fields[7]);
                writeSectors += Long.parseLong(fields[9]);
                weightedIoMillis += Long.parseLong(fields[13]);
            }
            return new DiskCounters(reads, readSectors, writes, writeSectors, weightedIoMillis);
        } catch (IOException | NumberFormatException | SecurityException ignored) {
            return DiskCounters.ZERO;
        }
    }

    private static boolean isWholeDisk(String name) {
        return name.matches("^(sd[a-z]+|vd[a-z]+|xvd[a-z]+|nvme[0-9]+n[0-9]+|mmcblk[0-9]+|md[0-9]+|hd[a-z]+)$");
    }

    /**
     * Linux memory follows the same practical rule used by server panels such as BaoTa:
     * reclaimable cache and buffers should not be reported as application-used memory.
     * MemAvailable is preferred on modern kernels; the older equivalent is calculated
     * from MemFree, Buffers, Cached and SReclaimable.
     */
    private static MemoryReading readMemory() {
        MemoryReading linuxMemory = readLinuxMemory();
        if (linuxMemory != null) {
            return linuxMemory;
        }

        long total = readTotalMemory();
        long free = readFreeMemory();
        return new MemoryReading(total, free, calculateUsedPercent(total, free));
    }

    private static MemoryReading readLinuxMemory() {
        Map<String, Long> values = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_MEMINFO))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }

                String key = line.substring(0, colon).trim();
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length == 0) {
                    continue;
                }

                try {
                    long value = Long.parseLong(parts[0]);
                    if (parts.length > 1 && "kB".equalsIgnoreCase(parts[1])) {
                        value *= 1024L;
                    }
                    values.put(key, value);
                } catch (NumberFormatException ignored) {
                    // Ignore malformed entries and use the remaining fields.
                }
            }
        } catch (IOException | SecurityException ignored) {
            return null;
        }

        long total = values.getOrDefault("MemTotal", -1L);
        if (total <= 0L) {
            return null;
        }

        long available = values.getOrDefault("MemAvailable", -1L);
        if (available < 0L) {
            long free = values.getOrDefault("MemFree", 0L);
            long buffers = values.getOrDefault("Buffers", 0L);
            long cached = values.getOrDefault("Cached", 0L);
            long reclaimable = values.getOrDefault("SReclaimable", 0L);
            long shared = values.getOrDefault("Shmem", 0L);
            available = free + buffers + cached + reclaimable - shared;
        }

        available = Math.max(0L, Math.min(total, available));
        return new MemoryReading(total, available, calculateUsedPercent(total, available));
    }

    private static double readCpuPercent() {
        double load = -1.0D;
        if (SUN_OS_BEAN != null) {
            try {
                load = SUN_OS_BEAN.getSystemCpuLoad();
            } catch (RuntimeException ignored) {
                // Some JVMs expose the bean but cannot read this value.
            }
        }

        if (!isValidRatio(load)) {
            double average = safeLoadAverage();
            int processors = Math.max(1, JAVA_OS_BEAN.getAvailableProcessors());
            if (average >= 0.0D) {
                load = Math.min(1.0D, average / processors);
            }
        }
        return isValidRatio(load) ? round(load * 100.0D) : -1.0D;
    }

    private static long readTotalMemory() {
        if (SUN_OS_BEAN == null) {
            return -1L;
        }
        try {
            return SUN_OS_BEAN.getTotalPhysicalMemorySize();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static long readFreeMemory() {
        if (SUN_OS_BEAN == null) {
            return -1L;
        }
        try {
            return SUN_OS_BEAN.getFreePhysicalMemorySize();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static double safeLoadAverage() {
        try {
            return JAVA_OS_BEAN.getSystemLoadAverage();
        } catch (RuntimeException ignored) {
            return -1.0D;
        }
    }

    private static long safeTotalSpace(File path) {
        try {
            return path == null ? -1L : path.getTotalSpace();
        } catch (SecurityException ignored) {
            return -1L;
        }
    }

    private static long safeUsableSpace(File path) {
        try {
            return path == null ? -1L : path.getUsableSpace();
        } catch (SecurityException ignored) {
            return -1L;
        }
    }

    private static double calculateUsedPercent(long total, long free) {
        if (total <= 0L || free < 0L) {
            return -1.0D;
        }
        long boundedFree = Math.min(total, free);
        return round(((double) (total - boundedFree) / (double) total) * 100.0D);
    }

    private static boolean isValidRatio(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0D && value <= 1.0D;
    }

    private static double round(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static final class MemoryReading {
        private final long totalBytes;
        private final long availableBytes;
        private final double usedPercent;

        private MemoryReading(long totalBytes, long availableBytes, double usedPercent) {
            this.totalBytes = totalBytes;
            this.availableBytes = availableBytes;
            this.usedPercent = usedPercent;
        }
    }

    private static final class NetworkCounters {
        private static final NetworkCounters ZERO = new NetworkCounters(0L, 0L);
        private final long receivedBytes;
        private final long transmittedBytes;
        private NetworkCounters(long receivedBytes, long transmittedBytes) {
            this.receivedBytes = receivedBytes;
            this.transmittedBytes = transmittedBytes;
        }
    }

    private static final class DiskCounters {
        private static final DiskCounters ZERO = new DiskCounters(0L, 0L, 0L, 0L, 0L);
        private final long reads;
        private final long readSectors;
        private final long writes;
        private final long writeSectors;
        private final long weightedIoMillis;
        private DiskCounters(long reads, long readSectors, long writes, long writeSectors, long weightedIoMillis) {
            this.reads = reads;
            this.readSectors = readSectors;
            this.writes = writes;
            this.writeSectors = writeSectors;
            this.weightedIoMillis = weightedIoMillis;
        }
    }

    private static final class ResourceCounters {
        private final NetworkCounters network;
        private final DiskCounters disk;
        private ResourceCounters(NetworkCounters network, DiskCounters disk) {
            this.network = network;
            this.disk = disk;
        }
    }

    private static final class ResourceRates {
        private static final ResourceRates ZERO = new ResourceRates(0D, 0D, 0D, 0D, 0D, 0D);
        private final double networkUploadBytesPerSecond;
        private final double networkDownloadBytesPerSecond;
        private final double diskReadBytesPerSecond;
        private final double diskWriteBytesPerSecond;
        private final double diskIops;
        private final double diskIoLatencyMillis;
        private ResourceRates(double upload, double download, double read, double write, double iops, double latency) {
            this.networkUploadBytesPerSecond = upload;
            this.networkDownloadBytesPerSecond = download;
            this.diskReadBytesPerSecond = read;
            this.diskWriteBytesPerSecond = write;
            this.diskIops = iops;
            this.diskIoLatencyMillis = latency;
        }
        private static ResourceRates from(ResourceCounters previous, ResourceCounters current, double seconds) {
            double upload = Math.max(0D, current.network.transmittedBytes - previous.network.transmittedBytes) / seconds;
            double download = Math.max(0D, current.network.receivedBytes - previous.network.receivedBytes) / seconds;
            double read = Math.max(0D, current.disk.readSectors - previous.disk.readSectors) * 512D / seconds;
            double write = Math.max(0D, current.disk.writeSectors - previous.disk.writeSectors) * 512D / seconds;
            double ioCount = Math.max(0D, current.disk.reads - previous.disk.reads)
                    + Math.max(0D, current.disk.writes - previous.disk.writes);
            double iops = ioCount / seconds;
            double latency = ioCount > 0D
                    ? Math.max(0D, current.disk.weightedIoMillis - previous.disk.weightedIoMillis) / ioCount
                    : 0D;
            return new ResourceRates(upload, download, read, write, iops, latency);
        }
    }
}
