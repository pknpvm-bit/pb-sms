A/B Form Filler — Multi Website v2

Use this extension only on websites where you are authorized to enter data. It fills text fields but NEVER submits forms.

Install:
1. Extract the ZIP.
2. In Chrome open chrome://extensions
3. Enable Developer mode.
4. Click Load unpacked and choose the extracted folder.
5. Open a website with repeated person forms.
6. Click the extension icon, then Auto Detect Form Groups.
7. Review the page and click Fill detected forms.

Pattern (per person, 11 fields):
1–10: 9 A + 2 B
11–20: 8 A + 3 B
21–30: 7 A + 4 B
31–40: 6 A + 5 B
... continues, capped at 11 B.
B positions are shuffled independently for each person.

Important limitations:
- Auto-detection is a best-effort heuristic. Websites have different HTML structures; it may fail or select the wrong group.
- If auto-detection fails, use Manual selector settings and enter the CSS selector for one person's row and the 11 fields inside it.
- The extension processes at most 100 groups.
- It does not enter data in hidden, disabled, or read-only fields.
- It does not click Submit. Always review the filled data before submitting yourself.
- Some sites use frames, shadow DOM, custom controls, or dynamically loaded rows; those may need site-specific adaptation.
