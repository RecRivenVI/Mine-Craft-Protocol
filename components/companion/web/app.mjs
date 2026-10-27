import { nodesOf, validBounds, nodeTitle, mapPoint, glfwKey, glfwButton, pendingAfterFrameChange, spectatorTarget } from './model.mjs';

const $ = id => document.getElementById(id);
let browserToken='', snapshot, captureUrl='', captureFrame='', captureBusy=false, polling=false;
let sequence=0, sending=false, composing=false, queue=[], lastMoveSent=0, spectatorBusy=false;
const stage=$('stage'), keyboard=$('keyboard');
const heldKeys=new Set(),heldButtons=new Set();
const notice = text => { $('notice').textContent=text; $('notice').hidden=!text; };
const modifiers = e => (e.shiftKey?1:0)|(e.ctrlKey?2:0)|(e.altKey?4:0)|(e.metaKey?8:0);
async function api(path, body, binary=false) {
  const response=await fetch('/api/'+path,{method:body===undefined?'GET':'POST',
    headers:{'x-web-ui-token':browserToken,...(body===undefined?{}:{'content-type':'application/json'})},
    ...(body===undefined?{}:{body:JSON.stringify(body)}),keepalive:path==='release',signal:AbortSignal.timeout(30000)});
  if(!response.ok){const e=await response.json();throw Object.assign(new Error(e.error+': '+(e.message||'请求被拒绝')),e);}
  return binary?response:response.json();
}
function render(value) {
  if(value.headless) {
    snapshot=value;$('console').hidden=false;stage.hidden=true;$('screenshots').hidden=true;
    for(const id of ['acquire','release','escape','inventory'])$(id).hidden=true;
    $('status').textContent=(value.session.target||'Minecraft')+' · '+value.control.mode+' · 独立服务器';
    $('console-output').textContent=value.console.records?.map(record=>`[${record.level}] ${record.text}`).join('\n')||value.console.message||'等待服务端日志';
    $('console-input').disabled=value.control.mode!=='OPERATE';
    return;
  }
  if(snapshot&&snapshot.inputFrameKey!==value.inputFrameKey)queue=pendingAfterFrameChange(queue,heldKeys,heldButtons);
  snapshot=value;
  if(!sending&&!queue.length)sequence=value.inputSequence??0;
  $('status').textContent=(value.session.target||'Minecraft')+' · '+value.control.mode+' · '+(value.tree.title||value.tree.screenClass?.split('.').pop()||'Gameplay');
  $('acquire').disabled=!!value.otherBrowserOwnsLease||value.control.mode==='TAKEOVER';
  $('acquire').textContent=value.control.reconsentRequired?'重新接管':'接管';
  $('release').disabled=!value.ownsLease;$('escape').disabled=!value.ownsLease;
  $('inventory').disabled=!value.ownsLease||!value.session.inWorld;
  stage.style.aspectRatio=value.tree.width+' / '+value.tree.height;
  $('surface').setAttribute('viewBox','0 0 '+value.tree.width+' '+value.tree.height);
  $('surface').replaceChildren();
  if($('show-structure').checked)for(const node of nodesOf(value.tree)) {
    if(node.visible===false||!validBounds(node))continue;
    const rect=document.createElementNS('http://www.w3.org/2000/svg','rect');
    for(const field of ['x','y','width','height'])rect.setAttribute(field,String(node[field]));
    const title=document.createElementNS('http://www.w3.org/2000/svg','title');title.textContent=nodeTitle(node);rect.append(title);$('surface').append(rect);
    if(node.width>=25&&node.height>=8&&node.label){const text=document.createElementNS('http://www.w3.org/2000/svg','text');text.setAttribute('x',String(node.x+node.width/2));text.setAttribute('y',String(node.y+node.height/2));text.textContent=node.label.slice(0,80);$('surface').append(text);}
  }
  if(value.control.mode!=='TAKEOVER'){queue=[];heldKeys.clear();heldButtons.clear();}
  if(value.control.reconsentRequired) { queue=[];notice('用户手动结束接管。观察继续；再次接管需要重新明确同意，不会自动恢复。'); }
  if(captureUrl&&captureFrame!==value.frameKey)$('capture-status').textContent='界面已更新，正在刷新画面';
}
async function refresh() {
  if(polling)return;polling=true;
  try{render(await api('snapshot'));}catch(e){queue=[];notice(e.message);}finally{polling=false;}
}
async function capture() {
  if(snapshot?.headless)return;
  if(captureBusy)return;captureBusy=true;
  try{
    const response=await api('capture',{},true),frame=decodeURIComponent(response.headers.get('x-web-ui-frame')||'');
    const blob=await response.blob();await refresh();
    if(frame!==snapshot?.frameKey)return;
    const url=URL.createObjectURL(blob);if(captureUrl)URL.revokeObjectURL(captureUrl);
    captureUrl=url;captureFrame=frame;$('background').src=url;$('background').hidden=false;
    $('save-capture').disabled=$('clear-capture').disabled=false;
    $('capture-status').textContent='当前截图 · '+new Date().toLocaleTimeString('zh-CN');
  }catch(e){if(!$('auto-capture').checked)notice(e.message);}finally{captureBusy=false;}
}
function allowed(cleanup=false) {
  const owner=snapshot?.ownsLease&&snapshot.control.mode==='TAKEOVER'&&!snapshot.control.reconsentRequired;
  return owner&&(cleanup||(snapshot.tree.screenClass&&(!captureUrl||captureFrame===snapshot.frameKey)));
}
function point(e){const rect=stage.getBoundingClientRect();return mapPoint({left:rect.left+stage.clientLeft,top:rect.top+stage.clientTop,width:stage.clientWidth,height:stage.clientHeight},snapshot.tree,e.clientX,e.clientY);}
function enqueue(event) {
  if(!allowed(event.action===0&&(event.type==='button'||event.type==='key')))return;
  if(queue.length>=64){queue=[];void reset();notice('输入超出队列预算，已释放按键；请减慢操作');return;}
  event.inputFrameKey=snapshot.inputFrameKey;
  if(event.type==='move'&&queue.at(-1)?.type==='move')queue[queue.length-1]=event;
  else queue.push(event);
  void drain();
}
async function drain() {
  if(sending)return;sending=true;
  try{
    while(queue.length&&allowed(true)){
      if(queue[0].type==='move')await new Promise(r=>setTimeout(r,Math.max(0,50-(performance.now()-lastMoveSent))));
      if(!queue.length||!allowed(true))break;
      const event=queue.shift();
      if(event.type==='move')lastMoveSent=performance.now();
      const held=event.type==='key'?heldKeys:event.type==='button'?heldButtons:null;
      const identity=event.type==='key'?event.key:event.button;
      if(held&&event.action!==0)held.add(identity);
      const value=await api('input',{...event,sequence:++sequence});
      if(held&&event.action===0)held.delete(identity);
      if(value.snapshot)render(value.snapshot);
    }
  }catch(e){queue=[];notice(e.message);await reset();await refresh();}
  finally{sending=false;}
}
async function reset(){
  queue=[];
  heldKeys.clear();heldButtons.clear();
  if(!snapshot?.ownsLease)return;
  try{await api('input/reset',{});}catch{}
}
function spectatorContext() { return allowed(true) && !snapshot.tree.screenClass && snapshot.tree.uiInputContext?.startsWith('spectator'); }
async function spectatorAction(node, action) {
  if (!spectatorContext() || !node?.actions?.includes(action) || spectatorBusy) return;
  spectatorBusy=true;
  try { const result=await api('action',{nodeId:node.nodeId,frameKey:snapshot.frameKey,action});render(result.snapshot); }
  catch(e){notice(e.message);await refresh();}
  finally{spectatorBusy=false;}
}
stage.addEventListener('pointermove',e=>{if(!allowed())return;enqueue({type:'move',...point(e)});});
stage.addEventListener('pointerdown',e=>{
  if(spectatorContext()){
    e.preventDefault();keyboard.focus({preventScroll:true});
    const node=spectatorTarget(snapshot.tree,point(e),snapshot.frameKey,captureUrl?captureFrame:undefined);
    if(e.button===0&&node)void spectatorAction(node,'select');
    else if(e.button===1)void spectatorAction(nodesOf(snapshot.tree).find(n=>n.inputRoute==='spectator_open'),'open');
    return;
  }
  if(allowed(true)&&!snapshot.tree.screenClass){e.preventDefault();keyboard.focus({preventScroll:true});return;}
  const button=glfwButton(e.button);if(!allowed()||button===null)return;e.preventDefault();stage.setPointerCapture(e.pointerId);keyboard.focus({preventScroll:true});
  enqueue({type:'button',...point(e),button,action:1,modifiers:modifiers(e)});
});
stage.addEventListener('pointerup',e=>{
  if(spectatorContext()){e.preventDefault();return;}
  const button=glfwButton(e.button);if(!allowed(true)||button===null)return;e.preventDefault();enqueue({type:'button',...point(e),button,action:0,modifiers:modifiers(e)});
  if(stage.hasPointerCapture(e.pointerId))stage.releasePointerCapture(e.pointerId);
});
stage.addEventListener('pointercancel',()=>void reset());
stage.addEventListener('contextmenu',e=>e.preventDefault());
stage.addEventListener('auxclick',e=>e.preventDefault());
stage.addEventListener('dblclick',e=>{
  if(!spectatorContext()||e.button!==0)return;e.preventDefault();
  const node=spectatorTarget(snapshot.tree,point(e),snapshot.frameKey,captureUrl?captureFrame:undefined);if(node?.selected)void spectatorAction(node,'activate');
});
stage.addEventListener('wheel',e=>{
  if(spectatorContext()){
    e.preventDefault();
    const items=nodesOf(snapshot.tree).filter(n=>n.role==='spectator_item'&&n.active&&n.visible&&n.activationEnabled);
    const current=nodesOf(snapshot.tree).find(n=>n.role==='spectator_item'&&n.selected)?.entryIndex??-1;
    const next=e.deltaY>0?items.find(n=>n.entryIndex>current):items.toReversed().find(n=>n.entryIndex<current);
    if(next)void spectatorAction(next,'select');return;
  }
  if(!allowed())return;e.preventDefault();
  enqueue({type:'scroll',...point(e),xOffset:Math.max(-20,Math.min(20,-e.deltaX/60)),yOffset:Math.max(-20,Math.min(20,-e.deltaY/60))});
},{passive:false});
keyboard.addEventListener('keydown',e=>{
  if(spectatorContext()&&!['Escape','KeyE'].includes(e.code)){
    e.preventDefault();if(e.repeat||e.isComposing)return;
    const items=nodesOf(snapshot.tree);
    const selected=items.find(n=>n.role==='spectator_item'&&n.selected);
    const bound=items.find(n=>n.inputRoute==='spectator_hotbar'&&n.inputKey===glfwKey(e.code));
    if(e.code==='Enter'&&selected)void spectatorAction(selected,'activate');
    else if(bound)void spectatorAction(bound,bound.selected&&bound.activationEnabled?'activate':'select');
    return;
  }
  if(allowed(true)&&!snapshot.tree.screenClass){
    if(['Escape','KeyE'].includes(e.code)){e.preventDefault();if(!e.repeat)void shortcut(e.code==='Escape'?'Escape':'Inventory');}
    return;
  }
  if(!allowed())return;
  if((e.ctrlKey||e.metaKey)&&['KeyV','KeyW','KeyL','KeyT','KeyN','KeyR'].includes(e.code))return;
  if(e.key==='Tab'||e.key==='Escape'||e.key==='Enter'||e.key.startsWith('Arrow')||e.key==='Backspace'||e.key==='Delete'||e.ctrlKey||e.metaKey)e.preventDefault();
  if(composing||e.isComposing)return;
  const key=glfwKey(e.code);if(key!==null)enqueue({type:'key',key,action:e.repeat?2:1,modifiers:modifiers(e)});
});
keyboard.addEventListener('keyup',e=>{
  if(!allowed(true)||composing||e.isComposing)return;
  if((e.ctrlKey||e.metaKey)&&['KeyV','KeyW','KeyL','KeyT','KeyN','KeyR'].includes(e.code))return;
  const key=glfwKey(e.code);if(key!==null&&(heldKeys.has(key)||queue.some(v=>v.type==='key'&&v.key===key&&v.action!==0)))enqueue({type:'key',key,action:0,modifiers:modifiers(e)});
});
keyboard.addEventListener('beforeinput',e=>{
  if(composing||e.isComposing)return;
  if(e.inputType==='insertText'||e.inputType==='insertFromPaste'){e.preventDefault();if(e.data)enqueue({type:'text',text:e.data});}
});
keyboard.addEventListener('paste',e=>{e.preventDefault();const text=e.clipboardData?.getData('text/plain');if(text)enqueue({type:'text',text});});
keyboard.addEventListener('compositionstart',()=>{composing=true;});
keyboard.addEventListener('compositionend',e=>{composing=false;keyboard.value='';if(e.data)enqueue({type:'text',text:e.data});});
window.addEventListener('blur',()=>void reset());
document.addEventListener('visibilitychange',()=>{if(document.hidden)void reset();});
window.addEventListener('pagehide',()=>{queue=[];void api('release',{}).catch(()=>{});});
$('acquire').addEventListener('click',async()=>{
  if(snapshot?.control.reconsentRequired&&!confirm('您是否明确同意重新接管这个 Minecraft 实例？'))return;
  try{render(await api('acquire',{}));notice('');await capture();}catch(e){notice(e.message);}
});
$('release').addEventListener('click',async()=>{queue=[];try{render(await api('release',{}));}catch(e){notice(e.message);}});
async function shortcut(key){
  queue=[];await reset();try{const r=await api('key',{key});render(r.snapshot);await capture();}catch(e){notice(e.message);}
}
for(const [id,key] of [['escape','Escape'],['inventory','Inventory']])$(id).addEventListener('click',()=>void shortcut(key));
$('capture').addEventListener('click',()=>void capture());
$('show-structure').addEventListener('change',()=>snapshot&&render(snapshot));
$('clear-capture').addEventListener('click',()=>{
  $('auto-capture').checked=false;if(captureUrl)URL.revokeObjectURL(captureUrl);
  captureUrl=captureFrame='';$('background').hidden=true;$('background').removeAttribute('src');
  $('save-capture').disabled=$('clear-capture').disabled=true;$('capture-status').textContent='无截图 · 控件结构可独立显示';
});
$('save-capture').addEventListener('click',()=>{
  if(!captureUrl)return;const a=document.createElement('a');a.href=captureUrl;a.download='minecraft-'+Date.now()+'.png';a.click();
});
for(const [id,mode] of [['console-read','READ'],['console-operate','OPERATE']])$(id).addEventListener('click',async()=>{
  try{await api('console',{action:'set_mode',mode});await refresh();notice('');}catch(error){notice(error.message);}
});
$('console-form').addEventListener('submit',async event=>{
  event.preventDefault();try{const result=await api('console',{action:'execute',command:$('console-input').value});
    $('console-result').textContent=JSON.stringify(result,null,2);await refresh();}catch(error){notice(error.message);}
});
$('console-complete').addEventListener('click',async()=>{
  try{const input=$('console-input'),result=await api('console',{action:'complete',input:input.value,cursor:input.selectionStart??input.value.length});
    $('console-suggestions').replaceChildren();for(const item of result.suggestions??[]){const button=document.createElement('button');button.type='button';button.textContent=item.text;
      button.addEventListener('click',()=>{input.value=input.value.slice(0,item.start)+item.text+input.value.slice(item.end);input.focus();});$('console-suggestions').append(button);}
  }catch(error){notice(error.message);}
});
try{
  const bootstrap=await(await fetch('/api/bootstrap')).json();browserToken=bootstrap.browserToken;
  await refresh();await capture();
  setInterval(()=>void refresh(),700);
  setInterval(()=>{if($('auto-capture').checked&&!document.hidden)void capture();},900);
}catch(e){notice(e.message);}
