const $=id=>document.getElementById(id);
$("manualToggle").onclick=()=>{$("manual").hidden=!$("manual").hidden};
async function getSettings(){
 const saved=await chrome.storage.local.get({row:"",fields:""});
 return {row:$("row").value.trim()||saved.row,fields:$("fields").value.trim()||saved.fields,limit:Math.max(1,Math.min(100,Number($("limit").value)||100))};
}
(async()=>{const s=await chrome.storage.local.get({row:"",fields:""});$("row").value=s.row;$("fields").value=s.fields;})();
$("save").onclick=async()=>{await chrome.storage.local.set({row:$("row").value.trim(),fields:$("fields").value.trim()});$("status").textContent="Selectors saved for reuse. You can change them on another website.";};
async function send(type){
 const [tab]=await chrome.tabs.query({active:true,currentWindow:true});
 if(!tab?.id){$("status").textContent="No active tab.";return;}
 try{
  const result=await chrome.tabs.sendMessage(tab.id,{type,settings:await getSettings()});
  $("status").textContent=result?.message||"Done.";
 }catch(e){$("status").textContent="Cannot read this page. Refresh the website and try again. Some browser-internal pages cannot be accessed.";}
}
$("detect").onclick=()=>send("AB_AUTO_DETECT");
$("fill").onclick=()=>send("AB_FILL");
