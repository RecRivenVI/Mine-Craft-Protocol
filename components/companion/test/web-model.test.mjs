import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { nodesOf, validBounds, nodeTitle, canClick, canAction, makeAction, makeCoordinateAction, mapPoint, glfwKey, glfwButton, pendingAfterFrameChange, spectatorTarget } from '../web/model.mjs';

const node = { nodeId: 'opaque:do-not-parse', role: 'slot', slot: 47, nativeSlotIndex: 0,
  x: 3.5, y: 7.25, width: 18, height: 18, item: 'minecraft:stone', count: 64, actions: ['click'] };
const snapshot = { ownsLease: true, frameKey: 'exact-frame', control: { mode: 'TAKEOVER', reconsentRequired: false } };

test('spectator display-only icons resolve to semantic shortcuts, never first-person coordinates',()=>{
  const item={...node,role:'spectator_item',inputRoute:'spectator_hotbar',boundsSemantics:'display_only',actions:['select','click']};
  const tree={screenClass:'',uiInputContext:'spectator_menu',children:[item]};
  assert.equal(spectatorTarget(tree,{x:4,y:8}),item);
  assert.equal(spectatorTarget(tree,{x:4,y:8},'new-menu','old-menu'),null);
  assert.equal(spectatorTarget(tree,{x:4,y:8},'current','current'),item);
  assert.equal(spectatorTarget(tree,{x:21.5,y:8}),null);
  assert.equal(spectatorTarget({...tree,screenClass:'PauseScreen'},{x:4,y:8}),null);
  assert.equal(makeAction(snapshot,item,{action:'select'}).nodeId,item.nodeId);
  assert.throws(()=>makeAction(snapshot,item,{action:'activate'}));
  assert.throws(()=>makeCoordinateAction({...snapshot,tree},4,8));
  const open={nodeId:'open',inputRoute:'spectator_open',boundsAvailable:false,actions:['open']};
  assert(canAction(snapshot,open,'open'));assert(!canClick(snapshot,open));
});

test('list containers do not impersonate rows; nested worlds remain independent semantic targets', () => {
  const list = {...node, nodeId:'list', role:'list', actions:['hover','scroll'], children:[
    {...node,nodeId:'world-a',role:'list_item',worldId:'a'}, {...node,nodeId:'world-b',role:'list_item',worldId:'b'}
  ]};
  const nodes = nodesOf({children:[list]});
  assert.equal(canClick(snapshot, nodes[0]), false);
  assert.equal(canClick(snapshot, nodes[1]), true);
  assert.equal(makeAction(snapshot, nodes[2]).nodeId, 'world-b');
  const hidden = {...node,visible:false,actions:['scroll_into_view']};
  assert(canAction(snapshot, hidden, 'scroll_into_view'));
  assert.equal(makeAction(snapshot, hidden, {action:'scroll_into_view'}).action, 'scroll_into_view');
  const notLaidOut = {...hidden, width: 0, height: 0, boundsAvailable: false};
  assert(canAction(snapshot, notLaidOut, 'scroll_into_view'));
  assert.equal(canClick(snapshot, notLaidOut), false);
  assert.throws(()=>makeAction(snapshot, list));
});

test('web layout keeps exact API bounds and semantic slot identity, including duplicate native indices', () => {
  const tree = { children: [node, { ...node, nodeId: 'another', slot: 48, nativeSlotIndex: 0 }] };
  const nodes = nodesOf(tree);
  assert.deepEqual(nodes.map(n => n.slot), [47, 48]); assert.equal(nodes[0].x, 3.5); assert.equal(nodes[0].y, 7.25);
  assert(validBounds(node)); assert(!validBounds({ ...node, width: 0 })); assert(!validBounds({ ...node, x: NaN }));
  assert.equal(nodeTitle(node), '槽 47 · minecraft:stone ×64');
  assert.deepEqual(makeAction(snapshot, node, { shift: true }), {
    nodeId: 'opaque:do-not-parse', frameKey: 'exact-frame', action: 'click', button: 0, shift: true
  });
});

test('typed native keyboard editors can receive text without an invented click area', () => {
  const field = {nodeId:'sign-line-2',role:'text_field',controlKey:'sign.line.2',
    inputRoute:'screen_keyboard',visible:true,active:true,boundsAvailable:false,actions:['type_text']};
  assert(canAction(snapshot, field, 'type_text'));
  assert.equal(canClick(snapshot, field), false);
  assert.equal(makeAction(snapshot, field, {action:'type_text',text:'Line 3'}).nodeId, 'sign-line-2');
  assert.equal(canAction({...snapshot,control:{mode:'READ'}},field,'type_text'),false);
});

test('web model never upgrades READ, OPERATE, revoked or other-owner sessions', () => {
  for (const state of [{ ...snapshot, ownsLease: false }, { ...snapshot, control: { mode: 'READ' } },
    { ...snapshot, control: { mode: 'OPERATE' } }, { ...snapshot, control: { mode: 'TAKEOVER', reconsentRequired: true } }]) {
    assert.equal(canClick(state, node), false); assert.throws(() => makeAction(state, node));
  }
  assert.equal(canClick(snapshot, { ...node, visible: false }), false);
  assert.equal(canClick(snapshot, { ...node, active: false }), false);
  assert.equal(canClick(snapshot, { ...node, occluded: true }), false);
  assert.equal(canAction(snapshot, { ...node, occluded: true }, 'click'), false);
});

test('web frontend treats Runtime labels as data and screenshot is an explicit standard capture action', async () => {
  const code = await readFile(new URL('../web/app.mjs', import.meta.url), 'utf8');
  assert(!code.includes('innerHTML')); assert(!code.includes('eval(')); assert(code.includes('textContent'));
  assert(code.includes("api('capture',{},true)")); assert(code.includes('captureFrame!==value.frameKey'));
  assert(code.includes("api('input',")); assert(code.includes("stage.addEventListener('pointerdown'"));
  assert(code.includes("stage.addEventListener('pointerup'")); assert(code.includes("stage.addEventListener('wheel'"));
  assert(code.includes('rect.left+stage.clientLeft')); assert(code.includes('width:stage.clientWidth'));
});

test('web explicit coordinate opt-in preserves fractional GUI positions and never works in READ or Gameplay', () => {
  const state = { ...snapshot, tree: { screenClass: 'SelectWorldScreen', width: 427, height: 240 } };
  assert.deepEqual(makeCoordinateAction(state, 426.75, 239.25).coordinates, { x: 426.75, y: 239.25 });
  assert.throws(() => makeCoordinateAction({ ...state, ownsLease: false }, 10, 10));
  assert.throws(() => makeCoordinateAction({ ...state, tree: { ...state.tree, screenClass: '' } }, 10, 10));
  assert.throws(() => makeCoordinateAction(state, 427, 10));
});

test('game viewport mapping preserves exact fractional GUI coordinates at any CSS size',()=>{
 const rect={left:100,top:50,width:960,height:540},tree={width:480,height:270};
 assert.deepEqual(mapPoint(rect,tree,121.5,90.25),{x:10.75,y:20.125});
 assert.equal(glfwKey('ArrowLeft'),263);assert.equal(glfwKey('KeyA'),65);assert.equal(glfwKey('F12'),301);
 assert.equal(glfwKey('Numpad7'),327);assert.equal(glfwKey('Unrecognized'),null);
 assert.equal(glfwButton(0),0);assert.equal(glfwButton(1),2);assert.equal(glfwButton(2),1);assert.equal(glfwButton(3),3);assert.equal(glfwButton(99),null);
});

test('Screen change discards queued input but retains cleanup only for actually dispatched presses',()=>{
 const events=[{type:'move',x:10,y:20},{type:'text',text:'late'},{type:'key',key:340,action:0},
  {type:'key',key:65,action:0},{type:'button',button:0,action:0},{type:'button',button:1,action:1}];
 assert.deepEqual(pendingAfterFrameChange(events,new Set([340]),new Set([0])),[events[2],events[4]]);
 assert.deepEqual(pendingAfterFrameChange(events,new Set(),new Set()),[]);
});
