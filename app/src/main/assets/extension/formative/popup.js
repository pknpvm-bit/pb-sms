const MAX_MIN_STORAGE_KEY = 'bsp_formative_maxmin_groups_v1';

function createDefaultGroup() {
  return {
    m1Min: 17,
    m1Max: 20,
    m2Min: 17,
    m2Max: 20,
    m3Min: 17,
    m3Max: 20
  };
}

let maxMinGroups = [
  createDefaultGroup(),
  createDefaultGroup(),
  createDefaultGroup()
];

function setStatusMessage(message, color) {
  const statusEl = document.getElementById('status');
  statusEl.style.display = 'block';
  statusEl.style.color = color || '#188038';
  statusEl.textContent = message;
}

function saveGroupsToStorage() {
  try {
    localStorage.setItem(MAX_MIN_STORAGE_KEY, JSON.stringify(maxMinGroups));
  } catch (_) {}
  try {
    if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
      chrome.storage.local.set({ [MAX_MIN_STORAGE_KEY]: maxMinGroups });
    }
  } catch (_) {}
}

function loadGroupsFromStorage(onReady) {
  let loadedFromLocal = null;
  try {
    const raw = localStorage.getItem(MAX_MIN_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        loadedFromLocal = parsed;
      }
    }
  } catch (_) {}

  if (loadedFromLocal) {
    maxMinGroups = loadedFromLocal;
  }

  try {
    if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
      chrome.storage.local.get([MAX_MIN_STORAGE_KEY], (data) => {
        if (data && Array.isArray(data[MAX_MIN_STORAGE_KEY]) && data[MAX_MIN_STORAGE_KEY].length > 0) {
          maxMinGroups = data[MAX_MIN_STORAGE_KEY];
        }
        onReady();
      });
      return;
    }
  } catch (_) {}

  onReady();
}

function renderGroups() {
  const container = document.getElementById('groupsContainer');
  container.innerHTML = '';

  maxMinGroups.forEach((grp, idx) => {
    const startStudent = idx * 10 + 1;
    const endStudent = (idx + 1) * 10;

    const box = document.createElement('div');
    box.className = 'group-box';

    const header = document.createElement('div');
    header.className = 'group-header';
    header.innerHTML = `<span>Group ${idx + 1} (Students ${startStudent}–${endStudent})</span>`;
    box.appendChild(header);

    const grid = document.createElement('div');
    grid.className = 'group-grid';

    const markDefs = [
      { title: 'Marks 1', minKey: 'm1Min', maxKey: 'm1Max' },
      { title: 'Marks 2', minKey: 'm2Min', maxKey: 'm2Max' },
      { title: 'Marks 3', minKey: 'm3Min', maxKey: 'm3Max' }
    ];

    markDefs.forEach((def) => {
      const col = document.createElement('div');
      col.className = 'mark-col';

      const title = document.createElement('div');
      title.className = 'mark-col-title';
      title.textContent = def.title;
      col.appendChild(title);

      const pair = document.createElement('div');
      pair.className = 'minmax-pair';

      ['Min', 'Max'].forEach((kind) => {
        const key = kind === 'Min' ? def.minKey : def.maxKey;
        const wrap = document.createElement('div');
        wrap.className = 'input-wrap';

        const lbl = document.createElement('label');
        lbl.textContent = kind;
        wrap.appendChild(lbl);

        const inp = document.createElement('input');
        inp.type = 'number';
        inp.inputMode = 'numeric';
        inp.min = '0';
        inp.step = '1';
        inp.value = grp[key] !== undefined && grp[key] !== null ? String(grp[key]) : '';
        inp.dataset.groupIdx = String(idx);
        inp.dataset.fieldKey = key;
        inp.addEventListener('input', (e) => {
          const valStr = e.target.value.trim();
          maxMinGroups[idx][key] = valStr === '' ? '' : parseInt(valStr, 10);
          saveGroupsToStorage();
        });
        wrap.appendChild(inp);

        pair.appendChild(wrap);
      });

      col.appendChild(pair);
      grid.appendChild(col);
    });

    box.appendChild(grid);
    container.appendChild(box);
  });

  const removeBtn = document.getElementById('removeGroupBtn');
  if (removeBtn) {
    removeBtn.disabled = maxMinGroups.length <= 1;
    removeBtn.style.opacity = maxMinGroups.length <= 1 ? '0.5' : '1';
  }
}

document.getElementById('addGroupBtn').addEventListener('click', () => {
  const last = maxMinGroups[maxMinGroups.length - 1] || createDefaultGroup();
  maxMinGroups.push({ ...last });
  saveGroupsToStorage();
  renderGroups();
});

document.getElementById('removeGroupBtn').addEventListener('click', () => {
  if (maxMinGroups.length > 1) {
    maxMinGroups.pop();
    saveGroupsToStorage();
    renderGroups();
  }
});

document.getElementById('copyFirstGroupBtn').addEventListener('click', () => {
  if (maxMinGroups.length === 0) return;
  const first = maxMinGroups[0];
  maxMinGroups = maxMinGroups.map(() => ({ ...first }));
  saveGroupsToStorage();
  renderGroups();
  setStatusMessage('Group 1-এর Min–Max সেটিংস সব গ্রুপে কপি করা হয়েছে!', '#123b70');
});

loadGroupsFromStorage(renderGroups);

// =========================================================================
// 1. PREVIOUS AUTO FILL (Existing logic preserved unchanged)
// =========================================================================
document.getElementById('fillBtn').addEventListener('click', async () => {
  setStatusMessage('নম্বর বসানো হচ্ছে...', '#555');

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: autoFillMarksLogic
    });

    let totalFilled = 0;
    if (results) {
      results.forEach(res => {
        if (res.result && res.result.count) {
          totalFilled += res.result.count;
        }
      });
    }

    if (totalFilled > 0) {
      setStatusMessage(`সফলভাবে ${totalFilled} জন ছাত্রের নম্বর বসানো হয়েছে!`, '#188038');
    } else {
      setStatusMessage('কোনো ইনপুট বক্স পাওয়া যায়নি!', '#d93025');
    }
  } catch (err) {
    setStatusMessage('Error: ' + err.message, '#d93025');
  }
});

function autoFillMarksLogic() {
  // ১. পেজ থেকে Full Marks খুঁজে বের করা (20 নাকি 10)
  const pageText = document.body.innerText || "";
  let fullMarks = 20;
  let minMarks = 17;

  // রেজেক্স দিয়ে "Full Marks - 10" বা "Full Marks : 10" চেক করা
  const fmMatch = pageText.match(/Full\s*Marks\s*[-:]\s*(\d+)/i);
  if (fmMatch && fmMatch[1]) {
    const detected = parseInt(fmMatch[1], 10);
    if (detected === 10) {
      fullMarks = 10;
      minMarks = 7;
    } else if (detected === 20) {
      fullMarks = 20;
      minMarks = 17;
    }
  } else if (pageText.includes("Full Marks - 10") || (!pageText.includes("20") && pageText.includes("10"))) {
    fullMarks = 10;
    minMarks = 7;
  }

  // ২. টেবিলের রো বা ইনপুট ফিল্ডগুলো খুঁজে বের করা
  const rows = document.querySelectorAll('tr');
  let inputRows = [];

  rows.forEach(row => {
    const inputs = Array.from(row.querySelectorAll('input')).filter(inp => 
      (inp.type === 'text' || inp.type === 'number' || !inp.type) && 
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );
    if (inputs.length === 3) {
      inputRows.push(inputs);
    }
  });

  // যদি tr দিয়ে না পাওয়া যায়, তবে সব দৃশ্যমান টেক্সট/নাম্বার ইনপুট ৩টি করে গ্রুপ করা
  if (inputRows.length === 0) {
    const allInputs = Array.from(document.querySelectorAll('input')).filter(inp => 
      (inp.type === 'text' || inp.type === 'number' || !inp.type) && 
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );
    for (let i = 0; i < allInputs.length; i += 3) {
      if (i + 2 < allInputs.length) {
        inputRows.push([allInputs[i], allInputs[i + 1], allInputs[i + 2]]);
      }
    }
  }

  if (inputRows.length === 0) {
    return { count: 0 };
  }

  // র্যান্ডম শাফেল ফাংশন (Fisher-Yates)
  function shuffleArray(array) {
    for (let i = array.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [array[i], array[j]] = [array[j], array[i]];
    }
    return array;
  }

  // Angular বা React ফ্রেমওয়ার্কে সঠিকভাবে ভ্যালু সেট করার ফাংশন
  function setNativeValue(element, value) {
    const valueSetter = Object.getOwnPropertyDescriptor(element, 'value')?.set;
    const prototype = Object.getPrototypeOf(element);
    const prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;

    if (valueSetter && valueSetter !== prototypeValueSetter) {
      prototypeValueSetter.call(element, value);
    } else if (prototypeValueSetter) {
      prototypeValueSetter.call(element, value);
    } else {
      element.value = value;
    }

    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.dispatchEvent(new Event('blur', { bubbles: true }));
  }

  // ৩. প্রতি ১০ জনের ব্যাচ অনুযায়ী নম্বর বসানো
  // ব্যাচ ০: [M, M, M]
  // ব্যাচ ১: [M, M, M-1]
  // ব্যাচ ২: [M, M-1, M-1]
  // ব্যাচ ৩: [M-1, M-1, M-1]
  // ব্যাচ ৪: [M-1, M-1, M-2] ... এভাবে চলবে (minMarks পর্যন্ত)
  inputRows.forEach((inputs, studentIndex) => {
    const batchIndex = Math.floor(studentIndex / 10);
    
    // প্রতিটি স্টেপ মানে ৩টি ঘরের মোট যোগফল থেকে ১ করে কমানো
    // শুরুতে মোট যোগফল = fullMarks * 3
    // সর্বনিম্ন মোট যোগফল = minMarks * 3
    const maxTotal = fullMarks * 3;
    const minTotal = minMarks * 3;
    const targetTotal = Math.max(minTotal, maxTotal - batchIndex);

    // targetTotal কে ৩টি সংখ্যায় ভাগ করা (যাতে পার্থক্য সর্বোচ্চ ১ হয়)
    const baseVal = Math.floor(targetTotal / 3);
    const remainder = targetTotal % 3;

    let marks = [baseVal, baseVal, baseVal];
    for (let r = 0; r < remainder; r++) {
      marks[r] += 1;
    }

    // ৩টি ঘরে র্যান্ডমলি বসানোর জন্য শাফেল করা
    marks = shuffleArray(marks);

    inputs.forEach((input, idx) => {
      setNativeValue(input, marks[idx]);
    });
  });

  return { count: inputRows.length, fullMarks: fullMarks };
}

// =========================================================================
// 2. MAX–MIN FILL (Separate Min/Max for Marks 1, 2, 3 per group of 10)
// =========================================================================
function validateAndNormalizeGroups() {
  const normalized = [];
  for (let i = 0; i < maxMinGroups.length; i++) {
    const g = maxMinGroups[i];
    const pairs = [
      { label: 'Marks 1', min: Number(g.m1Min), max: Number(g.m1Max), minVal: g.m1Min, maxVal: g.m1Max },
      { label: 'Marks 2', min: Number(g.m2Min), max: Number(g.m2Max), minVal: g.m2Min, maxVal: g.m2Max },
      { label: 'Marks 3', min: Number(g.m3Min), max: Number(g.m3Max), minVal: g.m3Min, maxVal: g.m3Max }
    ];

    for (const p of pairs) {
      if (p.minVal === '' || p.maxVal === '' || !Number.isFinite(p.min) || !Number.isFinite(p.max)) {
        return { error: `Group ${i + 1} (${p.label}): Min ও Max উভয় মান লিখুন।` };
      }
      if (!Number.isInteger(p.min) || !Number.isInteger(p.max) || p.min < 0 || p.max < 0) {
        return { error: `Group ${i + 1} (${p.label}): শূন্য বা ধনাত্মক পূর্ণসংখ্যা দিন।` };
      }
      if (p.min > p.max) {
        return { error: `Group ${i + 1} (${p.label}): Min (${p.min}) অবশ্যই Max (${p.max})-এর থেকে ছোট বা সমান হতে হবে।` };
      }
    }

    normalized.push({
      m1Min: pairs[0].min,
      m1Max: pairs[0].max,
      m2Min: pairs[1].min,
      m2Max: pairs[1].max,
      m3Min: pairs[2].min,
      m3Max: pairs[2].max
    });
  }
  return { groups: normalized };
}

document.getElementById('maxMinFillBtn').addEventListener('click', async () => {
  const validation = validateAndNormalizeGroups();
  if (validation.error) {
    setStatusMessage(validation.error, '#d93025');
    return;
  }

  saveGroupsToStorage();
  setStatusMessage('MAX–MIN অনুযায়ী নম্বর বসানো হচ্ছে...', '#555');

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: maxMinFillMarksLogic,
      args: [validation.groups]
    });

    let totalFilled = 0;
    if (results) {
      results.forEach(res => {
        if (res.result && res.result.count) {
          totalFilled += res.result.count;
        }
      });
    }

    if (totalFilled > 0) {
      setStatusMessage(`সফলভাবে ${totalFilled} জন ছাত্রের MAX–MIN নম্বর বসানো হয়েছে!`, '#0f766e');
    } else {
      setStatusMessage('কোনো ইনপুট বক্স পাওয়া যায়নি!', '#d93025');
    }
  } catch (err) {
    setStatusMessage('Error: ' + err.message, '#d93025');
  }
});

function maxMinFillMarksLogic(groups) {
  if (!Array.isArray(groups) || groups.length === 0) {
    return { count: 0 };
  }

  const rows = document.querySelectorAll('tr');
  let inputRows = [];

  rows.forEach(row => {
    const inputs = Array.from(row.querySelectorAll('input')).filter(inp =>
      (inp.type === 'text' || inp.type === 'number' || !inp.type) &&
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );
    if (inputs.length === 3) {
      inputRows.push(inputs);
    }
  });

  if (inputRows.length === 0) {
    const allInputs = Array.from(document.querySelectorAll('input')).filter(inp =>
      (inp.type === 'text' || inp.type === 'number' || !inp.type) &&
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );
    for (let i = 0; i < allInputs.length; i += 3) {
      if (i + 2 < allInputs.length) {
        inputRows.push([allInputs[i], allInputs[i + 1], allInputs[i + 2]]);
      }
    }
  }

  if (inputRows.length === 0) {
    return { count: 0 };
  }

  function setNativeValue(element, value) {
    const valueSetter = Object.getOwnPropertyDescriptor(element, 'value')?.set;
    const prototype = Object.getPrototypeOf(element);
    const prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;

    if (valueSetter && valueSetter !== prototypeValueSetter) {
      prototypeValueSetter.call(element, value);
    } else if (prototypeValueSetter) {
      prototypeValueSetter.call(element, value);
    } else {
      element.value = value;
    }

    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.dispatchEvent(new Event('blur', { bubbles: true }));
  }

  function randomWholeMark(minVal, maxVal) {
    const lo = Math.min(minVal, maxVal);
    const hi = Math.max(minVal, maxVal);
    if (lo === hi) return lo;
    return Math.floor(Math.random() * (hi - lo + 1)) + lo;
  }

  inputRows.forEach((inputs, studentIndex) => {
    const batchIndex = Math.floor(studentIndex / 10);
    const grp = groups[Math.min(batchIndex, groups.length - 1)];

    const m1 = randomWholeMark(grp.m1Min, grp.m1Max);
    const m2 = randomWholeMark(grp.m2Min, grp.m2Max);
    const m3 = randomWholeMark(grp.m3Min, grp.m3Max);

    setNativeValue(inputs[0], m1);
    setNativeValue(inputs[1], m2);
    setNativeValue(inputs[2], m3);
  });

  return { count: inputRows.length };
}

// =========================================================================
// 3. FULL MARKS (Detect Full Marks separately for each field; ignore Min-Max)
// =========================================================================
document.getElementById('fullMarksBtn').addEventListener('click', async () => {
  setStatusMessage('প্রতিটি ফিল্ডের Full Marks ডিটেক্ট করে বসানো হচ্ছে...', '#555');

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: fullMarksFillLogic
    });

    let totalFilled = 0;
    const detectedSummaries = [];
    if (results) {
      results.forEach(res => {
        if (res.result && res.result.count) {
          totalFilled += res.result.count;
          if (Array.isArray(res.result.detectedPerField) && res.result.detectedPerField.length === 3) {
            detectedSummaries.push(res.result.detectedPerField.join(', '));
          }
        }
      });
    }

    if (totalFilled > 0) {
      const fmInfo = detectedSummaries.length > 0 ? ` (${detectedSummaries[0]})` : '';
      setStatusMessage(`সফলভাবে ${totalFilled} জন ছাত্রের Full Marks${fmInfo} বসানো হয়েছে!`, '#e65100');
    } else {
      setStatusMessage('কোনো ইনপুট বক্স পাওয়া যায়নি!', '#d93025');
    }
  } catch (err) {
    setStatusMessage('Error: ' + err.message, '#d93025');
  }
});

function fullMarksFillLogic() {
  const rows = document.querySelectorAll('tr');
  let inputRows = [];

  rows.forEach(row => {
    const inputs = Array.from(row.querySelectorAll('input')).filter(inp =>
      (inp.type === 'text' || inp.type === 'number' || !inp.type) &&
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );
    if (inputs.length === 3) {
      inputRows.push(inputs);
    }
  });

  if (inputRows.length === 0) {
    const allInputs = Array.from(document.querySelectorAll('input')).filter(inp =>
      (inp.type === 'text' || inp.type === 'number' || !inp.type) &&
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );
    for (let i = 0; i < allInputs.length; i += 3) {
      if (i + 2 < allInputs.length) {
        inputRows.push([allInputs[i], allInputs[i + 1], allInputs[i + 2]]);
      }
    }
  }

  if (inputRows.length === 0) {
    return { count: 0 };
  }

  function setNativeValue(element, value) {
    const valueSetter = Object.getOwnPropertyDescriptor(element, 'value')?.set;
    const prototype = Object.getPrototypeOf(element);
    const prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;

    if (valueSetter && valueSetter !== prototypeValueSetter) {
      prototypeValueSetter.call(element, value);
    } else if (prototypeValueSetter) {
      prototypeValueSetter.call(element, value);
    } else {
      element.value = value;
    }

    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.dispatchEvent(new Event('blur', { bubbles: true }));
  }

  function parsePositiveInt(val) {
    if (val === null || val === undefined) return null;
    const trimmed = String(val).trim();
    if (!/^\d+$/.test(trimmed)) return null;
    const n = parseInt(trimmed, 10);
    return n > 0 ? n : null;
  }

  function extractExplicitFullMark(text) {
    if (!text) return null;
    const explicit = text.match(/(?:Full\s*Marks?|F\.?\s*M\.?|Max(?:imum)?\s*(?:Marks?)?|Total\s*Marks?|Out\s*of)\s*[-:=(]?\s*(\d+)/i);
    if (explicit && explicit[1]) {
      const n = parseInt(explicit[1], 10);
      if (n > 0) return n;
    }
    const bracketOrSlash = text.match(/(?:\(\s*(\d+)\s*\)|\[\s*(\d+)\s*\]|\/\s*(\d+))/);
    if (bracketOrSlash) {
      const raw = bracketOrSlash[1] || bracketOrSlash[2] || bracketOrSlash[3];
      const n = parseInt(raw, 10);
      if (n >= 5) return n;
    }
    const dashTenOrTwenty = text.match(/[-:]\s*(20|10)\b/);
    if (dashTenOrTwenty && dashTenOrTwenty[1]) {
      return parseInt(dashTenOrTwenty[1], 10);
    }
    return null;
  }

  // Page-level default fallback (same logic as Auto Fill)
  const pageText = document.body.innerText || '';
  let pageDefaultFullMarks = 20;
  const pageFmMatch = pageText.match(/Full\s*Marks\s*[-:]\s*(\d+)/i);
  if (pageFmMatch && pageFmMatch[1]) {
    const d = parseInt(pageFmMatch[1], 10);
    if (d > 0) pageDefaultFullMarks = d;
  } else if (pageText.includes('Full Marks - 10') || (!pageText.includes('20') && pageText.includes('10'))) {
    pageDefaultFullMarks = 10;
  }

  // Cache table header & table-level Full Marks per table element
  const tableHeaderCache = new WeakMap();
  function getTableMeta(table) {
    if (tableHeaderCache.has(table)) return tableHeaderCache.get(table);

    const allRows = Array.from(table.querySelectorAll('tr'));
    const headerRows = allRows.filter(tr =>
      tr.querySelector('th') || (tr.closest('thead') && !tr.querySelector('input'))
    );
    const occupied = [];
    const colTexts = {};
    let fullMarksColIdx = -1;

    headerRows.forEach((tr, rIdx) => {
      if (!occupied[rIdx]) occupied[rIdx] = [];
      let cIdx = 0;
      Array.from(tr.children).forEach(cell => {
        while (occupied[rIdx][cIdx]) cIdx++;
        const rs = Math.max(1, parseInt(cell.getAttribute('rowspan') || '1', 10) || 1);
        const cs = Math.max(1, parseInt(cell.getAttribute('colspan') || '1', 10) || 1);
        const text = (cell.innerText || cell.textContent || '').trim();
        if (cs === 1 && /\b(?:Full\s*Marks?|F\.?\s*M\.?)\b/i.test(text) && !/\bMarks\s*[123]\b/i.test(text)) {
          fullMarksColIdx = cIdx;
        }
        for (let r = 0; r < rs; r++) {
          if (!occupied[rIdx + r]) occupied[rIdx + r] = [];
          for (let c = 0; c < cs; c++) {
            occupied[rIdx + r][cIdx + c] = true;
            if (r === 0 && text) {
              if (!colTexts[cIdx + c]) colTexts[cIdx + c] = [];
              colTexts[cIdx + c].push({ text, colspan: cs });
            }
          }
        }
        cIdx += cs;
      });
    });

    // Nearest preceding caption/heading above the table
    let tableFullMarks = null;
    const caption = table.querySelector('caption');
    if (caption) {
      tableFullMarks = extractExplicitFullMark(caption.innerText || caption.textContent || '');
    }
    if (tableFullMarks === null) {
      let prev = table.previousElementSibling;
      let steps = 0;
      while (prev && steps < 4) {
        const hit = extractExplicitFullMark(prev.innerText || prev.textContent || '');
        if (hit !== null) {
          tableFullMarks = hit;
          break;
        }
        prev = prev.previousElementSibling;
        steps++;
      }
    }

    const meta = { colTexts, fullMarksColIdx, tableFullMarks };
    tableHeaderCache.set(table, meta);
    return meta;
  }

  function getCellVisualCol(tr, targetCell) {
    let col = 0;
    for (const child of Array.from(tr.children)) {
      if (child === targetCell) return col;
      const cs = Math.max(1, parseInt(child.getAttribute('colspan') || '1', 10) || 1);
      col += cs;
    }
    return -1;
  }

  function detectFieldFullMarks(input) {
    // 1. Direct attributes on the <input>
    const attrNames = [
      'max',
      'aria-valuemax',
      'data-full-marks',
      'data-fullmarks',
      'data-max',
      'data-max-marks',
      'data-fm',
      'ng-max',
      'data-val-range-max'
    ];
    for (const attr of attrNames) {
      const parsed = parsePositiveInt(input.getAttribute(attr));
      if (parsed !== null) return parsed;
    }

    const inputAttrText = [
      input.getAttribute('placeholder') || '',
      input.getAttribute('title') || '',
      input.getAttribute('aria-label') || ''
    ].join(' ');
    const fromInputAttrText = extractExplicitFullMark(inputAttrText);
    if (fromInputAttrText !== null) return fromInputAttrText;

    // 2. Associated <label> or immediate cell/container text
    if (input.id) {
      const lbl = document.querySelector(`label[for="${CSS.escape ? CSS.escape(input.id) : input.id}"]`);
      if (lbl) {
        const fromLabel = extractExplicitFullMark(lbl.innerText || lbl.textContent || '');
        if (fromLabel !== null) return fromLabel;
      }
    }

    const cell = input.closest('td, th, .form-group, .input-group');
    if (cell) {
      const cellText = (cell.innerText || cell.textContent || '').trim();
      const fromCell = extractExplicitFullMark(cellText);
      if (fromCell !== null) return fromCell;
    }

    // 3. Matching column header in the <table> (with colspan/rowspan awareness)
    const tr = input.closest('tr');
    const table = input.closest('table');
    if (table && tr && cell) {
      const meta = getTableMeta(table);
      const colIdx = getCellVisualCol(tr, cell);
      if (colIdx >= 0 && meta.colTexts[colIdx]) {
        const entries = meta.colTexts[colIdx];
        // Prefer bottom-most, single-column headers first
        const singleColEntries = entries.filter(e => e.colspan === 1).reverse();
        const multiColEntries = entries.filter(e => e.colspan > 1).reverse();
        for (const entry of [...singleColEntries, ...multiColEntries]) {
          const hit = extractExplicitFullMark(entry.text);
          if (hit !== null) return hit;
        }
      }

      // 4. Row-level Full Marks column or explicit text in the <tr>
      if (meta.fullMarksColIdx >= 0 && tr.children[meta.fullMarksColIdx]) {
        const fmCellNum = parsePositiveInt(tr.children[meta.fullMarksColIdx].innerText || tr.children[meta.fullMarksColIdx].textContent || '');
        if (fmCellNum !== null) return fmCellNum;
      }
      const rowText = (tr.innerText || tr.textContent || '').trim();
      const fromRow = extractExplicitFullMark(rowText);
      if (fromRow !== null) return fromRow;

      // 5. Table-level caption / preceding heading
      if (meta.tableFullMarks !== null) return meta.tableFullMarks;
    }

    // 6. Page-level fallback
    return pageDefaultFullMarks;
  }

  let samplePerField = null;
  inputRows.forEach((inputs) => {
    const fieldFullMarks = inputs.map((inp) => detectFieldFullMarks(inp));
    if (!samplePerField) {
      samplePerField = fieldFullMarks;
    }
    inputs.forEach((input, idx) => {
      setNativeValue(input, fieldFullMarks[idx]);
    });
  });

  return {
    count: inputRows.length,
    detectedPerField: samplePerField || [pageDefaultFullMarks, pageDefaultFullMarks, pageDefaultFullMarks]
  };
}
