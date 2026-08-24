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

/** A point-in-time snapshot of the Jenkins host's resource usage. */
public final class SystemMetricsSnapshot {
    private final double cpuPercent;
    private final long memoryTotalBytes;
    private final long memoryFreeBytes;
    private final double memoryUsedPercent;
    private final long diskTotalBytes;
    private final long diskFreeBytes;
    private final double diskUsedPercent;
    private final double loadAverage;
    private final long uptimeSeconds;
    private final long timestamp;
    private final double networkUploadBytesPerSecond;
    private final double networkDownloadBytesPerSecond;
    private final double diskReadBytesPerSecond;
    private final double diskWriteBytesPerSecond;
    private final double diskIops;
    private final double diskIoLatencyMillis;

    public SystemMetricsSnapshot(
            double cpuPercent,
            long memoryTotalBytes,
            long memoryFreeBytes,
            double memoryUsedPercent,
            long diskTotalBytes,
            long diskFreeBytes,
            double diskUsedPercent,
            double loadAverage,
            long uptimeSeconds,
            long timestamp,
            double networkUploadBytesPerSecond,
            double networkDownloadBytesPerSecond,
            double diskReadBytesPerSecond,
            double diskWriteBytesPerSecond,
            double diskIops,
            double diskIoLatencyMillis) {
        this.cpuPercent = cpuPercent;
        this.memoryTotalBytes = memoryTotalBytes;
        this.memoryFreeBytes = memoryFreeBytes;
        this.memoryUsedPercent = memoryUsedPercent;
        this.diskTotalBytes = diskTotalBytes;
        this.diskFreeBytes = diskFreeBytes;
        this.diskUsedPercent = diskUsedPercent;
        this.loadAverage = loadAverage;
        this.uptimeSeconds = uptimeSeconds;
        this.timestamp = timestamp;
        this.networkUploadBytesPerSecond = networkUploadBytesPerSecond;
        this.networkDownloadBytesPerSecond = networkDownloadBytesPerSecond;
        this.diskReadBytesPerSecond = diskReadBytesPerSecond;
        this.diskWriteBytesPerSecond = diskWriteBytesPerSecond;
        this.diskIops = diskIops;
        this.diskIoLatencyMillis = diskIoLatencyMillis;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    public long getMemoryTotalBytes() {
        return memoryTotalBytes;
    }

    public long getMemoryFreeBytes() {
        return memoryFreeBytes;
    }

    public double getMemoryUsedPercent() {
        return memoryUsedPercent;
    }

    public long getDiskTotalBytes() {
        return diskTotalBytes;
    }

    public long getDiskFreeBytes() {
        return diskFreeBytes;
    }

    public double getDiskUsedPercent() {
        return diskUsedPercent;
    }

    public double getLoadAverage() {
        return loadAverage;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getNetworkUploadBytesPerSecond() { return networkUploadBytesPerSecond; }
    public double getNetworkDownloadBytesPerSecond() { return networkDownloadBytesPerSecond; }
    public double getDiskReadBytesPerSecond() { return diskReadBytesPerSecond; }
    public double getDiskWriteBytesPerSecond() { return diskWriteBytesPerSecond; }
    public double getDiskIops() { return diskIops; }
    public double getDiskIoLatencyMillis() { return diskIoLatencyMillis; }
}
