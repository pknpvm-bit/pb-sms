(function() {
  let activeDropdown = null;
  let globalAllOptions = null;

  function detectColumnMap(table) {
    const colMap = {};
    const headerRows = table.querySelectorAll("thead tr, tr");
    for (let r = 0; r < Math.min(headerRows.length, 4); r++) {
      const cells = headerRows[r].querySelectorAll("th, td");
      cells.forEach((cell, idx) => {
        const text = cell.innerText.trim().toUpperCase().replace(/[^A-Z]/g, "");
        if (["POI", "AOI", "PA", "EA", "FOA", "LG", "SLD"].includes(text)) {
          colMap[idx] = text;
        } else {
          // Partial match fallback
          const raw = cell.innerText.trim().toUpperCase();
          if (raw.includes("POI")) colMap[idx] = "POI";
          else if (raw.includes("AOI")) colMap[idx] = "AOI";
          else if (raw === "PA" || raw.includes("POSITIVE ATTITUDE")) colMap[idx] = "PA";
          else if (raw === "EA" || raw.includes("EXCEPTIONAL ABILITY")) colMap[idx] = "EA";
          else if (raw === "FOA" || raw.includes("ANXIETY")) colMap[idx] = "FOA";
          else if (raw === "LG" || raw.includes("LEARNING GAP")) colMap[idx] = "LG";
          else if (raw === "SLD" || raw.includes("SPECIFIC LEARNING")) colMap[idx] = "SLD";
        }
      });
      if (Object.keys(colMap).length > 0) break;
    }
    return colMap;
  }

  function setNativeValue(el, value) {
    if (el.tagName === "SELECT") {
      let matched = false;
      for (let opt of el.options) {
        if (opt.text.trim().toLowerCase() === value.toLowerCase() || opt.value.trim().toLowerCase() === value.toLowerCase()) {
          el.value = opt.value;
          matched = true;
          break;
        }
      }
      if (!matched) {
        for (let opt of el.options) {
          if (opt.text.toLowerCase().includes(value.toLowerCase()) || value.toLowerCase().includes(opt.text.toLowerCase())) {
            el.value = opt.value;
            break;
          }
        }
      }
      el.dispatchEvent(new Event("change", { bubbles: true }));
      return;
    }

    const prototype = el.tagName === "TEXTAREA" ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(prototype, "value")?.set;
    if (setter) {
      setter.call(el, value);
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    el.dispatchEvent(new Event("blur", { bubbles: true }));
  }

  function getTargetFields() {
    const targets = [];
    const tables = document.querySelectorAll("table");
    tables.forEach(table => {
      const colMap = detectColumnMap(table);
      if (Object.keys(colMap).length === 0) return;

      const rows = table.querySelectorAll("tbody tr, tr");
      rows.forEach(row => {
        if (row.querySelector("th")) return;
        const cells = row.querySelectorAll("td");
        cells.forEach((cell, idx) => {
          const colName = colMap[idx];
          if (!colName) return;
          const field = cell.querySelector("input[type='text'], input:not([type]), textarea, select");
          if (field && !field.disabled && !field.readOnly) {
            targets.push({ field, colName });
          }
        });
      });
    });
    return targets;
  }

  function closeDropdown() {
    if (activeDropdown) {
      activeDropdown.remove();
      activeDropdown = null;
    }
  }

  function showManualDropdown(field, colName, optionsList) {
    closeDropdown();
    if (!optionsList || optionsList.length === 0) return;

    const dropdown = document.createElement("div");
    dropdown.style.position = "absolute";
    dropdown.style.zIndex = "999999";
    dropdown.style.background = "#ffffff";
    dropdown.style.border = "1px solid #1a73e8";
    dropdown.style.borderRadius = "6px";
    dropdown.style.boxShadow = "0 4px 12px rgba(0,0,0,0.2)";
    dropdown.style.maxHeight = "220px";
    dropdown.style.overflowY = "auto";
    dropdown.style.minWidth = Math.max(field.offsetWidth, 260) + "px";
    dropdown.style.maxWidth = "360px";
    dropdown.style.fontFamily = "Segoe UI, sans-serif";
    dropdown.style.fontSize = "12px";

    const header = document.createElement("div");
    header.style.background = "#1a73e8";
    header.style.color = "#fff";
    header.style.padding = "5px 8px";
    header.style.fontWeight = "bold";
    header.style.display = "flex";
    header.style.justifyContent = "space-between";
    header.innerHTML = `<span>${colName} - All Options (${optionsList.length})</span><span style="cursor:pointer;" id="closeManualDrop">✕</span>`;
    dropdown.appendChild(header);

    optionsList.forEach((opt, idx) => {
      const item = document.createElement("div");
      item.style.padding = "6px 10px";
      item.style.cursor = "pointer";
      item.style.borderBottom = "1px solid #f0f0f0";
      item.style.color = "#222";
      item.textContent = `${idx + 1}. ${opt}`;

      item.addEventListener("mouseenter", () => {
        item.style.background = "#e8f0fe";
      });
      item.addEventListener("mouseleave", () => {
        item.style.background = "#ffffff";
      });
      item.addEventListener("mousedown", (e) => {
        e.preventDefault();
        setNativeValue(field, opt);
        closeDropdown();
      });

      dropdown.appendChild(item);
    });

    const rect = field.getBoundingClientRect();
    dropdown.style.left = (rect.left + window.scrollX) + "px";
    dropdown.style.top = (rect.bottom + window.scrollY + 2) + "px";

    document.body.appendChild(dropdown);
    activeDropdown = dropdown;

    dropdown.querySelector("#closeManualDrop").addEventListener("mousedown", (e) => {
      e.preventDefault();
      closeDropdown();
    });
  }

  function attachManualListeners(allOptions) {
    globalAllOptions = allOptions;
    const targets = getTargetFields();
    let attachedCount = 0;

    targets.forEach(({ field, colName }) => {
      if (field.tagName === "SELECT") return;
      if (field.dataset.manualDropdownAttached === "true") {
        attachedCount++;
        return;
      }

      field.dataset.manualDropdownAttached = "true";
      field.addEventListener("focus", () => {
        showManualDropdown(field, colName, globalAllOptions[colName]);
      });
      field.addEventListener("click", () => {
        showManualDropdown(field, colName, globalAllOptions[colName]);
      });
      attachedCount++;
    });

    return attachedCount;
  }

  document.addEventListener("mousedown", (e) => {
    if (activeDropdown && !activeDropdown.contains(e.target) && e.target.dataset.manualDropdownAttached !== "true") {
      closeDropdown();
    }
  });

  chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === "AUTO_FILL") {
      const { selectedPools, allOptions } = request;
      const targets = getTargetFields();
      let filledCount = 0;

      targets.forEach(({ field, colName }) => {
        const customPool = selectedPools[colName];
        const poolToUse = (customPool && customPool.length > 0) ? customPool : allOptions[colName];

        if (poolToUse && poolToUse.length > 0) {
          const randomVal = poolToUse[Math.floor(Math.random() * poolToUse.length)];
          setNativeValue(field, randomVal);
          filledCount++;
        }
      });

      attachManualListeners(allOptions);
      sendResponse({ status: `সফলভাবে ${filledCount}টি ঘর ফিল করা হয়েছে!` });
    } 
    else if (request.action === "ENABLE_MANUAL") {
      const count = attachManualListeners(request.allOptions);
      sendResponse({ status: `${count}টি ঘরে ম্যানুয়াল ড্রপডাউন চালু হয়েছে!` });
    } 
    else if (request.action === "CLEAR_FIELDS") {
      const targets = getTargetFields();
      targets.forEach(({ field }) => {
        setNativeValue(field, "");
      });
      sendResponse({ status: `সব ঘর খালি করা হয়েছে!` });
    }
    return true;
  });
})();
