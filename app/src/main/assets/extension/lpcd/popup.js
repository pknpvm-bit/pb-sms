let currentCol = "POI";
let selectedPools = {
  POI: [],
  AOI: [],
  PA: [],
  EA: [],
  FOA: [],
  LG: [],
  SLD: []
};

const columns = ["POI", "AOI", "PA", "EA", "FOA", "LG", "SLD"];

document.addEventListener("DOMContentLoaded", () => {
  chrome.storage.local.get(["selectedPools"], (result) => {
    if (result.selectedPools) {
      selectedPools = { ...selectedPools, ...result.selectedPools };
    }
    renderTabs();
    renderOptions();
  });

  document.getElementById("clearCurrentColBtn").addEventListener("click", () => {
    selectedPools[currentCol] = [];
    savePools(() => {
      renderTabs();
      renderOptions();
      showStatus(`${currentCol} এর সিলেকশন মুছে ফেলা হয়েছে!`);
    });
  });

  document.getElementById("clearAllPoolBtn").addEventListener("click", () => {
    columns.forEach(col => selectedPools[col] = []);
    savePools(() => {
      renderTabs();
      renderOptions();
      showStatus("সব কলামের সিলেকশন রিসেট করা হয়েছে!");
    });
  });

  document.getElementById("autoFillBtn").addEventListener("click", () => {
    sendMessageToTab({
      action: "AUTO_FILL",
      selectedPools: selectedPools,
      allOptions: COLUMN_OPTIONS
    });
  });

  document.getElementById("enableManualBtn").addEventListener("click", () => {
    sendMessageToTab({
      action: "ENABLE_MANUAL",
      allOptions: COLUMN_OPTIONS
    });
  });

  document.getElementById("clearWebInputsBtn").addEventListener("click", () => {
    sendMessageToTab({
      action: "CLEAR_FIELDS"
    });
  });
});

function savePools(callback) {
  chrome.storage.local.set({ selectedPools }, () => {
    if (callback) callback();
  });
}

function renderTabs() {
  const container = document.getElementById("tabsContainer");
  container.innerHTML = "";
  columns.forEach(col => {
    const btn = document.createElement("button");
    btn.className = "tab-btn" + (col === currentCol ? " active" : "");
    const count = selectedPools[col] ? selectedPools[col].length : 0;
    btn.innerHTML = `${col}${count > 0 ? `<span class="badge">${count}</span>` : ""}`;
    btn.addEventListener("click", () => {
      currentCol = col;
      renderTabs();
      renderOptions();
    });
    container.appendChild(btn);
  });
}

function renderOptions() {
  document.getElementById("currentColTitle").textContent = `${currentCol} - Auto Pool (${selectedPools[currentCol].length} selected)`;
  const listDiv = document.getElementById("optionsList");
  listDiv.innerHTML = "";

  const opts = COLUMN_OPTIONS[currentCol] || [];
  opts.forEach((optText, idx) => {
    const label = document.createElement("label");
    label.className = "option-item";

    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = selectedPools[currentCol].includes(optText);
    cb.addEventListener("change", () => {
      if (cb.checked) {
        if (!selectedPools[currentCol].includes(optText)) {
          selectedPools[currentCol].push(optText);
        }
      } else {
        selectedPools[currentCol] = selectedPools[currentCol].filter(item => item !== optText);
      }
      savePools(() => {
        renderTabs();
        document.getElementById("currentColTitle").textContent = `${currentCol} - Auto Pool (${selectedPools[currentCol].length} selected)`;
      });
    });

    const span = document.createElement("span");
    span.textContent = `${idx + 1}. ${optText}`;

    label.appendChild(cb);
    label.appendChild(span);
    listDiv.appendChild(label);
  });
}

function sendMessageToTab(payload) {
  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    if (!tabs[0]) return;
    chrome.tabs.sendMessage(tabs[0].id, payload, (response) => {
      if (chrome.runtime.lastError) {
        showStatus("পেজটি রিফ্রেশ (Reload) করে আবার চেষ্টা করুন!", true);
      } else if (response && response.status) {
        showStatus(response.status);
      }
    });
  });
}

function showStatus(msg, isError = false) {
  const el = document.getElementById("status");
  el.style.color = isError ? "#d93025" : "#188038";
  el.textContent = msg;
  setTimeout(() => {
    el.textContent = "";
  }, 4000);
}
