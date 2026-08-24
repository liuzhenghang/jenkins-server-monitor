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

import hudson.Extension;
import hudson.model.RootAction;
import java.io.IOException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerResponse;

/** Provides the JSON endpoint used by the page monitor. */
@Extension
public class ServerMonitorAction implements RootAction {
    @Override
    public String getIconFileName() {
        // Returning null keeps this technical endpoint out of the Jenkins side menu.
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.ServerMonitorAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "server-monitor";
    }

    /** GET /server-monitor/status */
    public void doStatus(StaplerResponse response) throws IOException {
        Jenkins.get().checkPermission(Jenkins.READ);

        SystemMetricsSnapshot snapshot = SystemMetricsCollector.collect(
                ServerMonitorConfiguration.get().resolveDiskPath());
        JSONObject payload = new JSONObject();
        payload.put("cpuPercent", snapshot.getCpuPercent());
        payload.put("memoryTotalBytes", snapshot.getMemoryTotalBytes());
        payload.put("memoryFreeBytes", snapshot.getMemoryFreeBytes());
        payload.put("memoryUsedPercent", snapshot.getMemoryUsedPercent());
        payload.put("diskTotalBytes", snapshot.getDiskTotalBytes());
        payload.put("diskFreeBytes", snapshot.getDiskFreeBytes());
        payload.put("diskUsedPercent", snapshot.getDiskUsedPercent());
        payload.put("loadAverage", snapshot.getLoadAverage());
        payload.put("uptimeSeconds", snapshot.getUptimeSeconds());
        payload.put("timestamp", snapshot.getTimestamp());
        payload.put("networkUploadBytesPerSecond", snapshot.getNetworkUploadBytesPerSecond());
        payload.put("networkDownloadBytesPerSecond", snapshot.getNetworkDownloadBytesPerSecond());
        payload.put("diskReadBytesPerSecond", snapshot.getDiskReadBytesPerSecond());
        payload.put("diskWriteBytesPerSecond", snapshot.getDiskWriteBytesPerSecond());
        payload.put("diskIops", snapshot.getDiskIops());
        payload.put("diskIoLatencyMillis", snapshot.getDiskIoLatencyMillis());

        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(payload.toString());
    }
}
