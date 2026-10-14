export function nodesOf(tree) {
  const nodes = [];
  function walk(children, depth) {
    if (!Array.isArray(children) || depth > 32) return;
    for (const node of children) {
      if (!node || typeof node !== 'object') continue;
      if (nodes.length >= 4096) return;
      nodes.push(node);
      walk(node.children, depth + 1);
    }
  }
  walk(tree?.children, 0);
  return nodes;
}

export function mapPoint(rect, tree, x, y) {
  if (![rect.width,rect.height,tree.width,tree.height].every(v=>Number.isFinite(v)&&v>0))throw new Error('WEB_UI_INVALID_COORDINATES');
  return { x:Math.max(0,Math.min(tree.width-0.000001,(x-rect.left)*tree.width/rect.width)),
    y:Math.max(0,Math.min(tree.height-0.000001,(y-rect.top)*tree.height/rect.height)) };
}

export function pendingAfterFrameChange(queue, heldKeys, heldButtons) {
  return queue.filter(e=>e.action===0&&((e.type==='key'&&heldKeys.has(e.key))||(e.type==='button'&&heldButtons.has(e.button))));
}

export function glfwButton(button) {
  if(!Number.isInteger(button)||button<0||button>7)return null;
  return ({0:0,1:2,2:1})[button]??button;
}

export function glfwKey(code) {
  if (/^F([1-9]|1[0-9]|2[0-5])$/.test(code))return 289+Number(code.slice(1));
  if (/^Numpad[0-9]$/.test(code))return 320+Number(code.slice(6));
  if (/^Key[A-Z]$/.test(code))return code.charCodeAt(3);
  if (/^Digit[0-9]$/.test(code))return code.charCodeAt(5);
  const keys={Escape:256,Enter:257,Tab:258,Backspace:259,Insert:260,Delete:261,ArrowRight:262,ArrowLeft:263,
    ArrowDown:264,ArrowUp:265,PageUp:266,PageDown:267,Home:268,End:269,Space:32,
    ShiftLeft:340,ControlLeft:341,AltLeft:342,MetaLeft:343,ShiftRight:344,ControlRight:345,AltRight:346,MetaRight:347,
    Semicolon:59,Equal:61,Comma:44,Minus:45,Period:46,Slash:47,Backquote:96,BracketLeft:91,Backslash:92,BracketRight:93,Quote:39,
    CapsLock:280,ScrollLock:281,NumLock:282,PrintScreen:283,Pause:284,NumpadDecimal:330,NumpadDivide:331,
    NumpadMultiply:332,NumpadSubtract:333,NumpadAdd:334,NumpadEnter:335,NumpadEqual:336};
  return keys[code]??null;
}

export function validBounds(node) {
  return [node.x, node.y, node.width, node.height].every(Number.isFinite)
    && node.width > 0 && node.height > 0;
}

export function nodeTitle(node) {
  if (node.worldId) return `${node.worldName || node.label || '世界'} · ${node.worldId}`;
  if (node.role === 'slot') {
    const item = typeof node.item === 'string' && node.item !== 'minecraft:air' ? node.item : '空';
    return `槽 ${node.slot ?? '?'} · ${item}${node.count > 0 ? ` ×${node.count}` : ''}`;
  }
  return node.label || node.role || '未命名控件';
}

export function canClick(snapshot, node) {
  return snapshot?.ownsLease === true && snapshot.control?.mode === 'TAKEOVER'
    && snapshot.control?.reconsentRequired !== true && node.active !== false && node.visible !== false && node.occluded !== true
    && validBounds(node) && Array.isArray(node.actions) && node.actions.includes('click');
}

export function canAction(snapshot, node, action) {
  return snapshot?.ownsLease === true && snapshot.control?.mode === 'TAKEOVER'
    && snapshot.control?.reconsentRequired !== true && node.active !== false && node.occluded !== true
    && (node.visible !== false || action === 'scroll_into_view')
    && (action === 'scroll_into_view' || validBounds(node)
      || (action === 'type_text' && node.inputRoute === 'screen_keyboard')
      || (action === 'open' && node.inputRoute === 'spectator_open'))
    && Array.isArray(node.actions) && node.actions.includes(action);
}

export function makeAction(snapshot, node, options = {}) {
  if (!canAction(snapshot, node, options.action || 'click')) throw new Error('TAKEOVER_REQUIRED');
  return { nodeId: node.nodeId, frameKey: snapshot.frameKey, action: options.action || 'click',
    button: options.button === 1 ? 1 : 0, shift: options.shift === true,
    ...(options.action === 'type_text' ? { text: options.text || '', replace: options.replace !== false } : {}),
    ...(options.action === 'set_value' ? { value: options.value } : {}),
    ...(options.action === 'scroll' ? { yOffset: options.yOffset || 0 } : {}) };
}

// Spectator icons describe display geometry, not native mouse hit areas.
export function spectatorTarget(tree, point, frameKey, captureFrame) {
  if (captureFrame !== undefined && captureFrame !== frameKey) return null;
  if (tree?.uiInputContext !== 'spectator_menu' || tree.screenClass) return null;
  return nodesOf(tree).find(n => n.role === 'spectator_item' && n.visible !== false && n.active !== false
    && validBounds(n) && point.x >= n.x && point.x < n.x + n.width && point.y >= n.y && point.y < n.y + n.height) ?? null;
}

export function makeCoordinateAction(snapshot, x, y, options = {}) {
  if (snapshot?.ownsLease !== true || snapshot.control?.mode !== 'TAKEOVER' || snapshot.control?.reconsentRequired
      || !snapshot.tree?.screenClass) throw new Error('TAKEOVER_REQUIRED');
  if (!Number.isFinite(x) || !Number.isFinite(y) || x < 0 || y < 0
      || x >= snapshot.tree.width || y >= snapshot.tree.height) throw new Error('WEB_UI_INVALID_COORDINATES');
  return { frameKey: snapshot.frameKey, coordinates: { x, y }, source: 'explicit_coordinate',
    action: 'click', button: options.button === 1 ? 1 : 0, shift: options.shift === true };
}
