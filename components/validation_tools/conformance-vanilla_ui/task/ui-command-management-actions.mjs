import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const TARGETS = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];
const ARRANGE = 'DIAGNOSTIC_current_player_commands_owned_copy_only';
const PREFIX = 'say Mine-Craft-Protocol_command_configuration_';
const digest = text => createHash('sha256').update(text).digest('hex');

// Uses the existing server receipt probe only as an independent diagnostic witness.
// All Screen entry, drafts and attempted submissions still use the USER input path.
export async function uiCommandManagementActions({api, repo, target, processId, command, key, tree, wait, nodes, ui, enterWorld, check, samples, readProbe},
  {now = () => performance.now(), sleep = milliseconds => new Promise(done => setTimeout(done, milliseconds))} = {}) {
  assert(TARGETS.includes(target)); assert(Number.isSafeInteger(processId) && processId > 0);
  const modern = !target.startsWith('1.'), started = now(), tag = 'ui_management_' + randomUUID().replaceAll('-', '');
  const path = resolve(repo, 'validations/conformance-vanilla_ui/result', target + '-command-configuration-probe.json');
  let lastPipeline = -Infinity, ownedCart = false, ownedBlock = false, primary, fixtureReady = false, ownsCleanup = false;
  const ownedScreens = ['MinecartCommandBlockEditScreen', 'TestBlockEditScreen', 'TestInstanceBlockEditScreen'];
  const healthy = () => assert(now() - started < 180000, 'Bounded command-management UI exceeded 180 seconds');
  async function clean() {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  }
  async function page(predicate, description) {
    const value = await wait(value => {healthy(); return predicate(value);}, description);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); return value;
  }
  async function operation(steps) {
    healthy(); const interval = 550 - (now() - lastPipeline); if (interval > 0) await sleep(interval); lastPipeline = now();
    const accepted = await api('POST', '/v0/pipelines', {steps, timeoutMs: 10000});
    const value = await api('POST', '/v0/operations/' + accepted.operationId + '/wait', {timeoutMs: 12000});
    assert(['completed', 'failed'].includes(value.state), 'Operation must reach a terminal state: ' + JSON.stringify(value)); return value;
  }
  async function marker() {
    const attemptId = randomUUID().replaceAll('-', ''); await command(PREFIX + attemptId);
    const deadline = Math.min(started + 180000, now() + 5000); let last;
    do {
      healthy();
      try {last = readProbe ? await readProbe() : JSON.parse(await readFile(path, 'utf8'));}
      catch (error) {if (error.code !== 'ENOENT' && !(error instanceof SyntaxError)) throw error;}
      if (last?.attemptId === attemptId) {
        assert.equal(last.probeOnly, true); assert.equal(last.validationName, 'conformance-vanilla_ui');
        assert.equal(last.target, target); assert.equal(last.processId, processId, 'Bind server observation to this Runtime process');
        assert.equal(last.fixtureAuthority, 'DIAGNOSTIC_native_server_receipt_observation');
        assert.equal(last.worldName, 'UI-Conformance'); assert.equal(last.singlePrivatePlayerBound, true);
        assert.equal(last.chunkLoadRequested, false); assert.equal(last.worldMutationUsedByProbe, false);
        for (const name of ['commandMinecartPackets', ...(modern ? ['testBlockPackets', 'testInstanceActionPackets'] : [])])
          assert(Number.isSafeInteger(last[name]) && last[name] >= 0, 'Actual native receipt counter: ' + name);
        assert(Number.isSafeInteger(last.nativePlayerCommands)); return last;
      }
      await sleep(25);
    } while (now() < deadline);
    assert.fail('Current server FIFO witness was not observed: ' + JSON.stringify(last));
  }
  function unchanged(before, after, stored) {
    assert.equal(after.nativePlayerCommands, before.nativePlayerCommands + 1, 'Only the subsequent FIFO marker is a player command');
    for (const field of ['commandBlockPackets', 'commandMinecartPackets', 'jigsawConfigurationPackets', 'jigsawGenerationPackets',
      'structureUpdatePackets', 'structureScanPackets', 'structureSavePackets', 'structureLoadPackets',
      ...(modern ? ['testBlockPackets', 'testInstanceActionPackets'] : []), ...stored]) {
      assert.equal(after[field], before[field], 'A rejected UI action changed native server evidence: ' + field);
    }
  }
  async function field(translationKey, text) {
    const value = await tree(); const matches = nodes(value).filter(node => node.role === 'text_field'
      && (translationKey === undefined || node.translationKey === translationKey) && node.visible !== false
      && node.active !== false && node.actions?.includes('type_text'));
    assert.equal(matches.length, 1, 'One actual current native management draft');
    return {value, node: matches[0], steps: [{type: 'ui.action', selector: {nodeId: matches[0].nodeId}, action: 'type_text', text}]};
  }
  async function deny(steps) {
    const result = await operation(steps); assert.equal(result.state, 'failed', JSON.stringify(result));
    assert.equal(result.errorCode, 'PLAYER_COMMAND_OUT_OF_SCOPE', JSON.stringify(result));
    assert.equal(result.result?.commandSubmissions?.length ?? 0, 0); return result;
  }
  async function deniedButton(translationKey) {
    const value = await tree(), choices = nodes(value).filter(node => node.translationKey === translationKey
      && node.visible !== false && node.active !== false && node.actions?.includes('click'));
    assert.equal(choices.length, 1, 'One actual native management button: ' + translationKey);
    return deny([{type: 'ui.action', selector: {nodeId: choices[0].nodeId}, action: 'click'}]);
  }
  async function open(className, targetRef) {
    const outcome = await operation([{type: 'world.action', category: 'USER', action: 'use', targetRef}]);
    if (outcome.state === 'failed') assert.equal(outcome.errorCode, 'PLAYER_COMMAND_OUT_OF_SCOPE', JSON.stringify(outcome));
    const value = await page(value => value.screenClass.endsWith(className), 'Actual native ' + className);
    assert.equal(value.treeTruncated, false); assert(value.screenIdentity > 0);
    return {value, outcome};
  }
  async function currentBlock(id) {
    let last; const end = Math.min(started + 180000, now() + 5000);
    do {last = await api('GET', '/v0/world/block?x=2&y=80&z=0&category=USER');
      assert.equal(last.dataCategory, 'USER');
      if (last.available && last.block === id && typeof last.targetRef === 'string') return last;
      await sleep(25);
    } while (now() < end);
    assert.fail('Owned native management block did not reach the client: ' + JSON.stringify(last));
  }
  function record(caseName, opened, extra) {
    samples.push({case: caseName, target, nativeScreens: [opened.value.screenClass], screenClass: opened.value.screenClass,
      screenIdentity: opened.value.screenIdentity, screenRevision: opened.value.screenRevision,
      openOutcome: opened.outcome, category: 'USER', arrangeAuthority: ARRANGE, actAuthority: 'PLAYTEST',
      readbackAuthority: 'DIAGNOSTIC_native_server_receipt_observation', managementExecutionValidated: false,
      semanticTargets: true, handwrittenCoordinates: false, directCallbackUsed: false, persistentWriteCalls: 0, ...extra});
  }
  async function closeOwned() {
    const value = await tree();
    if (ownedScreens.some(name => value.screenClass.endsWith(name))) await key(256);
    await page(value => !value.screenClass, 'Own native management draft closes without unrelated navigation');
  }
  async function ensureFixture() {
    if (fixtureReady) return;
    const current = await tree(); assert.equal(current.dataCategory, 'USER');
    let player;
    if (!current.screenClass) player = await api('GET', '/v0/player?category=USER');
    if (current.screenClass || !player?.available) {
      ownsCleanup = true; await enterWorld();
      await page(value => !value.screenClass, 'Native gameplay before command-management fixture');
      player = await api('GET', '/v0/player?category=USER');
    }
    assert.equal(player.available, true); assert.equal(player.gameMode, 'creative');
    ownsCleanup = true;
    await marker(); // Establish private current-player ownership before any Arrange mutation.
    for (const text of ['fill -1 79 -1 4 79 2 minecraft:stone', 'fill -1 80 -1 4 83 2 minecraft:air', 'tp @s 0.5 80 0.5']) await command(text);
    await clean(); fixtureReady = true;
  }
  try {
    await check('UI command management: native command minecart reads a draft and refuses host configuration before server receipt', async () => {
      await ensureFixture();
      ownedBlock = true; await command('setblock 2 80 0 minecraft:rail');
      ownedCart = true; await command('summon minecraft:command_block_minecart 2.5 80 0.5 {NoGravity:1b,Tags:["' + tag + '"],Command:""}');
      let entity; const end = Math.min(started + 180000, now() + 5000);
      do {const value = await api('GET', '/v0/world/entities?category=USER&radius=8&limit=128');
        assert.equal(value.dataCategory, 'USER'); assert.equal(value.entitiesTruncated, false); assert.equal(value.chunkLoadRequested, false);
        const found = value.entities.filter(value => value.type === 'minecraft:command_block_minecart'
          && Math.hypot(value.x - 2.5, value.y - 80, value.z - .5) < 1);
        if (found.length === 1 && typeof found[0].targetRef === 'string') {entity = found[0]; break;} await sleep(25);
      } while (now() < end); assert(entity, 'One actual current owned command-minecart target');
      const before = await marker(); assert.equal(before.uiFixtureMinecartCount, 1); assert.equal(before.uiFixtureMinecartCommandHash, digest(''));
      const inputBefore = await clean(), opened = await open('MinecartCommandBlockEditScreen', entity.targetRef);
      const draft = await field('advMode.command', 'reload'); await ui({nodeId: draft.node.nodeId}, 'type_text', {text: 'reload'});
      const edited = await page(value => value.screenClass === opened.value.screenClass
        && nodes(value).some(node => node.nodeId === draft.node.nodeId && node.value === 'reload'), 'Actual command-minecart local draft');
      const result = await deniedButton('gui.done'); await page(value => !value.screenClass, 'Native rejected command-minecart Done closes');
      const after = await marker(); unchanged(before, after, ['uiFixtureMinecartCount', 'uiFixtureMinecartCommandHash']);
      record('ui_command_minecart_configuration_denied', opened, {before, after, editedScreenRevision: edited.screenRevision,
        result, inputBefore, inputAfter: await clean(), semanticActions: ['world.use', 'draft.type_text', 'gui.done.denied']});
      await command('kill @e[type=minecraft:command_block_minecart,tag=' + tag + ']'); ownedCart = false;
    });
    if (modern) {
      await check('UI command management: native Test Block reads its local draft and refuses management Done before server receipt', async () => {
        await ensureFixture();
        await command('setblock 2 80 0 minecraft:air'); await command('setblock 2 80 0 minecraft:test_block[mode=log]');
        const block = await currentBlock('minecraft:test_block'), before = await marker();
        assert.equal(before.storedTestMode, 'LOG'); assert.equal(typeof before.storedTestMessageHash, 'string');
        const opened = await open('TestBlockEditScreen', block.targetRef), draft = await field(undefined, 'must_not_reach_server');
        await ui({nodeId: draft.node.nodeId}, 'type_text', {text: 'must_not_reach_server'});
        await page(value => value.screenClass === opened.value.screenClass
          && nodes(value).some(node => node.nodeId === draft.node.nodeId && node.value === 'must_not_reach_server'), 'Actual Test Block local draft');
        const result = await deniedButton('gui.done'); await page(value => !value.screenClass, 'Native rejected Test Block Done closes');
        const after = await marker(); unchanged(before, after, ['storedTestMode', 'storedTestMessageHash']);
        record('ui_test_block_configuration_denied', opened, {before, after, result, inputAfter: await clean(),
          semanticActions: ['world.use', 'draft.type_text', 'gui.done.denied']});
      });
      await check('UI command management: native Test Instance query and Reset are refused before server receipt, without claiming draft rollback', async () => {
        await ensureFixture();
        await command('setblock 2 80 0 minecraft:air'); await command('setblock 2 80 0 minecraft:test_instance_block');
        const block = await currentBlock('minecraft:test_instance_block'), before = await marker(); assert.equal(typeof before.storedTestInstanceHash, 'string');
        const opened = await open('TestInstanceBlockEditScreen', block.targetRef), draft = await field('test_instance_block.test_id', 'minecraft:must_not_query');
        const query = await deny(draft.steps), afterQuery = await tree();
        assert.equal(afterQuery.screenClass, opened.value.screenClass); assert.equal(afterQuery.screenIdentity, opened.value.screenIdentity);
        await closeOwned(); const afterCancel = await marker(); unchanged(before, afterCancel, ['storedTestInstanceHash']);
        const resetOpened = await open('TestInstanceBlockEditScreen', (await currentBlock('minecraft:test_instance_block')).targetRef);
        const reset = await deniedButton('test_instance.action.reset'); await page(value => !value.screenClass, 'Native rejected Test Instance Reset closes');
        const after = await marker(); unchanged(afterCancel, after, ['storedTestInstanceHash']);
        record('ui_test_instance_management_denied', opened, {before, afterCancel, after, query, reset, resetOpenOutcome: resetOpened.outcome,
          localDraftRollbackClaimed: false, inputAfter: await clean(), semanticActions: ['world.use', 'query.draft.denied', 'native.cancel', 'world.use', 'reset.denied']});
      });
    } else if (fixtureReady) samples.push({case: 'ui_test_management_native_absent', target, nativeScreens: [],
      screens: ['TestBlockEditScreen', 'TestInstanceBlockEditScreen'], disposition: 'NATIVE_CLASS_ABSENT', operationCoverage: false});
  } catch (error) {primary = error; throw error;}
  finally {
    if (ownsCleanup) {
    const failures = [];
    for (const cleanup of [async () => {await api('POST', '/v0/input/release-all', {});}, closeOwned,
      async () => {if (ownedCart) await command('kill @e[type=minecraft:command_block_minecart,tag=' + tag + ']');},
      async () => {if (ownedBlock) await command('setblock 2 80 0 minecraft:air');}, clean]) {
      try {await cleanup();} catch (error) {failures.push(error);}
    }
    if (failures.length) throw new AggregateError(primary ? [primary, ...failures] : failures, 'Native management UI cleanup failed', {cause: primary});
    }
  }
}
