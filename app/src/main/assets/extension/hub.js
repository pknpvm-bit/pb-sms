const tools=[
 {key:'formative',name:'FORMATIVE FILLER',desc:'Automatic marks filling',icon:'✎',url:'formative/popup.html'},
 {key:'talk',name:'TALK TO FILLER',desc:'Voice-controlled marks entry',icon:'🎙',url:'talk/popup.html'},
 {key:'lpcd',name:'LPCD FILLER',desc:'Qualitative data auto & manual',icon:'▤',url:'lpcd/popup.html'},
 {key:'bco',name:'BCO FILLER',desc:'A/B form group filler',icon:'▦',url:'bco/ab_form_filler_v2/popup.html'},
 {key:'excel',name:'EXCEL FILLER',desc:'Excel / CSV to webpage',icon:'▥',url:'excel/popup.html'},
 {key:'marksheet',name:'BULK MARKSHEET DOWNLOADER',desc:'Automatic & Manual HRPC Marksheet Download',icon:'⬇',url:'bulk_marksheet/popup.html',nativeAction:'BSP_OPEN_BULK_MARKSHEET'},
 {key:'result_ready',name:'RESULT READY TOOL',desc:'Extract Summative Evaluation to Excel (.xlsx)',icon:'📊',url:'result_ready/popup.html',nativeAction:'BSP_OPEN_RESULT_READY'},
 {key:'hprc_scan',name:'HPRC SCAN TOOL',desc:'Live OCR scan to Excel & safe auto-fill',icon:'📷',url:'hprc_scan/popup.html',nativeAction:'BSP_OPEN_HPRC_SCAN'}
];
const $=id=>document.getElementById(id);let current=null;
function checkToolAllowed(key){
 try{
  if(window.BspAndroidBridge&&typeof window.BspAndroidBridge.isToolAllowed==='function'){
   return Boolean(window.BspAndroidBridge.isToolAllowed(key));
  }
 }catch(e){}
 return true;
}
function render(filter=''){
 const box=$('cards');box.innerHTML='';
 tools.filter(t=>t.name.toLowerCase().includes(filter.toLowerCase())||t.desc.toLowerCase().includes(filter.toLowerCase())).forEach(t=>{
  const allowed=checkToolAllowed(t.key);
  const b=document.createElement('button');
  b.className=allowed?'card':'card locked';
  b.innerHTML=`<span class="ico">${t.icon}</span><b>${t.name}</b><small>${allowed?t.desc:'Disabled by Admin'}</small>`;
  b.addEventListener('click',()=>openTool(t));
  box.appendChild(b);
 });
}
function openTool(t){
 if(!checkToolAllowed(t.key)){
  if(window.BspAndroidBridge&&window.BspAndroidBridge.notifyHubAction){
   window.BspAndroidBridge.notifyHubAction('BSP_TOOL_DENIED:'+t.key);
  }
  render($('search')?.value||'');
  return;
 }
 chrome.storage.local.set({bspLastTool:t.name});
 if(t.nativeAction){
  if(window.BspAndroidBridge&&window.BspAndroidBridge.notifyHubAction){window.BspAndroidBridge.notifyHubAction(t.nativeAction);return;}
  parent.postMessage({type:t.nativeAction},'*');return;
 }
 current=t;$('toolTitle').textContent=t.name;$('toolFrame').src=t.url;$('mainView').hidden=true;$('toolView').hidden=false;
}
function back(){ $('toolFrame').src='about:blank';$('toolView').hidden=true;$('mainView').hidden=false;current=null;render($('search')?.value||'');}
$('search').addEventListener('input',e=>render(e.target.value));$('backBtn').onclick=back;$('minBtn').onclick=$('toolMin').onclick=()=>parent.postMessage({type:'BSP_MINIMIZE'},'*');$('closeBtn').onclick=()=>parent.postMessage({type:'BSP_CLOSE'},'*');window.addEventListener('message',e=>{if(e.data?.type==='BSP_RESTORE')parent.postMessage({type:'BSP_RESTORE'},'*');});render();

$('miniRestore').onclick=()=>parent.postMessage({type:'BSP_RESTORE'},'*');window.addEventListener('message',e=>{if(e.data?.type==='BSP_MINIMIZED')document.body.classList.add('minimized');if(e.data?.type==='BSP_RESTORED')document.body.classList.remove('minimized');});$('topbar').addEventListener('pointerdown',e=>{if(e.target.closest('button'))return;parent.postMessage({type:'BSP_DRAG_START',x:e.screenX,y:e.screenY},'*');});
