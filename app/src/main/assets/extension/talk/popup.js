document.getElementById('injectBtn').addEventListener('click', async () => {
  const lang = document.getElementById('langSelect').value;
  const statusEl = document.getElementById('status');

  try {
    if (window.BspAndroidBridge && typeof window.BspAndroidBridge.isToolAllowed === 'function') {
      if (!window.BspAndroidBridge.isToolAllowed('talk')) {
        statusEl.textContent = 'Access Blocked: Talk to Filler is disabled by Admin.';
        return;
      }
    }
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: initVoiceFillerWidget,
      args: [lang]
    });
    const anyInjected = Array.isArray(results) && results.some((r) => r && r.result && r.result.injected);
    if (!anyInjected) {
      statusEl.textContent = 'এই পেজে কোনো মার্কস এন্ট্রি বক্স পাওয়া যায়নি।';
      return;
    }
    window.close();
  } catch (err) {
    statusEl.textContent = 'Error: ' + err.message;
  }
});

function initVoiceFillerWidget(selectedLang) {
  function isWritableMarksInput(inp) {
    if (!inp || inp.closest('#vmf-floating-panel')) return false;
    const t = (inp.type || 'text').toLowerCase();
    if (!['text', 'number', 'tel', ''].includes(t)) return false;
    if (inp.disabled || inp.readOnly) return false;
    if (inp.offsetParent === null) return false;
    try {
      const cs = window.getComputedStyle(inp);
      if (cs.display === 'none' || cs.visibility === 'hidden') return false;
    } catch (e) {}

    if (inp.closest('.dataTables_filter, .dataTables_length, .pagination, nav, header, footer')) return false;
    const meta = [
      inp.name || '',
      inp.id || '',
      inp.className || '',
      inp.placeholder || '',
      inp.getAttribute('aria-label') || ''
    ].join(' ').toLowerCase();

    if (/search|filter|captcha|password|username|user_id|login|otp|email|phone|mobile|roll_no|student_name/.test(meta)) {
      return false;
    }

    // Check if inside a marks table or explicitly named as marks/score/obtained input
    const inTable = !!inp.closest('table');
    const looksLikeMarkName = /mark|score|obt|se_|fe_|summative|formative|grade_val|point|num/.test(meta);
    const hasNumericAttr = inp.type === 'number' || inp.hasAttribute('max') || inp.getAttribute('inputmode') === 'numeric';

    if (inTable || looksLikeMarkName || hasNumericAttr) return true;
    return false;
  }

  const allInputs = Array.from(document.querySelectorAll('input')).filter(isWritableMarksInput);
  if (allInputs.length === 0) return { injected: false };

  const existing = document.getElementById('vmf-floating-panel');
  if (existing) existing.remove();

  // Compute maxMarks limit for each writable input cleanly from attributes or column headers
  function detectMaxMarksForInput(inp) {
    const attrCandidates = [
      inp.getAttribute('max'),
      inp.getAttribute('data-max'),
      inp.getAttribute('data-max-marks'),
      inp.getAttribute('data-full-marks'),
      inp.getAttribute('data-fm')
    ];
    for (const cand of attrCandidates) {
      if (cand !== null && cand !== undefined && String(cand).trim() !== '') {
        const n = parseFloat(String(cand).trim());
        if (!isNaN(n) && isFinite(n) && n > 0 && n <= 500) return n;
      }
    }

    const row = inp.closest('tr');
    const tbl = inp.closest('table');
    if (row && tbl) {
      const td = inp.closest('td, th');
      if (td) {
        const rowCells = Array.from(row.children);
        const colIdx = rowCells.indexOf(td);
        const prevCell = colIdx > 0 ? rowCells[colIdx - 1] : null;
        if (prevCell) {
          const prevTxt = (prevCell.innerText || '').trim();
          if (/^\d{1,3}$/.test(prevTxt)) {
            const pVal = parseInt(prevTxt, 10);
            if ([10, 15, 20, 25, 30, 35, 40, 50, 70, 80, 90, 100].includes(pVal)) {
              // Check if previous cell header specifically says Full Marks / F.M.
              const headRows = Array.from(tbl.querySelectorAll('thead tr, tr')).slice(0, 3);
              const joinedHdr = headRows.map((hr) => hr.innerText || '').join(' ').toLowerCase();
              if (/full\s*marks?|f\.?\s*m\.?/i.test(joinedHdr)) {
                return pVal;
              }
            }
          }
        }
        const ths = Array.from(tbl.querySelectorAll('thead th, tr:first-child th, tr:first-child td'));
        if (colIdx >= 0 && colIdx < ths.length) {
          const hTxt = (ths[colIdx].innerText || '').trim();
          const hm = hTxt.match(/(?:max|full\s*marks?|f\.?\s*m\.?|se|\()\s*[:\-]?\s*(\d{1,3})\b/i);
          if (hm) {
            const hv = parseInt(hm[1], 10);
            if (hv > 0 && hv <= 500) return hv;
          }
        }
      }
    }

    const hint = (inp.placeholder || '') + ' ' + (inp.title || '');
    const hintM = hint.match(/(?:max|out\s*of|full\s*marks?|f\.?\s*m\.?|0\s*-\s*)\s*[:\-]?\s*(\d{1,3})\b/i);
    if (hintM) {
      const hv = parseInt(hintM[1], 10);
      if (hv > 0 && hv <= 500) return hv;
    }
    return null;
  }

  const maxMarksCache = allInputs.map(detectMaxMarksForInput);

  const studentNameCache = allInputs.map((inp) => {
    const row = inp.closest('tr');
    if (!row) return '';
    const cells = row.querySelectorAll('td');
    for (let i = 0; i < Math.min(3, cells.length); i++) {
      const txt = (cells[i].innerText || '').trim();
      if (txt && !/^\d+$/.test(txt) && txt.length >= 2) {
        return ' - ' + txt;
      }
    }
    return '';
  });

  const panel = document.createElement('div');
  panel.id = 'vmf-floating-panel';
  panel.style.cssText = `
    position: fixed;
    bottom: 18px;
    right: 16px;
    width: 320px;
    max-width: calc(100vw - 24px);
    background: #ffffff;
    border: 2px solid #1a73e8;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0,0,0,0.22);
    z-index: 999999;
    font-family: 'Segoe UI', sans-serif;
    padding: 12px;
    color: #202124;
  `;

  panel.innerHTML = `
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
      <strong style="color:#1a73e8; font-size:14px;">🎙️ Voice Marks Entry (${allInputs.length} boxes)</strong>
      <button id="vmf-close" style="background:none; border:none; font-size:20px; line-height:1; cursor:pointer; color:#555;">&times;</button>
    </div>
    <div style="display:flex; gap:6px; margin-bottom:8px;">
      <select id="vmf-lang" style="flex:1; padding:6px; border-radius:6px; border:1px solid #ccc; font-size:12px;">
        <option value="bn-IN" ${selectedLang === 'bn-IN' ? 'selected' : ''}>বাংলা (Bangla)</option>
        <option value="en-IN" ${selectedLang === 'en-IN' ? 'selected' : ''}>English</option>
      </select>
      <button id="vmf-undo" style="padding:6px 10px; background:#f59e0b; color:#fff; border:none; border-radius:6px; font-weight:bold; cursor:pointer; font-size:12px;">Undo</button>
      <button id="vmf-toggle" style="padding:6px 12px; background:#188038; color:#fff; border:none; border-radius:6px; font-weight:bold; cursor:pointer; font-size:12px;">Start Mic</button>
    </div>
    <div id="vmf-current-info" style="background:#e8f0fe; padding:7px 9px; border-radius:6px; font-size:12.5px; margin-bottom:6px; font-weight:600; color:#1967d2;">
      বর্তমান ঘর: #1
    </div>
    <div id="vmf-heard" style="background:#f1f3f4; padding:7px 9px; border-radius:6px; font-size:12px; min-height:20px; color:#3c4043;">
      মাইক চালু করতে "Start Mic" চাপুন...
    </div>
  `;

  document.body.appendChild(panel);

  let currentIndex = 0;
  let isListening = false;
  let lastHighlightedInput = null;
  let isProgrammaticFocus = false;
  let lastProcessedTranscript = '';
  let lastProcessedAt = 0;
  const undoStack = [];

  function setNativeValue(element, value) {
    const strVal = value === null || value === undefined ? '' : String(value);
    const valueSetter = Object.getOwnPropertyDescriptor(element, 'value')?.set;
    const prototype = Object.getPrototypeOf(element);
    const prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;

    if (valueSetter && valueSetter !== prototypeValueSetter) {
      prototypeValueSetter.call(element, strVal);
    } else if (prototypeValueSetter) {
      prototypeValueSetter.call(element, strVal);
    } else {
      element.value = strVal;
    }

    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.dispatchEvent(new Event('blur', { bubbles: true }));
  }

  function highlightCurrentInput() {
    if (lastHighlightedInput && lastHighlightedInput !== allInputs[currentIndex]) {
      lastHighlightedInput.style.outline = '';
      lastHighlightedInput.style.backgroundColor = '';
      lastHighlightedInput = null;
    }

    const infoEl = document.getElementById('vmf-current-info');
    if (!infoEl) return;
    if (currentIndex < allInputs.length) {
      const inp = allInputs[currentIndex];
      inp.style.outline = '3px solid #1a73e8';
      inp.style.backgroundColor = '#e8f0fe';
      lastHighlightedInput = inp;

      const rect = inp.getBoundingClientRect();
      if (rect.top < 70 || rect.bottom > window.innerHeight - 130) {
        inp.scrollIntoView({ behavior: 'auto', block: 'center' });
      }

      isProgrammaticFocus = true;
      try {
        inp.focus({ preventScroll: true });
      } catch (e) {
        inp.focus();
      } finally {
        isProgrammaticFocus = false;
      }

      const studentName = studentNameCache[currentIndex] || '';
      const maxM = maxMarksCache[currentIndex];
      const maxBadge = maxM !== null ? ` [Max: ${maxM}]` : '';
      infoEl.textContent = `বর্তমান ঘর: #${currentIndex + 1}/${allInputs.length}${maxBadge}${studentName}`;
    } else {
      infoEl.textContent = `✅ সবগুলো ঘর (${allInputs.length}টি) পূরণ হয়ে গেছে!`;
    }
  }

  allInputs.forEach((inp, idx) => {
    inp.addEventListener('focus', () => {
      if (isProgrammaticFocus) return;
      currentIndex = idx;
      highlightCurrentInput();
    });
  });

  function convertBengaliDigits(str) {
    const bn = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
    return String(str || '').replace(/[০-৯]/g, (d) => bn.indexOf(d));
  }

  // Comprehensive Bengali (0-100) + English composite numbers & Bengali phonetics dictionary
  const singleWordNum = {
    'শূন্য': 0, 'শুন্য': 0, 'জিরো': 0, 'zero': 0, 'oh': 0, 'nil': 0,
    'এক': 1, 'একটা': 1, 'ওয়ান': 1, 'ওয়ান': 1, 'one': 1,
    'দুই': 2, 'দুটো': 2, 'টু': 2, 'two': 2,
    'তিন': 3, 'থ্রি': 3, 'three': 3,
    'চার': 4, 'ফোর': 4, 'four': 4,
    'পাঁচ': 5, 'পাচ': 5, 'ফাইভ': 5, 'five': 5,
    'ছয়': 6, 'ছয়': 6, 'সিক্স': 6, 'six': 6,
    'সাত': 7, 'সেভেন': 7, 'seven': 7,
    'আট': 8, 'এইট': 8, 'eight': 8,
    'নয়': 9, 'নয়': 9, 'নাইন': 9, 'nine': 9,
    'দশ': 10, 'টেন': 10, 'ten': 10,
    'এগারো': 11, 'এগার': 11, 'ইলেভেন': 11, 'eleven': 11,
    'বারো': 12, 'বার': 12, 'টুয়েলভ': 12, 'টুয়েলভ': 12, 'twelve': 12,
    'তেরো': 13, 'তের': 13, 'থার্টিন': 13, 'thirteen': 13,
    'চৌদ্দ': 14, 'চোদ্দ': 14, 'ফোর্টিন': 14, 'fourteen': 14,
    'পনেরো': 15, 'পনের': 15, 'ফিফটিন': 15, 'fifteen': 15,
    'ষোলো': 16, 'ষোল': 16, 'সিক্সটিন': 16, 'sixteen': 16,
    'সতেরো': 17, 'সতের': 17, 'সেভেনটিন': 17, 'seventeen': 17,
    'আঠারো': 18, 'আঠার': 18, 'এইটিন': 18, 'eighteen': 18,
    'উনিশ': 19, 'ঊনিশ': 19, 'নাইনটিন': 19, 'nineteen': 19,
    'বিশ': 20, 'কুড়ি': 20, 'কুড়ি': 20, 'টুয়েন্টি': 20, 'টুয়েন্টি': 20, 'twenty': 20,
    'একুশ': 21, 'বাইশ': 22, 'তেইশ': 23, 'চব্বিশ': 24,
    'পঁচিশ': 25, 'পচিশ': 25, 'পঁইচিশ': 25,
    'ছাব্বিশ': 26, 'সাতাশ': 27, 'সাতাইশ': 27, 'আটাশ': 28, 'আঠাশ': 28,
    'উনত্রিশ': 29, 'ঊনত্রিশ': 29, 'উনতিরিশ': 29,
    'ত্রিশ': 30, 'তিরিশ': 30, 'থার্টি': 30, 'thirty': 30,
    'একত্রিশ': 31, 'একতিরিশ': 31, 'বত্রিশ': 32, 'বত্তিরিশ': 32,
    'তেত্রিশ': 33, 'তেত্তিরিশ': 33, 'চৌত্রিশ': 34, 'চৌতিরিশ': 34,
    'পঁয়ত্রিশ': 35, 'পঁয়ত্রিশ': 35, 'পয়ত্রিশ': 35, 'পয়ত্রিশ': 35, 'পঁইতিরিশ': 35,
    'ছত্রিশ': 36, 'ছত্তিরিশ': 36, 'সাঁইত্রিশ': 37, 'সাইত্রিশ': 37, 'সাতত্রিশ': 37,
    'আটত্রিশ': 38, 'আঠত্রিশ': 38, 'উনচল্লিশ': 39, 'ঊনচল্লিশ': 39,
    'চল্লিশ': 40, 'ফর্টি': 40, 'forty': 40, 'fourty': 40,
    'একচল্লিশ': 41, 'বিয়াল্লিশ': 42, 'বিয়াল্লিশ': 42, 'বেয়াল্লিশ': 42,
    'তেতাল্লিশ': 43, 'চুয়াল্লিশ': 44, 'চুয়াল্লিশ': 44,
    'পঁয়তাল্লিশ': 45, 'পঁয়তাল্লিশ': 45, 'পয়তাল্লিশ': 45, 'পয়তাল্লিশ': 45,
    'ছেচল্লিশ': 46, 'সাতচল্লিশ': 47, 'আটচল্লিশ': 48,
    'উনপঞ্চাশ': 49, 'ঊনপঞ্চাশ': 49,
    'পঞ্চাশ': 50, 'ফিফটি': 50, 'fifty': 50,
    'একান্ন': 51, 'বাহান্ন': 52, 'তিপ্পান্ন': 53, 'চুয়ান্ন': 54, 'চুয়ান্ন': 54,
    'পঞ্চান্ন': 55, 'ছাপ্পান্ন': 56, 'সাতান্ন': 57, 'আটান্ন': 58, 'উনষাট': 59,
    'ষাট': 60, 'সিক্সটি': 60, 'sixty': 60,
    'একষট্টি': 61, 'বাষট্টি': 62, 'তেষট্টি': 63, 'চৌষট্টি': 64,
    'পঁয়ষট্টি': 65, 'পঁয়ষট্টি': 65, 'পয়ষট্টি': 65, 'ছেষট্টি': 66,
    'সাতষট্টি': 67, 'আটষট্টি': 68, 'উনসত্তর': 69,
    'সত্তর': 70, 'সেভেন্টি': 70, 'seventy': 70,
    'একাত্তর': 71, 'বাহাত্তর': 72, 'তিয়াত্তর': 73, 'তিয়াত্তর': 73,
    'চুয়াত্তর': 74, 'চুয়াত্তর': 74, 'পঁচাত্তর': 75, 'পচাত্তর': 75,
    'ছিয়াত্তর': 76, 'ছিয়াত্তর': 76, 'সাতাত্তর': 77, 'আটাত্তর': 78, 'উনআশি': 79,
    'আশি': 80, 'এইটি': 80, 'eighty': 80,
    'একাশি': 81, 'বিরাশি': 82, 'তিরাশি': 83, 'চুরাশি': 84, 'পঁচাশি': 85,
    'ছিয়াশি': 86, 'ছিয়াশি': 86, 'সাতাশি': 87, 'আটাশি': 88, 'অষ্টাশি': 88, 'উননব্বই': 89,
    'নব্বই': 90, 'নাইন্টি': 90, 'ninety': 90,
    'একানব্বই': 91, 'বিরানব্বই': 92, 'তিরানব্বই': 93, 'চুরানব্বই': 94,
    'পঁচানব্বই': 95, 'ছিয়ানব্বই': 96, 'ছিয়ানব্বই': 96, 'সাতানব্বই': 97, 'আটানব্বই': 98, 'নিরানব্বই': 99,
    'একশ': 100, 'একশো': 100, 'শো': 100, 'hundred': 100
  };

  const englishTens = {
    'twenty': 20, 'টুয়েন্টি': 20, 'টুয়েন্টি': 20,
    'thirty': 30, 'থার্টি': 30,
    'forty': 40, 'fourty': 40, 'ফর্টি': 40,
    'fifty': 50, 'ফিফটি': 50,
    'sixty': 60, 'সিক্সটি': 60,
    'seventy': 70, 'সেভেন্টি': 70,
    'eighty': 80, 'এইটি': 80,
    'ninety': 90, 'নাইন্টি': 90
  };

  const englishOnes = {
    'one': 1, 'ওয়ান': 1, 'ওয়ান': 1,
    'two': 2, 'টু': 2,
    'three': 3, 'থ্রি': 3,
    'four': 4, 'ফোর': 4,
    'five': 5, 'ফাইভ': 5,
    'six': 6, 'সিক্স': 6,
    'seven': 7, 'সেভেন': 7,
    'eight': 8, 'এইট': 8,
    'nine': 9, 'নাইন': 9
  };

  function performUndo() {
    const heardEl = document.getElementById('vmf-heard');
    if (undoStack.length === 0) {
      if (heardEl) heardEl.textContent = 'আনডু করার মতো কোনো পূর্বের পরিবর্তন নেই।';
      return false;
    }
    const last = undoStack.pop();
    if (last && last.index >= 0 && last.index < allInputs.length) {
      setNativeValue(allInputs[last.index], last.prevValue);
      currentIndex = last.index;
      highlightCurrentInput();
      if (heardEl) {
        heardEl.textContent = `↩️ আনডু হয়েছে: ঘর #${last.index + 1} (${last.prevValue === '' ? 'খালি' : last.prevValue})`;
      }
      return true;
    }
    return false;
  }

  function applyMarkValue(numValue) {
    if (currentIndex >= allInputs.length) return;
    const inp = allInputs[currentIndex];
    const maxAllowed = maxMarksCache[currentIndex];
    const parsedNum = parseFloat(numValue);

    if (isNaN(parsedNum) || !isFinite(parsedNum) || parsedNum < 0) {
      const heardEl = document.getElementById('vmf-heard');
      if (heardEl) {
        heardEl.style.color = '#d93025';
        heardEl.textContent = `⚠️ অবৈধ নম্বর (${numValue}) — বসানো হয়নি।`;
      }
      return false;
    }

    if (maxAllowed !== null && parsedNum > maxAllowed) {
      const heardEl = document.getElementById('vmf-heard');
      if (heardEl) {
        heardEl.style.color = '#d93025';
        heardEl.textContent = `⚠️ ${numValue} মানটি সর্বোচ্চ নম্বর (${maxAllowed})-এর বেশি — বসানো হয়নি!`;
      }
      return false;
    }

    const formatted = Number.isInteger(parsedNum) ? String(parsedNum) : String(parsedNum);
    undoStack.push({ index: currentIndex, prevValue: inp.value || '', newValue: formatted });
    setNativeValue(inp, formatted);
    currentIndex++;
    return true;
  }

  function parseCompositeNumberAt(tokens, idx) {
    const tok = tokens[idx];
    if (!tok) return null;

    // Handle "সাড়ে" / "half" (e.g., "সাড়ে আঠারো" -> 18.5)
    if ((tok === 'সাড়ে' || tok === 'সাড়ে' || tok === 'half') && idx + 1 < tokens.length) {
      const nextParsed = parseCompositeNumberAt(tokens, idx + 1);
      if (nextParsed && nextParsed.isInt) {
        return { value: nextParsed.value + 0.5, consumed: 1 + nextParsed.consumed, isInt: false };
      }
    }

    // Handle hyphenated composite English e.g. "twenty-five"
    if (tok.includes('-')) {
      const parts = tok.split('-').filter(Boolean);
      if (parts.length === 2 && englishTens[parts[0]] !== undefined && englishOnes[parts[1]] !== undefined) {
        return { value: englishTens[parts[0]] + englishOnes[parts[1]], consumed: 1, isInt: true };
      }
    }

    // Direct numeric token ("15" or "15.5")
    if (/^\d+(?:\.\d+)?$/.test(tok)) {
      const n = parseFloat(tok);
      // Support decimal word next: "15 point 5" / "15 দশমিক পাঁচ"
      if (idx + 2 < tokens.length && ['point', 'dot', 'পয়েন্ট', 'পয়েন্ট', 'দশমিক'].includes(tokens[idx + 1])) {
        const decPart = singleWordNum[tokens[idx + 2]] ?? (/^\d+$/.test(tokens[idx + 2]) ? parseInt(tokens[idx + 2], 10) : null);
        if (decPart !== null && decPart >= 0 && decPart <= 9) {
          return { value: parseFloat(`${Math.trunc(n)}.${decPart}`), consumed: 3, isInt: false };
        }
      }
      return { value: n, consumed: 1, isInt: Number.isInteger(n) };
    }

    // Composite English "twenty five" -> 25
    if (englishTens[tok] !== undefined && idx + 1 < tokens.length && englishOnes[tokens[idx + 1]] !== undefined) {
      const val = englishTens[tok] + englishOnes[tokens[idx + 1]];
      if (idx + 3 < tokens.length && ['point', 'dot', 'পয়েন্ট', 'পয়েন্ট', 'দশমিক'].includes(tokens[idx + 2])) {
        const dp = singleWordNum[tokens[idx + 3]] ?? (/^\d+$/.test(tokens[idx + 3]) ? parseInt(tokens[idx + 3], 10) : null);
        if (dp !== null && dp >= 0 && dp <= 9) {
          return { value: parseFloat(`${val}.${dp}`), consumed: 4, isInt: false };
        }
      }
      return { value: val, consumed: 2, isInt: true };
    }

    // "one hundred" / "a hundred" -> 100
    if ((tok === 'one' || tok === 'এক') && idx + 1 < tokens.length && (tokens[idx + 1] === 'hundred' || tokens[idx + 1] === 'শো' || tokens[idx + 1] === 'শ')) {
      return { value: 100, consumed: 2, isInt: true };
    }

    // Single Bengali or English number word (including "পঁয়তাল্লিশ", "একুশ", etc.)
    if (Object.prototype.hasOwnProperty.call(singleWordNum, tok)) {
      const baseVal = singleWordNum[tok];
      if (idx + 2 < tokens.length && ['point', 'dot', 'পয়েন্ট', 'পয়েন্ট', 'দশমিক'].includes(tokens[idx + 1])) {
        const dp = singleWordNum[tokens[idx + 2]] ?? (/^\d+$/.test(tokens[idx + 2]) ? parseInt(tokens[idx + 2], 10) : null);
        if (dp !== null && dp >= 0 && dp <= 9) {
          return { value: parseFloat(`${baseVal}.${dp}`), consumed: 3, isInt: false };
        }
      }
      return { value: baseVal, consumed: 1, isInt: true };
    }

    return null;
  }

  function processVoiceTranscript(transcript) {
    const cleanRaw = String(transcript || '').trim();
    if (!cleanRaw) return;
    const now = Date.now();
    if (cleanRaw === lastProcessedTranscript && (now - lastProcessedAt) < 450) {
      return;
    }
    lastProcessedTranscript = cleanRaw;
    lastProcessedAt = now;

    const heardEl = document.getElementById('vmf-heard');
    if (heardEl) {
      heardEl.style.color = '#3c4043';
      heardEl.textContent = '';
      heardEl.append('শুনেছি: ');
      const boldEl = document.createElement('b');
      boldEl.textContent = `"${cleanRaw}"`;
      heardEl.appendChild(boldEl);
    }

    let text = cleanRaw.toLowerCase().trim();
    text = convertBengaliDigits(text);

    const tokens = text.split(/[\s,;|।]+/).filter(Boolean);
    let needsHighlightUpdate = false;
    let validationBlocked = false;

    for (let i = 0; i < tokens.length; i++) {
      const token = tokens[i];

      if (['undo', 'আনডু', 'আন্ডু', 'রিভার্ট', 'revert', 'ফিরিয়ে', 'ফিরিয়ে', 'ফেরত'].includes(token)) {
        performUndo();
        continue;
      }

      if (['skip', 'স্কিপ', 'বাদ', 'ছাড়ো', 'ছাড়ো', 'leave'].includes(token)) {
        if (currentIndex < allInputs.length) {
          currentIndex++;
          needsHighlightUpdate = true;
        }
        continue;
      }

      if (['back', 'ব্যাক', 'আগের', 'পিছনে', 'আগে', 'previous'].includes(token)) {
        if (currentIndex > 0) {
          currentIndex--;
          needsHighlightUpdate = true;
        }
        continue;
      }

      if (['delete', 'ডিলিট', 'মুছুন', 'মুছে', 'কাটো', 'clear', 'ক্লিয়ার', 'ক্লিয়ার'].includes(token)) {
        if (currentIndex < allInputs.length) {
          const inp = allInputs[currentIndex];
          undoStack.push({ index: currentIndex, prevValue: inp.value || '', newValue: '' });
          setNativeValue(inp, '');
          needsHighlightUpdate = true;
        }
        continue;
      }

      if (['next', 'নেক্সট', 'পরের', 'পরে', 'সামনে', 'আগামী'].includes(token)) {
        if (currentIndex < allInputs.length - 1) {
          currentIndex++;
          needsHighlightUpdate = true;
        }
        continue;
      }

      if (['stop', 'স্টপ', 'বন্ধ', 'থামো'].includes(token)) {
        stopMicCleanly();
        return;
      }

      const numMatch = parseCompositeNumberAt(tokens, i);
      if (numMatch !== null) {
        i += (numMatch.consumed - 1);
        if (currentIndex < allInputs.length) {
          const applied = applyMarkValue(numMatch.value);
          if (applied) {
            needsHighlightUpdate = true;
          } else {
            validationBlocked = true;
          }
        }
      }
    }

    if (needsHighlightUpdate && !validationBlocked) {
      highlightCurrentInput();
    }
  }

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    document.getElementById('vmf-heard').textContent = 'দুঃখিত, আপনার ব্রাউজারে Speech Recognition সাপোর্ট করছে না।';
    return { injected: true };
  }

  const recognition = new SpeechRecognition();
  recognition.continuous = true;
  recognition.interimResults = true;
  recognition.lang = document.getElementById('vmf-lang').value;

  const toggleBtn = document.getElementById('vmf-toggle');

  function stopMicCleanly() {
    isListening = false;
    try {
      recognition.abort ? recognition.abort() : recognition.stop();
    } catch (e) {}
    if (toggleBtn) {
      toggleBtn.textContent = 'Start Mic';
      toggleBtn.style.background = '#188038';
    }
    const heardEl = document.getElementById('vmf-heard');
    if (heardEl) {
      heardEl.style.color = '#3c4043';
      heardEl.textContent = 'মাইক বন্ধ করা হয়েছে।';
    }
  }

  recognition.oninterim = (partial) => {
    if (!isListening || !partial) return;
    const heardEl = document.getElementById('vmf-heard');
    if (heardEl) {
      heardEl.style.color = '#3c4043';
      heardEl.textContent = `শুনছি: "${partial}"`;
    }
  };

  recognition.onresult = (event) => {
    if (!isListening) return;
    const lastResultIndex = event.results.length - 1;
    const transcript = event.results[lastResultIndex][0].transcript;
    processVoiceTranscript(transcript);
  };

  recognition.onerror = (event) => {
    if (!isListening) return;
    if (event.error === 'no-speech') {
      const heardEl = document.getElementById('vmf-heard');
      if (heardEl) {
        heardEl.style.color = '#3c4043';
        heardEl.textContent = 'শুনছি... নম্বর বা কমান্ড বলুন';
      }
      return;
    }
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      stopMicCleanly();
    }
    const heardEl = document.getElementById('vmf-heard');
    if (heardEl) {
      heardEl.style.color = '#d93025';
      heardEl.textContent = 'Mic Error: ' + event.error;
    }
  };

  recognition.onend = () => {
    if (isListening) {
      try {
        recognition.start();
      } catch (e) {}
    }
  };

  toggleBtn.addEventListener('click', () => {
    if (!isListening) {
      recognition.lang = document.getElementById('vmf-lang').value;
      try {
        isListening = true;
        recognition.start();
        toggleBtn.textContent = 'Stop Mic';
        toggleBtn.style.background = '#d93025';
        const heardEl = document.getElementById('vmf-heard');
        if (heardEl) {
          heardEl.style.color = '#3c4043';
          heardEl.textContent = 'শুনছি... নম্বর বা কমান্ড বলুন';
        }
        highlightCurrentInput();
      } catch (err) {
        isListening = false;
        document.getElementById('vmf-heard').textContent = 'Error: ' + err.message;
      }
    } else {
      stopMicCleanly();
    }
  });

  document.getElementById('vmf-undo').addEventListener('click', () => {
    performUndo();
  });

  document.getElementById('vmf-lang').addEventListener('change', (e) => {
    recognition.lang = e.target.value;
    if (isListening) {
      try {
        recognition.stop();
        setTimeout(() => {
          if (isListening) {
            try { recognition.start(); } catch (err) {}
          }
        }, 80);
      } catch (err) {}
    }
  });

  document.getElementById('vmf-close').addEventListener('click', () => {
    stopMicCleanly();
    allInputs.forEach((inp) => {
      inp.style.outline = '';
      inp.style.backgroundColor = '';
    });
    panel.remove();
  });

  highlightCurrentInput();
  return { injected: true, count: allInputs.length };
}
