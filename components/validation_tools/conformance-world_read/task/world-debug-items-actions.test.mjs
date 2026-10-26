import assert from 'node:assert/strict';
import test from 'node:test';
import {closeItemFixtureUi} from './world-debug-items-actions.mjs';

test('item cleanup follows the actual older parent Pause and modern direct close without blind extra Esc', async () => {
  for(const screens of [['AutomationProbeScreen','PauseScreen',''],['AutomationProbeScreen',''],['PauseScreen',''],['']]) {
    let index = 0;
    await closeItemFixtureUi({read:async () => ({screenClass:screens[index]}),
      escape:async () => {index++;assert(index < screens.length);},
      wait:async predicate => {const tree={screenClass:screens[index]};assert(predicate(tree));return tree;}});
    assert.equal(index,screens.length-1);
  }
});

test('item cleanup preserves an unexpected native Screen instead of sending arbitrary Escape', async () => {
  await assert.rejects(closeItemFixtureUi({read:async () => ({screenClass:'OptionsScreen'}),
    escape:async () => assert.fail('Unexpected screen must not receive input'),wait:async () => assert.fail()}), /Unexpected Screen/);
});
