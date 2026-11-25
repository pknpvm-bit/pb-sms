// Zero-dependency lightweight parser for .xlsx (using native DecompressionStream) and .csv
const SimpleSheetReader = {
  async parseFile(file) {
    const fileName = file.name.toLowerCase();
    if (fileName.endsWith('.csv') || fileName.endsWith('.txt')) {
      const text = await file.text();
      return this.parseCSV(text);
    } else if (fileName.endsWith('.xlsx')) {
      const buffer = await file.arrayBuffer();
      return await this.parseXLSX(buffer);
    } else {
      throw new Error("অনুগ্রহ করে .xlsx অথবা .csv ফাইল আপলোড করুন।");
    }
  },

  parseCSV(text) {
    const rows = [];
    let currentRow = [];
    let currentVal = '';
    let inQuotes = false;

    for (let i = 0; i < text.length; i++) {
      const char = text[i];
      const nextChar = text[i + 1];

      if (inQuotes) {
        if (char === '"' && nextChar === '"') {
          currentVal += '"';
          i++;
        } else if (char === '"') {
          inQuotes = false;
        } else {
          currentVal += char;
        }
      } else {
        if (char === '"') {
          inQuotes = true;
        } else if (char === ',' || char === '	') {
          currentRow.push(currentVal.trim());
          currentVal = '';
        } else if (char === '\r' && nextChar === '\n') {
          currentRow.push(currentVal.trim());
          rows.push(currentRow);
          currentRow = [];
          currentVal = '';
          i++;
        } else if (char === '\n' || char === '\r') {
          currentRow.push(currentVal.trim());
          rows.push(currentRow);
          currentRow = [];
          currentVal = '';
        } else {
          currentVal += char;
        }
      }
    }
    if (currentVal !== '' || currentRow.length > 0) {
      currentRow.push(currentVal.trim());
      rows.push(currentRow);
    }
    return rows.filter(r => r.some(cell => cell !== ''));
  },

  async parseXLSX(arrayBuffer) {
    const files = await this.unzip(new Uint8Array(arrayBuffer));
    const decoder = new TextDecoder('utf-8');

    // 1. Parse sharedStrings.xml if available
    let sharedStrings = [];
    if (files['xl/sharedStrings.xml']) {
      const ssXml = decoder.decode(files['xl/sharedStrings.xml']);
      const parser = new DOMParser();
      const doc = parser.parseFromString(ssXml, 'application/xml');
      const siNodes = doc.getElementsByTagName('si');
      for (let i = 0; i < siNodes.length; i++) {
        const tNodes = siNodes[i].getElementsByTagName('t');
        let str = '';
        for (let j = 0; j < tNodes.length; j++) {
          str += tNodes[j].textContent || '';
        }
        sharedStrings.push(str);
      }
    }

    // 2. Find first worksheet (xl/worksheets/sheet1.xml)
    const sheetKey = Object.keys(files).find(k => k.startsWith('xl/worksheets/sheet1.xml')) ||
                     Object.keys(files).find(k => k.startsWith('xl/worksheets/sheet'));
    if (!sheetKey) {
      throw new Error("Excel ফাইলের ভেতরে কোনো ওয়ার্কশিট পাওয়া যায়নি।");
    }

    const sheetXml = decoder.decode(files[sheetKey]);
    const parser = new DOMParser();
    const doc = parser.parseFromString(sheetXml, 'application/xml');
    const rowNodes = doc.getElementsByTagName('row');
    const resultRows = [];

    for (let r = 0; r < rowNodes.length; r++) {
      const rowData = [];
      const cNodes = rowNodes[r].getElementsByTagName('c');
      for (let c = 0; c < cNodes.length; c++) {
        const cell = cNodes[c];
        const cellRef = cell.getAttribute('r') || '';
        const colIdx = this.colRefToIndex(cellRef);
        const cellType = cell.getAttribute('t');

        let val = '';
        if (cellType === 'inlineStr') {
          const tNode = cell.getElementsByTagName('t')[0];
          val = tNode ? tNode.textContent : '';
        } else {
          const vNode = cell.getElementsByTagName('v')[0];
          if (vNode) {
            val = vNode.textContent;
            if (cellType === 's') {
              val = sharedStrings[parseInt(val, 10)] || '';
            }
          }
        }
        if (colIdx >= 0) {
          rowData[colIdx] = String(val).trim();
        } else {
          rowData.push(String(val).trim());
        }
      }
      // Fill empty holes with ""
      for (let k = 0; k < rowData.length; k++) {
        if (rowData[k] === undefined) rowData[k] = '';
      }
      if (rowData.some(x => x !== '')) {
        resultRows.push(rowData);
      }
    }
    return resultRows;
  },

  colRefToIndex(ref) {
    const match = ref.match(/^([A-Z]+)/i);
    if (!match) return -1;
    const letters = match[1].toUpperCase();
    let idx = 0;
    for (let i = 0; i < letters.length; i++) {
      idx = idx * 26 + (letters.charCodeAt(i) - 64);
    }
    return idx - 1;
  },

  async unzip(data) {
    const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
    let offset = 0;
    const files = {};

    while (offset + 30 <= data.length) {
      const sig = view.getUint32(offset, true);
      if (sig !== 0x04034b50) break; // Local file header signature

      const flags = view.getUint16(offset + 6, true);
      const compression = view.getUint16(offset + 8, true);
      let compSize = view.getUint32(offset + 18, true);
      const nameLen = view.getUint16(offset + 26, true);
      const extraLen = view.getUint16(offset + 28, true);

      const nameBytes = data.slice(offset + 30, offset + 30 + nameLen);
      const fileName = new TextDecoder('utf-8').decode(nameBytes);
      const dataStart = offset + 30 + nameLen + extraLen;

      // Handle data descriptor (bit 3) if compSize is 0
      if ((flags & 0x0008) !== 0 && compSize === 0) {
        let search = dataStart;
        while (search + 16 <= data.length) {
          if (view.getUint32(search, true) === 0x08074b50) {
            compSize = view.getUint32(search + 8, true);
            offset = search + 16;
            break;
          }
          search++;
        }
      } else {
        offset = dataStart + compSize;
      }

      const compData = data.slice(dataStart, dataStart + compSize);
      if (!fileName.endsWith('/')) {
        if (compression === 0) {
          files[fileName] = compData;
        } else if (compression === 8) {
          const ds = new DecompressionStream('deflate-raw');
          const writer = ds.writable.getWriter();
          writer.write(compData);
          writer.close();
          const resp = new Response(ds.readable);
          const buf = await resp.arrayBuffer();
          files[fileName] = new Uint8Array(buf);
        }
      }
    }
    return files;
  }
};
