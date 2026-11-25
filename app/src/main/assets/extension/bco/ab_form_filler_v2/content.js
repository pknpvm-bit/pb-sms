function visibleTextInputs(root) {
 return Array.from(root.querySelectorAll('input:not([type]),input[type="text"],input[type="search"],textarea'))
  .filter(el=>!el.disabled && !el.readOnly && el.getClientRects().length);
}
function cssEscape(s){return CSS.escape(s);}
function selectorFor(el){
 if(el.id) return "#"+cssEscape(el.id);
 const classes=Array.from(el.classList||[]).filter(Boolean).slice(0,3);
 return el.tagName.toLowerCase()+(classes.length?"."+classes.map(cssEscape).join("."):"");
}
function detectGroups(){
 // Find repeated elements whose descendants contain exactly 11 editable text fields.
 const candidates=Array.from(document.querySelectorAll("body *"))
  .filter(el=>el.children.length>0 && visibleTextInputs(el).length===11);
 // Prefer the smallest qualifying container, then group siblings with the same simple selector.
 const smallest=candidates.filter(el=>!Array.from(el.children).some(ch=>visibleTextInputs(ch).length===11));
 const counts=new Map();
 for(const el of smallest){
   const sel=selectorFor(el);
   const key=(el.parentElement?selectorFor(el.parentElement):"")+" > "+sel;
   if(!counts.has(key)) counts.set(key,{key,selector:sel,parent:el.parentElement,items:[]});
   counts.get(key).items.push(el);
 }
 const groups=Array.from(counts.values()).filter(g=>g.items.length>=2).sort((a,b)=>b.items.length-a.items.length);
 if(!groups.length) return null;
 const best=groups[0];
 // Build a page selector based on the detected parent and child selector.
 const parentSel=best.parent ? selectorFor(best.parent) : "body";
 return {row: parentSel+" > "+best.selector, fields:'input:not([type]),input[type="text"],input[type="search"],textarea', count:best.items.length};
}
function setValue(el,value){
 const proto=el instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
 const setter=Object.getOwnPropertyDescriptor(proto,"value")?.set;
 if(setter) setter.call(el,value); else el.value=value;
 el.dispatchEvent(new Event("input",{bubbles:true}));
 el.dispatchEvent(new Event("change",{bubbles:true}));
}
function shuffle(a){for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}
chrome.runtime.onMessage.addListener((msg,sender,sendResponse)=>{
 try{
  if(msg.type==="AB_AUTO_DETECT"){
   const d=detectGroups();
   if(!d){sendResponse({message:"Auto-detect could not confidently find repeated groups of exactly 11 text fields. Use Manual selector settings, or ask me to adapt it for this page."});return true;}
   chrome.storage.local.set({row:d.row,fields:d.fields});
   sendResponse({message:`Detected about ${d.count} repeated form group(s). Selector saved. Check the page before filling.`});
   return true;
  }
  if(msg.type==="AB_FILL"){
   const settings=msg.settings||{};
   if(!settings.row||!settings.fields){sendResponse({message:"First click Auto Detect, or enter and save selectors in Manual selector settings."});return true;}
   const rows=Array.from(document.querySelectorAll(settings.row)).slice(0,settings.limit||100);
   let filled=0,skipped=0;
   rows.forEach((row,index)=>{
    const fields=Array.from(row.querySelectorAll(settings.fields))
      .filter(el=>(el instanceof HTMLInputElement||el instanceof HTMLTextAreaElement)&&!el.disabled&&!el.readOnly&&el.getClientRects().length)
      .slice(0,11);
    if(fields.length!==11){skipped++;return;}
    const bCount=Math.min(11,Math.floor(index/10)+2);
    const vals=shuffle([...Array(11-bCount).fill("A"),...Array(bCount).fill("B")]);
    fields.forEach((f,i)=>setValue(f,vals[i]));filled++;
   });
   sendResponse({message:`Filled ${filled} form(s); skipped ${skipped}. Nothing was submitted. Please review all entries before submitting on the website.`});
   return true;
  }
 }catch(e){sendResponse({message:"Error: "+e.message});return true;}
});
