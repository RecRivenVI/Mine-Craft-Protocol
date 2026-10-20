import assert from 'node:assert/strict';

const operations = new Set([
  'ui.widget.active.set', 'ui.widget.visible.set', 'ui.text.set', 'ui.scroll.set',
  'ui.slider.set', 'ui.toast.reset', 'ui.toast.hide',
  'ui.canvas.pan.set',
  'ui.list.selection.set',
  'ui.hud.title.times.set', 'ui.hud.title.clear',
]);

export function uiDebugNodes(tree) {
  return [...(tree.children ?? []), ...(tree.presentation?.children ?? [])]
    .flatMap(node => [node, ...uiDebugNodes(node)]);
}

// Run on actual captured trees; declaration-only samples do not count as Live evidence.
export function auditUiDebugCapabilities(debug, user, target) {
  assert.equal(debug.dataCategory, 'DEBUG');
  assert.equal(user.dataCategory, 'USER');
  for (const node of uiDebugNodes(user)) {
    assert(!Object.hasOwn(node, 'debugMutation'), 'USER leaked typed DEBUG applicability');
    assert(!Object.hasOwn(node, 'resourceVersion'), 'USER leaked typed DEBUG mutation version');
    for(const field of ['debugPanReady','debugPanValue','debugPanRange'])assert(!Object.hasOwn(node,field),'USER leaked internal canvas pan metadata');
    for(const field of ['debugSelectionIndex','debugSelectionRange','debugSelectionUnit','debugSelectionGuardAvailable'])assert(!Object.hasOwn(node,field),'USER leaked internal suggestion selection metadata');
    for(const field of ['debugTitleTimes','debugTitleCleared','debugTitleRemainingTicks','debugTitleGuardAvailable'])assert(!Object.hasOwn(node,field),'USER leaked internal title lifecycle metadata');
  }
  const samples = [];
  for (const node of uiDebugNodes(debug)) {
    const support = node.debugMutation;
    assert(support, `Missing actual-owner descriptor on ${node.nodeId}`);
    assert.equal(support.grantsPermission, false);
    assert.equal(support.requiresInputLease, false);
    assert.equal(support.requiresUiArm, true);
    assert.equal(support.requiresResourceVersion, true);
    assert.equal(support.inputDispatchCount, 0);
    assert.deepEqual(support.supportedOperations, Object.keys(support.operations));
    for (const operation of support.supportedOperations) {
      assert(operations.has(operation), `Unknown advertised operation ${operation}`);
      assert(node.resourceVersion, 'A supported setter has no actual Resource Version');
      assert.notEqual(node.debugTextGuardAvailable, false);
      assert.notEqual(node.debugToastGuardAvailable, false);
      assert(!Object.hasOwn(support.blockedOperations, operation));
    }
    if (node.debugToastKind === 'system' && target === '1.20.1-forge') {
      assert(!support.supportedOperations.includes('ui.toast.hide'));
      assert.equal(support.limitation, 'NATIVE_SYSTEM_TOAST_HAS_NO_FORCE_HIDE');
    }
    if (node.debugToastKind === 'tutorial') {
      assert.equal(support.supportedOperations.includes('ui.slider.set'), node.debugProgressable);
    }
    if (support.operations['ui.scroll.set']) {
      const spec = support.operations['ui.scroll.set'];
      assert(['normalized', 'native_pixels'].includes(spec.unit));
      if (node.controlKey?.endsWith('.scrollbar.track')) {
        assert.equal(spec.unit, 'normalized');
        assert.equal(spec.maximum, node.active ? 1 : 0);
      }
    }
    if (support.operations['ui.toast.hide']) assert.equal(support.operations['ui.toast.hide'].onlyValue, true);
    if (support.operations['ui.canvas.pan.set']) {
      const spec=support.operations['ui.canvas.pan.set'];
      assert.equal(node.debugPanReady,true);assert.equal(spec.valueType,'object');
      assert.equal(spec.unit,'native_gui_pixels');assert.equal(spec.requiredShape,'explicit_finite_x_and_y');
      assert.deepEqual(spec.range,node.debugPanRange);
      for(const axis of ['x','y']){assert(Number.isFinite(node.debugPanValue[axis]));assert(Number.isFinite(spec.range[axis].minimum));assert(Number.isFinite(spec.range[axis].maximum));}
    }
    samples.push({
      nodeId: node.nodeId, nativeClass: node.class, controlKey: node.controlKey,
      operations: support.supportedOperations, blocked: support.blockedOperations,
      ...(support.limitation ? { limitation: support.limitation } : {}),
    });
  }
  return samples;
}

export function auditAppliedUiMutation(result) {
  assert.equal(result.dataCategory, 'DEBUG');
  assert.equal(result.evidenceAuthority, 'DEBUG_PRIVILEGED');
  assert.equal(result.inputDispatchCount, 0);
  assert(result.before?.debugMutation?.supportedOperations.includes(result.operation),
    `Real setter ${result.operation} was not advertised by its actual owner`);
  return { operation: result.operation, nativeDescriptorMatchedActualSetter: true, inputDispatchCount: 0 };
}
