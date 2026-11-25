(() => {
  if (window.__bspChromeCompatInstalled) return;
  window.__bspChromeCompatInstalled = true;

  const ASSET_BASE = "https://appassets.androidplatform.net/assets/extension/";
  const bridge = (() => {
    try {
      let cur = window;
      for (let i = 0; i < 6 && cur; i++) {
        if (cur.BspAndroidBridge) return cur.BspAndroidBridge;
        if (!cur.parent || cur.parent === cur) break;
        cur = cur.parent;
      }
    } catch (e) {}
    return window.BspAndroidBridge || null;
  })();
  const callbacks = new Map();
  let nextCallbackId = 1;

  window.__bspResolveCallback = (id, result, error) => {
    const entry = callbacks.get(id);
    if (!entry) return;
    callbacks.delete(id);
    if (error) {
      entry.reject(new Error(error));
    } else {
      entry.resolve(result);
    }
  };

  const runtimeListeners = [];
  window.__bspDispatchRuntimeMessage = (callbackId, message) => {
    let responded = false;
    let keepOpen = false;
    const sendResponse = (resp) => {
      if (responded) return;
      responded = true;
      if (bridge && bridge.resolveMessageFromPortal) {
        bridge.resolveMessageFromPortal(
          callbackId,
          JSON.stringify(resp === undefined ? null : resp)
        );
      }
    };
    for (const listener of runtimeListeners) {
      try {
        const ret = listener(message, { id: "bsp-sms-android", tab: { id: 1 } }, sendResponse);
        if (ret === true) keepOpen = true;
      } catch (e) {
        console.warn("Runtime listener error:", e);
      }
    }
    if (!responded && !keepOpen && bridge && bridge.resolveMessageFromPortal) {
      bridge.resolveMessageFromPortal(callbackId, "null");
    }
  };

  function makeStorageArea() {
    return {
      get(keys, cb) {
        const promise = new Promise((resolve) => {
          let result = {};
          try {
            if (bridge && bridge.storageGet) {
              const raw = bridge.storageGet(
                keys === undefined ? "null" : JSON.stringify(keys)
              );
              result = JSON.parse(raw || "{}");
            }
          } catch (e) {
            console.warn("storage.get fallback:", e);
          }
          if (typeof cb === "function") cb(result);
          resolve(result);
        });
        return promise;
      },
      set(items, cb) {
        return new Promise((resolve) => {
          try {
            if (bridge && bridge.storageSet) {
              bridge.storageSet(JSON.stringify(items || {}));
            }
          } catch (e) {
            console.warn("storage.set error:", e);
          }
          if (typeof cb === "function") cb();
          resolve();
        });
      },
      remove(keys, cb) {
        return new Promise((resolve) => {
          try {
            if (bridge && bridge.storageRemove) {
              bridge.storageRemove(JSON.stringify(keys));
            }
          } catch (e) {
            console.warn("storage.remove error:", e);
          }
          if (typeof cb === "function") cb();
          resolve();
        });
      },
      clear(cb) {
        return new Promise((resolve) => {
          try {
            if (bridge && bridge.storageClear) {
              bridge.storageClear();
            }
          } catch (e) {
            console.warn("storage.clear error:", e);
          }
          if (typeof cb === "function") cb();
          resolve();
        });
      }
    };
  }

  const storageLocal = makeStorageArea();

  window.chrome = window.chrome || {};
  window.chrome.runtime = {
    ...(window.chrome.runtime || {}),
    id: "bsp-sms-android",
    lastError: null,
    getURL(path) {
      return ASSET_BASE + String(path || "").replace(/^\/+/, "");
    },
    sendMessage(message, cb) {
      return new Promise((resolve, reject) => {
        if (!bridge || !bridge.sendTabMessage) {
          if (typeof cb === "function") cb(undefined);
          resolve(undefined);
          return;
        }
        const id = "cb_" + nextCallbackId++;
        callbacks.set(id, {
          resolve(res) {
            window.chrome.runtime.lastError = null;
            if (typeof cb === "function") cb(res);
            resolve(res);
          },
          reject(err) {
            window.chrome.runtime.lastError = { message: err.message };
            if (typeof cb === "function") {
              cb(undefined);
              resolve(undefined);
            } else {
              reject(err);
            }
          }
        });
        bridge.sendTabMessage(id, JSON.stringify(message));
      });
    },
    onMessage: {
      addListener(fn) {
        if (typeof fn === "function" && !runtimeListeners.includes(fn)) {
          runtimeListeners.push(fn);
        }
      },
      removeListener(fn) {
        const idx = runtimeListeners.indexOf(fn);
        if (idx >= 0) runtimeListeners.splice(idx, 1);
      }
    }
  };

  window.chrome.storage = {
    ...(window.chrome.storage || {}),
    local: storageLocal,
    sync: storageLocal
  };

  window.chrome.tabs = {
    ...(window.chrome.tabs || {}),
    query(queryInfo, cb) {
      return new Promise((resolve) => {
        let tab = { id: 1, active: true, url: location.href, title: document.title };
        try {
          if (bridge && bridge.getActiveTabInfo) {
            tab = JSON.parse(bridge.getActiveTabInfo());
          }
        } catch (e) {
          console.warn("tabs.query fallback:", e);
        }
        const tabs = [tab];
        if (typeof cb === "function") cb(tabs);
        resolve(tabs);
      });
    },
    sendMessage(tabId, message, cb) {
      return window.chrome.runtime.sendMessage(message, cb);
    }
  };

  window.chrome.scripting = {
    ...(window.chrome.scripting || {}),
    executeScript(injection, cb) {
      return new Promise((resolve, reject) => {
        if (!bridge || !bridge.executeScriptInPortal) {
          reject(new Error("Android scripting bridge unavailable"));
          return;
        }

        let code = "";
        if (typeof injection?.func === "function") {
          const serializedArgs = JSON.stringify(Array.isArray(injection.args) ? injection.args : []);
          code = `return (${injection.func.toString()}).apply(null, ${serializedArgs});`;
        }

        const id = "cb_" + nextCallbackId++;
        callbacks.set(id, {
          resolve(res) {
            window.chrome.runtime.lastError = null;
            if (typeof cb === "function") cb(res);
            resolve(res);
          },
          reject(err) {
            window.chrome.runtime.lastError = { message: err.message };
            reject(err);
          }
        });

        bridge.executeScriptInPortal(
          id,
          JSON.stringify({
            files: Array.isArray(injection?.files) ? injection.files : [],
            code,
            allFrames: Boolean(injection?.target?.allFrames)
          })
        );
      });
    }
  };

  // Bridge popup window.close() & hub postMessage actions to native Android container
  if (location.href.startsWith(ASSET_BASE) && bridge && bridge.notifyHubAction) {
    window.close = () => {
      bridge.notifyHubAction("BSP_CLOSE");
    };
    if (window.parent === window) {
      const originalPostMessage = window.postMessage.bind(window);
      window.postMessage = (message, targetOrigin, transfer) => {
        if (message && typeof message.type === "string" && message.type.startsWith("BSP_")) {
          bridge.notifyHubAction(message.type);
        }
        return originalPostMessage(message, targetOrigin || "*", transfer);
      };
    }
  }

  // Native Android SpeechRecognizer polyfill inside WebView (including iframes)
  if (bridge && bridge.startNativeSpeech) {
    let activeRecognitionInstance = null;

    window.__bspOnSpeechInterim = (transcript) => {
      if (activeRecognitionInstance && activeRecognitionInstance._active && typeof activeRecognitionInstance.oninterim === "function") {
        activeRecognitionInstance.oninterim(transcript);
      }
    };
    window.__bspOnSpeechResult = (transcript) => {
      if (activeRecognitionInstance && activeRecognitionInstance._active && typeof activeRecognitionInstance.onresult === "function") {
        activeRecognitionInstance.onresult({
          results: [[{ transcript }]]
        });
      }
    };
    window.__bspOnSpeechError = (errorCode) => {
      if (activeRecognitionInstance && activeRecognitionInstance._active && typeof activeRecognitionInstance.onerror === "function") {
        activeRecognitionInstance.onerror({ error: errorCode || "aborted" });
      }
    };
    window.__bspOnSpeechEnd = () => {
      const inst = activeRecognitionInstance;
      if (inst && inst._active && typeof inst.onend === "function") {
        inst.onend();
      }
    };
    window.__bspOnSpeechResultAndEnd = (transcript) => {
      const inst = activeRecognitionInstance;
      if (!inst || !inst._active) return;
      if (typeof inst.onresult === "function") {
        inst.onresult({
          results: [[{ transcript }]]
        });
      }
      if (inst._active && typeof inst.onend === "function") {
        inst.onend();
      }
    };

    class AndroidSpeechRecognition {
      constructor() {
        this.continuous = true;
        this.interimResults = false;
        this.lang = "bn-IN";
        this.oninterim = null;
        this.onresult = null;
        this.onerror = null;
        this.onend = null;
        this._active = false;
      }
      start() {
        this._active = true;
        activeRecognitionInstance = this;
        bridge.startNativeSpeech(this.lang || "bn-IN");
      }
      stop() {
        this._active = false;
        if (activeRecognitionInstance === this) {
          activeRecognitionInstance = null;
        }
        bridge.stopNativeSpeech();
      }
      abort() {
        this._active = false;
        if (activeRecognitionInstance === this) {
          activeRecognitionInstance = null;
        }
        bridge.stopNativeSpeech();
      }
    }

    window.SpeechRecognition = AndroidSpeechRecognition;
    window.webkitSpeechRecognition = AndroidSpeechRecognition;
  }
})();
