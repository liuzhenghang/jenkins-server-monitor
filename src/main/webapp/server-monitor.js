(function () {
  'use strict';

  var PANEL_ID = 'jenkins-server-monitor-navbar';
  var LEGACY_PANEL_ID = 'jenkins-server-monitor-panel';
  var INSTANCE_KEY = '__jenkinsServerMonitorInstance';
  var REQUEST_TIMEOUT_MS = 10000;
  var TRANSLATIONS = {
    zh: {
      resource: '服务器资源监控', cpu: 'CPU', memory: '内存', storage: '存储',
      network: '网络', disk: '磁盘', upload: '上传', download: '下载',
      read: '读取', write: '写入', iops: '每秒读写', iopsUnit: '次', latency: 'IO 延迟',
      load: '系统负载', available: '可用', unknown: '未知', failed: '读取失败'
    },
    en: {
      resource: 'Server Monitor', cpu: 'CPU', memory: 'Memory', storage: 'Storage',
      network: 'Network', disk: 'Disk', upload: 'Upload', download: 'Download',
      read: 'Read', write: 'Write', iops: 'IOPS', iopsUnit: 'ops', latency: 'IO latency',
      load: 'Load', available: 'free', unknown: 'Unknown', failed: 'Read failed'
    }
  };

  function getTranslations() {
    var language = (document.documentElement.getAttribute('lang') ||
      (window.navigator && window.navigator.language) || 'en').toLowerCase();
    return /^zh(?:-|_|$)/.test(language) ? TRANSLATIONS.zh : TRANSLATIONS.en;
  }

  function trimTrailingSlashes(value) {
    var trimmed = (value || '').replace(/\/+$/, '');
    return trimmed || '/';
  }

  function getConfiguration() {
    var rootNode = document.querySelector('meta[name="jenkins-server-monitor-root-url"]');
    var refreshNode = document.querySelector('meta[name="jenkins-server-monitor-refresh"]');
    if (!rootNode) {
      return null;
    }

    var rootUrl = rootNode.getAttribute('content') || '/';
    var rootHref = new URL(rootUrl, window.location.href).toString();
    if (rootHref.charAt(rootHref.length - 1) !== '/') {
      rootHref += '/';
    }

    return {
      rootPath: trimTrailingSlashes(new URL(rootHref).pathname),
      endpoint: new URL('server-monitor/status', rootHref).toString(),
      refreshSeconds: Math.max(2, parseInt(refreshNode && refreshNode.getAttribute('content'), 10) || 2)
    };
  }

  function createPanel(t) {
    var panel = document.createElement('section');
    panel.id = PANEL_ID;
    panel.className = 'jenkins-server-monitor-navbar';
    panel.setAttribute('aria-label', t.resource);
    panel.innerHTML = '<div class="jenkins-server-monitor-grid">' +
        metricMarkup('cpu', t.cpu) +
        metricMarkup('memory', t.memory) +
        metricMarkup('disk', t.storage) + '</div>' +
      '<div class="jenkins-server-monitor-io">' +
        '<div class="jenkins-server-monitor-network"><span class="io-title">' + t.network + '</span>' +
          '<span class="io-stack"><span>⬆️ ' + t.upload + ' <b data-network-upload>0 B/s</b></span>' +
          '<span>⬇️ ' + t.download + ' <b data-network-download>0 B/s</b></span></span>' +
        '</div>' +
        '<div class="jenkins-server-monitor-diskio"><span class="io-title">' + t.disk + '</span>' +
          '<span class="io-stack"><span>' + t.read + ' <b data-disk-read>0 B</b></span>' +
          '<span>' + t.write + ' <b data-disk-write>0 B</b></span></span>' +
          '<span class="io-stack io-stack-separated"><span>' + t.iops + ' <b data-disk-iops>0</b> ' + t.iopsUnit + '</span>' +
          '<span>' + t.latency + ' <b data-disk-latency>0 ms</b></span></span>' +
        '</div>' +
      '</div>';
    return panel;
  }

  function metricMarkup(key, label) {
    return '<div class="jenkins-server-monitor-metric" data-metric="' + key + '">' +
      '<div class="jenkins-server-monitor-metric-head">' +
        '<div class="jenkins-server-monitor-metric-label">' + label + '</div>' +
        '<div class="jenkins-server-monitor-progress" aria-hidden="true">' +
          '<span class="jenkins-server-monitor-progress-fill" data-fill></span>' +
        '</div>' +
        '<span class="jenkins-server-monitor-metric-value" data-value>—</span>' +
      '</div>' +
      '<div class="jenkins-server-monitor-metric-detail" data-detail>—</div>' +
    '</div>';
  }

  function isNumber(value) {
    return typeof value === 'number' && isFinite(value) && value >= 0;
  }

  function percentage(value) {
    return isNumber(value) ? Math.max(0, Math.min(100, value)) : null;
  }

  function formatBytes(bytes, t) {
    if (!isNumber(bytes) || bytes <= 0) {
      return t.unknown;
    }
    var units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
    var index = 0;
    var number = bytes;
    while (number >= 1024 && index < units.length - 1) {
      number /= 1024;
      index += 1;
    }
    return (index === 0 ? Math.round(number) : number.toFixed(number >= 100 ? 0 : 1)) + ' ' + units[index];
  }

  function formatRate(bytes, t) {
    if (!isNumber(bytes) || bytes <= 0) return '0 B';
    return formatBytes(bytes, t).replace(' ', '') + '/s';
  }

  function setText(panel, selector, value) {
    var node = panel.querySelector(selector);
    if (node) node.textContent = value;
  }

  function setMetric(panel, key, percent, detail, t) {
    var metric = panel.querySelector('[data-metric="' + key + '"]');
    if (!metric) {
      return;
    }
    var valueNode = metric.querySelector('[data-value]');
    var fillNode = metric.querySelector('[data-fill]');
    var detailNode = metric.querySelector('[data-detail]');
    var validPercent = percentage(percent);
    valueNode.textContent = validPercent === null ? t.unknown : validPercent.toFixed(1) + '%';
    var targetPercent = validPercent === null ? 0 : validPercent;
    fillNode.style.width = targetPercent + '%';
    fillNode.classList.toggle('is-warning', validPercent !== null && validPercent >= 80);
    fillNode.classList.toggle('is-critical', validPercent !== null && validPercent >= 95);
    metric.title = detail;
    if (detailNode) {
      detailNode.textContent = detail;
    }
  }

  function render(panel, data) {
    var t = panel.monitorText;
    setMetric(panel, 'cpu', data.cpuPercent,
      isNumber(data.loadAverage) ? t.load + ' ' + data.loadAverage.toFixed(2) : t.load + ' ' + t.unknown, t);
    setMetric(panel, 'memory', data.memoryUsedPercent,
      formatBytes(data.memoryFreeBytes, t) + ' ' + t.available + ' / ' + formatBytes(data.memoryTotalBytes, t), t);
    setMetric(panel, 'disk', data.diskUsedPercent,
      formatBytes(data.diskFreeBytes, t) + ' ' + t.available + ' / ' + formatBytes(data.diskTotalBytes, t), t);
    setText(panel, '[data-network-upload]', formatRate(data.networkUploadBytesPerSecond, t));
    setText(panel, '[data-network-download]', formatRate(data.networkDownloadBytesPerSecond, t));
    setText(panel, '[data-disk-read]', formatBytes(data.diskReadBytesPerSecond, t));
    setText(panel, '[data-disk-write]', formatBytes(data.diskWriteBytesPerSecond, t));
    setText(panel, '[data-disk-iops]', isNumber(data.diskIops) ? data.diskIops.toFixed(0) : '0');
    setText(panel, '[data-disk-latency]', (isNumber(data.diskIoLatencyMillis) ? data.diskIoLatencyMillis.toFixed(0) : '0') + ' ms');

    var state = panel.querySelector('[data-monitor-state]');
    if (state) {
      state.textContent = '运行正常';
      state.className = 'jenkins-server-monitor-state is-ok';
    }
    var updated = panel.querySelector('[data-monitor-updated]');
    if (updated) {
      updated.textContent = data.timestamp ? '更新于 ' + new Date(data.timestamp).toLocaleTimeString() : '—';
    }
  }

  function renderError(panel, t) {
    var state = panel.querySelector('[data-monitor-state]');
    if (state) {
      state.textContent = t.failed;
      state.className = 'jenkins-server-monitor-state is-error';
    }
  }

  function isPanelAttached(panel) {
    return !!(panel && panel.parentNode && document.documentElement.contains(panel));
  }

  function scheduleNext(instance) {
    if (instance.stopped || instance.paused || instance.timerId !== null) {
      return;
    }
    instance.timerId = window.setTimeout(function () {
      instance.timerId = null;
      fetchMetrics(instance);
    }, instance.refreshSeconds * 1000);
  }

  function finishRequest(instance, controller) {
    if (instance.requestTimeoutId !== null) {
      window.clearTimeout(instance.requestTimeoutId);
      instance.requestTimeoutId = null;
    }
    if (instance.controller === controller) {
      instance.controller = null;
    }
    instance.requestInFlight = false;

    if (instance.stopped || instance.paused) {
      return;
    }
    if (!isPanelAttached(instance.panel)) {
      instance.stop();
      return;
    }
    scheduleNext(instance);
  }

  function fetchMetrics(instance) {
    if (instance.stopped || instance.paused || instance.requestInFlight) {
      return;
    }
    if (!isPanelAttached(instance.panel)) {
      instance.stop();
      return;
    }

    var controller = typeof window.AbortController === 'function'
      ? new window.AbortController()
      : null;
    var requestOptions = {
      method: 'GET',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { 'Accept': 'application/json' }
    };
    if (controller) {
      requestOptions.signal = controller.signal;
    }

    instance.requestInFlight = true;
    instance.controller = controller;
    if (controller) {
      instance.requestTimeoutId = window.setTimeout(function () {
        controller.abort();
      }, REQUEST_TIMEOUT_MS);
    }

    fetch(instance.endpoint, requestOptions).then(function (response) {
      if (!response.ok) {
        throw new Error('HTTP ' + response.status);
      }
      return response.json();
    }).then(function (data) {
      if (!instance.stopped) {
        render(instance.panel, data);
      }
    }, function (error) {
      if (!instance.stopped && (!error || error.name !== 'AbortError')) {
        renderError(instance.panel, instance.panel.monitorText);
      }
    }).then(function () {
      finishRequest(instance, controller);
    }, function () {
      finishRequest(instance, controller);
    });
  }

  function mount() {
    var configuration = getConfiguration();
    var legacyPanel = document.getElementById(LEGACY_PANEL_ID);
    if (legacyPanel) {
      legacyPanel.remove();
    }

    var existingInstance = window[INSTANCE_KEY];
    if (existingInstance) {
      if (isPanelAttached(existingInstance.panel)) {
        return;
      }
      if (existingInstance.stop) {
        existingInstance.stop();
      }
    }

    if (!configuration || document.getElementById(PANEL_ID)) {
      return;
    }

    var target = document.getElementById('page-header') || document.getElementById('header') || document.querySelector('header');
    if (!target) {
      return;
    }

    var translations = getTranslations();
    var panel = createPanel(translations);
    panel.monitorText = translations;
    target.appendChild(panel);

    var instance = {
      panel: panel,
      endpoint: configuration.endpoint,
      refreshSeconds: configuration.refreshSeconds,
      timerId: null,
      requestTimeoutId: null,
      controller: null,
      requestInFlight: false,
      paused: !!document.hidden,
      stopped: false,
      visibilityHandler: null,
      stop: null
    };

    instance.stop = function () {
      if (instance.stopped) {
        return;
      }
      instance.stopped = true;
      if (instance.timerId !== null) {
        window.clearTimeout(instance.timerId);
        instance.timerId = null;
      }
      if (instance.requestTimeoutId !== null) {
        window.clearTimeout(instance.requestTimeoutId);
        instance.requestTimeoutId = null;
      }
      if (instance.controller) {
        instance.controller.abort();
        instance.controller = null;
      }
      if (instance.visibilityHandler) {
        document.removeEventListener('visibilitychange', instance.visibilityHandler);
      }
      if (window[INSTANCE_KEY] === instance) {
        delete window[INSTANCE_KEY];
      }
    };

    instance.visibilityHandler = function () {
      if (document.hidden) {
        instance.paused = true;
        if (instance.timerId !== null) {
          window.clearTimeout(instance.timerId);
          instance.timerId = null;
        }
        if (instance.controller) {
          instance.controller.abort();
        }
      } else if (!instance.stopped) {
        instance.paused = false;
        fetchMetrics(instance);
      }
    };

    window[INSTANCE_KEY] = instance;
    document.addEventListener('visibilitychange', instance.visibilityHandler);
    // Poll immediately once, then schedule the next request after completion.
    fetchMetrics(instance);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
}());
