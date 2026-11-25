chrome.action.onClicked.addListener(async (tab) => {
  if (!tab.id) return;
  try {
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["launcher.js"] });
  } catch (e) {
    console.warn("BSP-SMS could not open on this page:", e);
  }
});
