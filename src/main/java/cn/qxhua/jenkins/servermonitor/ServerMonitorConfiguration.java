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
import hudson.Util;
import java.io.File;
import java.util.Objects;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/** Stores the global settings for the server monitor. */
@Extension
public class ServerMonitorConfiguration extends GlobalConfiguration {
    public static final int DEFAULT_REFRESH_SECONDS = 2;
    public static final int MIN_REFRESH_SECONDS = 2;
    public static final int MAX_REFRESH_SECONDS = 60;
    public static final String DEFAULT_DISK_PATH = ".";

    private boolean enabled = true;
    private int refreshSeconds = DEFAULT_REFRESH_SECONDS;
    private String diskPath = DEFAULT_DISK_PATH;

    public ServerMonitorConfiguration() {
        load();
    }

    public static ServerMonitorConfiguration get() {
        return Objects.requireNonNull(
                GlobalConfiguration.all().get(ServerMonitorConfiguration.class),
                "ServerMonitorConfiguration has not been registered");
    }

    @Override
    public String getDisplayName() {
        return Messages.ServerMonitorConfiguration_DisplayName();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRefreshSeconds() {
        return refreshSeconds;
    }

    public String getDiskPath() {
        return diskPath;
    }

    /**
     * Resolves relative paths below the Jenkins home directory. This keeps the common
     * configuration value "." useful and avoids making the browser choose a server path.
     */
    public File resolveDiskPath() {
        String configuredPath = Util.fixEmptyAndTrim(diskPath);
        if (configuredPath == null) {
            configuredPath = DEFAULT_DISK_PATH;
        }

        File configured = new File(configuredPath);
        if (configured.isAbsolute()) {
            return configured;
        }
        return new File(Jenkins.get().getRootDir(), configuredPath);
    }

    @Override
    public boolean configure(StaplerRequest request, JSONObject json) throws FormException {
        // A checkbox is omitted from the submitted JSON when it is unchecked.
        enabled = json.optBoolean("enabled", false);
        refreshSeconds = clampRefreshSeconds(readInteger(json, "refreshSeconds", DEFAULT_REFRESH_SECONDS));

        String submittedPath = json.optString("diskPath", DEFAULT_DISK_PATH);
        submittedPath = Util.fixEmptyAndTrim(submittedPath);
        diskPath = submittedPath == null ? DEFAULT_DISK_PATH : submittedPath;
        save();
        return true;
    }

    private static int readInteger(JSONObject json, String key, int defaultValue) {
        Object value = json.opt(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int clampRefreshSeconds(int value) {
        return Math.max(MIN_REFRESH_SECONDS, Math.min(MAX_REFRESH_SECONDS, value));
    }
}
