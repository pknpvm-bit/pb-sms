(() => {
  const ID = "bsp-sms-host";
  const existing = document.getElementById(ID);
  if (existing) {
    existing.shadowRoot?.querySelector("iframe")?.contentWindow?.postMessage({ type: "BSP_RESTORE" }, "*");
    existing.shadowRoot?.querySelector("iframe")?.focus();
    return;
  }
  const host = document.createElement("div");
  host.id = ID;
  host.style.cssText = "all:initial;position:fixed;z-index:2147483647;right:24px;top:80px;width:430px;height:620px;";
  const shadow = host.attachShadow({ mode: "open" });
  const frame = document.createElement("iframe");
  frame.src = chrome.runtime.getURL("hub.html");
  frame.title = "BSP-SMS";
  frame.allow = "microphone";
  frame.style.cssText = "border:0;width:100%;height:100%;border-radius:18px;box-shadow:0 16px 48px rgba(0,0,0,.28);background:white;";
  shadow.appendChild(frame);
  document.documentElement.appendChild(host);
  let minimized = false;
  let drag = null;
  window.addEventListener("message", (event) => {
    if (event.source !== frame.contentWindow || !event.data?.type?.startsWith("BSP_")) return;
    if (event.data.type === "BSP_MINIMIZE") {
      minimized = true;
      frame.contentWindow.postMessage({type: "BSP_MINIMIZED"}, "*");
      host.style.width = "64px"; host.style.height = "64px"; host.style.right = "18px"; host.style.top = "auto"; host.style.bottom = "22px";
      frame.style.borderRadius = "50%";
    }
    if (event.data.type === "BSP_RESTORE") {
      minimized = false;
      frame.contentWindow.postMessage({type: "BSP_RESTORED"}, "*");
      host.style.width = "430px"; host.style.height = "620px"; host.style.right = "24px"; host.style.top = "80px"; host.style.bottom = "auto";
      frame.style.borderRadius = "18px";
    }
    if (event.data.type === "BSP_CLOSE") host.remove();
    if (event.data.type === "BSP_DRAG_START") {
      drag = { x: event.data.x, y: event.data.y, left: host.getBoundingClientRect().left, top: host.getBoundingClientRect().top };
      const move = (ev) => { if (!drag) return; host.style.left = Math.max(0, Math.min(innerWidth - host.offsetWidth, drag.left + ev.screenX - drag.x)) + "px"; host.style.top = Math.max(0, Math.min(innerHeight - host.offsetHeight, drag.top + ev.screenY - drag.y)) + "px"; host.style.right = "auto"; host.style.bottom = "auto"; };
      const stop = () => { drag = null; removeEventListener("pointermove", move); removeEventListener("pointerup", stop); };
      addEventListener("pointermove", move); addEventListener("pointerup", stop, { once: true });
    }
    if (event.data.type === "BSP_RESIZE") {
      host.style.width = Math.max(350, Math.min(760, Number(event.data.width) || 430)) + "px";
      host.style.height = Math.max(420, Math.min(850, Number(event.data.height) || 620)) + "px";
    }
  });
})();
