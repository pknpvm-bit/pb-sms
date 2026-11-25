let excelRows = [];
let headers = [];
let pageTextCache = "";

// পপআপ খোলার সাথে সাথেই বর্তমান ওয়েবপেজের টেক্সট পড়ে নেওয়া (সাবজেক্ট অটো-ডিটেক্ট করার জন্য)
document.addEventListener('DOMContentLoaded', async () => {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: () => document.body ? document.body.innerText : ""
    });
    if (results) {
      pageTextCache = results.map(r => r.result || "").join(" ");
    }
  } catch (e) {
    console.error("Page read error:", e);
  }
});

// Excel / CSV ফাইল আপলোড হলে পার্স করা
document.getElementById('excelFile').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  const statusEl = document.getElementById('status');
  statusEl.innerHTML = '';
  if (!file) return;

  try {
    const jsonData = await SimpleSheetReader.parseFile(file);
    
    if (jsonData.length < 2) {
      statusEl.innerHTML = '<span style="color:#d93025;">ফাইলে পর্যাপ্ত ডেটা নেই!</span>';
      return;
    }

    headers = jsonData[0].map(h => String(h).trim());
    excelRows = jsonData.slice(1).filter(row => row[0] && String(row[0]).trim() !== "");

    populateSubjectsAndAutoDetect();
  } catch (err) {
    statusEl.innerHTML = `<span style="color:#d93025;">ফাইল পড়তে সমস্যা হয়েছে: ${err.message}</span>`;
  }
});

function populateSubjectsAndAutoDetect() {
  const subjectSelect = document.getElementById('subjectSelect');
  const subjectBox = document.getElementById('subjectBox');
  const detectedInfo = document.getElementById('detectedInfo');
  const fillBtn = document.getElementById('fillBtn');

  subjectSelect.innerHTML = '';

  let matchedIndex = -1;
  const upperPageText = pageTextCache.toUpperCase();

  // বাংলা ও ইংরেজি নামের সম্ভাব্য প্রতিশব্দ ম্যাপ
  const aliasMap = {
    'BENGALI': ['BENGALI', 'BANGLA', 'বাংলা', '1ST LANGUAGE'],
    'ENGLISH': ['ENGLISH', 'ইংরেজি', '2ND LANGUAGE'],
    'MATH': ['MATH', 'MATHEMATICS', 'গণিত', 'অঙ্ক'],
    'EVS': ['EVS', 'ENVIRONMENT', 'পরিবেশ', 'OUR ENVIRONMENT'],
    'SCIENCE': ['SCIENCE', 'বিজ্ঞান', 'PHYSICAL SCIENCE', 'LIFE SCIENCE'],
    'HISTORY': ['HISTORY', 'ইতিহাস'],
    'GEOGRAPHY': ['GEOGRAPHY', 'ভূগোল'],
    'ART': ['ART', 'WORK EDUCATION', 'ART & WORK EDUCATION', 'স্বাস্থ্য']
  };

  for (let i = 1; i < headers.length; i++) {
    const colName = headers[i];
    if (!colName) continue;

    const option = document.createElement('option');
    option.value = i;
    option.textContent = colName;
    subjectSelect.appendChild(option);

    // অটো ডিটেক্ট লজিক
    if (matchedIndex === -1) {
      const upperCol = colName.toUpperCase();
      if (upperPageText.includes(upperCol)) {
        matchedIndex = i;
      } else {
        for (const [key, aliases] of Object.entries(aliasMap)) {
          if (aliases.some(a => upperCol.includes(a)) && aliases.some(a => upperPageText.includes(a))) {
            matchedIndex = i;
            break;
          }
        }
      }
    }
  }

  subjectBox.style.display = 'block';
  detectedInfo.style.display = 'block';

  if (matchedIndex !== -1) {
    subjectSelect.value = matchedIndex;
    detectedInfo.innerHTML = `✅ অটো-ডিটেক্টেড সাবজেক্ট: <b>${headers[matchedIndex]}</b>`;
  } else {
    detectedInfo.innerHTML = `⚠️ পেজের সাবজেক্ট অটো-ডিটেক্ট হয়নি, নিচে থেকে সিলেক্ট করুন।`;
  }

  fillBtn.disabled = false;
}

// Fill Marks বাটনে ক্লিক করলে ওয়েবপেজে ডেটা পাঠানো
document.getElementById('fillBtn').addEventListener('click', async () => {
  const colIndex = parseInt(document.getElementById('subjectSelect').value, 10);
  const statusEl = document.getElementById('status');
  statusEl.innerHTML = 'নম্বর মেলানো হচ্ছে...';

  const studentMarksMap = {};
  excelRows.forEach(row => {
    const rawName = String(row[0] || "").trim().toLowerCase().replace(/\s+/g, ' ');
    const markVal = row[colIndex];
    if (rawName && markVal !== undefined && markVal !== "") {
      studentMarksMap[rawName] = String(markVal).trim();
    }
  });

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: matchAndFillOnPage,
      args: [studentMarksMap]
    });

    let totalMatched = 0;
    let allUnmatched = [];

    if (results) {
      results.forEach(res => {
        if (res.result) {
          totalMatched += res.result.matchedCount || 0;
          if (res.result.unmatchedNames) {
            allUnmatched = allUnmatched.concat(res.result.unmatchedNames);
          }
        }
      });
    }

    let html = `<span style="color:#188038; font-weight:bold;">✅ সফলভাবে ${totalMatched} জনের নম্বর বসানো হয়েছে!</span>`;
    if (allUnmatched.length > 0) {
      html += `<div class="unmatched-list"><b>এক্সেলে পাওয়া যায়নি (${allUnmatched.length} জন):</b><br>${allUnmatched.join(', ')}</div>`;
    }
    statusEl.innerHTML = html;

  } catch (err) {
    statusEl.innerHTML = `<span style="color:#d93025;">Error: ${err.message}</span>`;
  }
});

function matchAndFillOnPage(studentMarksMap) {
  function normalizeName(name) {
    return name.toLowerCase().replace(/[^a-z0-9ঀ-৿\s]/g, '').replace(/\s+/g, ' ').trim();
  }

  const normalizedMap = {};
  for (const [k, v] of Object.entries(studentMarksMap)) {
    normalizedMap[normalizeName(k)] = v;
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

  const rows = document.querySelectorAll('tr');
  let matchedCount = 0;
  let unmatchedNames = [];

  rows.forEach(row => {
    const inputs = Array.from(row.querySelectorAll('input')).filter(inp => 
      (inp.type === 'text' || inp.type === 'number' || !inp.type) && 
      !inp.disabled && !inp.readOnly && inp.offsetParent !== null
    );

    if (inputs.length === 0) return;

    const cells = Array.from(row.querySelectorAll('td'));
    let foundName = "";
    let matchedMark = null;

    for (const cell of cells) {
      if (cell.querySelector('input')) continue;
      const cellText = cell.innerText.trim();
      if (!cellText || /^\d+$/.test(cellText)) continue;

      const normCell = normalizeName(cellText);
      if (normCell.length < 2) continue;

      foundName = cellText;
      if (normalizedMap.hasOwnProperty(normCell)) {
        matchedMark = normalizedMap[normCell];
        break;
      }
    }

    if (matchedMark !== null) {
      setNativeValue(inputs[0], matchedMark);
      inputs[0].style.backgroundColor = '#e6f4ea';
      matchedCount++;
    } else if (foundName) {
      unmatchedNames.push(foundName);
    }
  });

  return { matchedCount, unmatchedNames };
}
