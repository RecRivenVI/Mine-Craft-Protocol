import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {createWriteStream} from 'node:fs';
import {access, copyFile, mkdir, readFile, readdir, stat, writeFile} from 'node:fs/promises';
import {homedir} from 'node:os';
import {createServer} from 'node:net';
import {delimiter, dirname, join, resolve} from 'node:path';
import {findJava} from '../../integration-packaged_runtime/task/installed-launcher.mjs';
import {automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {checkPeerAuthority} from './peer-authority-checks.mjs';
import {prepareServerWorld} from './server-world-fixture.mjs';

const repo = resolve(import.meta.dirname, '../../../..');
const target = process.argv.find(arg => arg.startsWith('--target='))?.slice(9);
const targets = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];
assert(targets.includes(target), 'Explicit registered Target required');
const properties = async path => Object.fromEntries((await readFile(path, 'utf8')).split(/\r?\n/)
  .filter(line => line.includes('=') && !line.startsWith('#')).map(line => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)]; }));
const facts = await properties(join(repo, 'versions', target, 'target.properties'));
const product = await properties(join(repo, 'gradle.properties'));
assert.equal(product.mod_client_side, 'optional'); assert.equal(product.mod_server_side, 'optional');
assert(/^eula\s*=\s*true\s*$/m.test(await readFile(join(repo, 'local.toml'), 'utf8')), 'Explicit existing EULA consent required');
const minecraft = target.slice(0, target.lastIndexOf('-')), loader = target.slice(target.lastIndexOf('-') + 1);
const installation = join(repo, 'validations/integration-packaged_runtime/instance/installed', target, 'installation');
const vanilla = JSON.parse(await readFile(join(installation, 'versions', minecraft, minecraft + '.json'), 'utf8'));
const profile = loader === 'fabric' ? JSON.parse(await readFile(join(installation, 'fabric-profile.json'), 'utf8'))
  : await (async () => { const ids = (await readdir(join(installation, 'versions'))).filter(id => id !== minecraft); assert.equal(ids.length, 1); return JSON.parse(await readFile(join(installation, 'versions', ids[0], ids[0] + '.json'), 'utf8')); })();
const java = await findJava(facts.java_version);
const base = join(repo, 'validations/integration-server_peer');
const root = join(base, 'instance', target, 'optional-install');
const jarName = `${product.mod_id}-${target}-${product.mod_version}.jar`;
const artifact = join(repo, 'build/release', product.mod_version, jarName);
const jarHash = createHash('sha256').update(await readFile(artifact)).digest('hex');
const exists = path => access(path).then(() => true, () => false);
const sleep = ms => new Promise(done => setTimeout(done, ms));
async function deadline(promise, ms, label) {
  let timer;
  try { return await Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Error(label)), ms); })]); }
  finally { clearTimeout(timer); }
}
const alive = pid => { if (!pid) return false; try { process.kill(pid, 0); return true; } catch (error) { if (error.code === 'ESRCH') return false; throw error; } };
await mkdir(root, {recursive: true}); await mkdir(join(base, 'result'), {recursive: true});
const ownerFile = join(root, 'owner.json');
if (await exists(ownerFile)) {
  const old = JSON.parse(await readFile(ownerFile, 'utf8'));
  assert.equal(old.owner, 'optional-install-smoke'); assert.equal(old.target, target);
  assert(![old.pid, ...(old.children ?? [])].some(alive), 'Previous runner or owned game still active; directory untouched');
}
const children = [];
async function saveOwner() { await writeFile(ownerFile, JSON.stringify({owner: 'optional-install-smoke', target, pid: process.pid, children: children.filter(c => !c.exited).map(c => c.child.pid)})); }
await saveOwner();
const report = {target, loaderVersion: facts.loader_version, jarSha256: jarHash, startedAt: new Date().toISOString(),
  runtime: 'official_installed_loader_and_vanilla_release', developmentClasspath: false, cases: [], persistentWriteCalls: 0};
function permitted(rules) {
  if (!rules) return true;
  let allowed = false;
  for (const rule of rules) {
    if (rule.os?.name && rule.os.name !== 'windows') continue;
    if (rule.os?.arch && !['x86_64', 'amd64', 'x64'].includes(rule.os.arch)) continue;
    if (rule.os?.version && !new RegExp(rule.os.version).test('10.0')) continue;
    if (rule.features && Object.values(rule.features).some(value => value !== false)) continue;
    allowed = rule.action === 'allow';
  }
  return allowed;
}
async function launch(args, game, name) {
  const output = createWriteStream(join(game, name + '.log'));
  const child = spawn(java, ['-Xms512M', '-Xmx4G', '-Dfile.encoding=UTF-8', ...args],
    {cwd: game, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
  const record = {child, game, name, exited: false, output: '', startedAt: Date.now()};
  const collect = bytes => { record.output = (record.output + bytes.toString()).slice(-262144); };
  child.stdout.on('data', collect); child.stderr.on('data', collect);
  child.stdout.pipe(output, {end: false}); child.stderr.pipe(output, {end: false});
  record.exit = new Promise((done, fail) => { child.once('error', fail); child.once('exit', code => { record.exited = true; record.exitCode = code; output.end(); done(code); }); });
  children.push(record); await saveOwner(); return record;
}
async function until(read, valid, label, processRecord, timeout = 30000) {
  const end = Date.now() + timeout; let value;
  do {
    assert(!processRecord?.exited, `${label}: owned process exited (${processRecord?.exitCode})`);
    value = await read(); if (valid(value)) return value; await sleep(150);
  } while (Date.now() < end);
  throw Error(`${label}: ${JSON.stringify(value)}`);
}
async function logOf(record) { return exists(join(record.game, 'logs/latest.log')).then(yes => yes ? readFile(join(record.game, 'logs/latest.log'), 'utf8') : ''); }
async function apiFor(record) {
  const path = join(record.game, 'minecraft-protocol/endpoint.json');
  const endpoint = await until(async () => await exists(path) ? JSON.parse(await readFile(path, 'utf8')) : null,
    value => value?.pid === record.child.pid && value?.target === target, 'Runtime endpoint', record, 180000);
  assert.equal(endpoint.target, target); assert.equal(endpoint.pid, record.child.pid);
  const token = (await readFile(join(record.game, 'minecraft-protocol/token'), 'utf8')).trim();
  const call = async (method, path, body, expected = 200, headers = {}) => {
    const response = await fetch(endpoint.baseUrl + path, {method, headers: {Authorization: 'Bearer ' + token, 'X-MCP-Runtime-Instance': endpoint.runtimeInstanceId,
      ...(body ? {'Content-Type': 'application/json'} : {}), ...headers}, ...(body ? {body: JSON.stringify(body)} : {}), signal: AbortSignal.timeout(15000)});
    assert.equal(response.headers.get('x-mcp-runtime-instance'), endpoint.runtimeInstanceId);
    const value = await response.json(); assert.equal(response.status, expected, JSON.stringify(value)); return value;
  };
  return {call, endpoint};
}
async function gameDirectory(name) {
  const game = join(root, name); await mkdir(game, {recursive: true});
  // Only this runner's directories may be reused, never a live Runtime or arbitrary mods.
  const endpoint = join(game, 'minecraft-protocol/endpoint.json');
  if (await exists(endpoint)) assert(!alive(JSON.parse(await readFile(endpoint, 'utf8')).pid), 'Live Runtime directory');
  await mkdir(join(game, 'mods'), {recursive: true});
  assert((await readdir(join(game, 'mods'))).every(file => file === jarName), 'Unexpected installed mods');
  return game;
}
async function installProduct(game, enabled) {
  const mods = await readdir(join(game, 'mods'));
  if (enabled) await copyFile(artifact, join(game, 'mods', jarName));
  else assert.equal(mods.length, 0, 'Unmodded counterpart must contain no mods');
}
async function server(modded, name, delegated = false) {
  const game = await gameDirectory(name); await installProduct(game, modded);
  const reservation = createServer(); await new Promise(done => reservation.listen(0, '127.0.0.1', done));
  const port = reservation.address().port; await new Promise(done => reservation.close(done));
  await writeFile(join(game, 'eula.txt'), 'eula=true\n');
  await writeFile(join(game, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nlevel-name=Optional-Install\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nmax-players=1\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\n`);
  const bundled = join(homedir(), '.gradle/caches/neoformruntime/artifacts', `minecraft_${minecraft}_server.jar`);
  assert.equal(createHash('sha1').update(await readFile(bundled)).digest('hex'), vanilla.downloads.server.sha1, 'Official vanilla server');
  let args = ['-jar', bundled, 'nogui'];
  if (modded && loader === 'fabric') {
    const cp = profile.libraries.map(lib => { const [group, name, version] = lib.name.split(':'); return join(installation, 'libraries', group.replaceAll('.', '/'), name, version, `${name}-${version}.jar`); });
    args = ['-Dfabric.gameJarPath=' + bundled, '-cp', cp.join(delimiter), 'net.fabricmc.loader.impl.launch.server.FabricServerLauncher', 'nogui'];
  } else if (modded) {
    const cache = join(base, 'instance', target, 'console-server');
    const coordinate = loader === 'forge' ? minecraft + '-' + facts.loader_version : facts.loader_version;
    const argFile = join(cache, 'libraries', loader === 'forge' ? 'net/minecraftforge/forge' : 'net/neoforged/neoforge', coordinate, 'win_args.txt');
    const libraryDirectory = join(cache, 'libraries').replaceAll('\\', '/');
    const text = (await readFile(argFile, 'utf8')).replaceAll('libraries/', libraryDirectory + '/')
      .replace('-DlibraryDirectory=libraries', '-DlibraryDirectory=' + libraryDirectory);
    await writeFile(join(game, 'server-args.txt'), text); args = ['@server-args.txt', 'nogui'];
  }
  const scopes=delegated?['-Dminecraft.protocol.scopes=read,event,control,command,diagnostics,server_console,server_peer,debug,debug.world.read,debug.write,debug.world,debug.entity,debug.player,debug.menu,debug.block_entity']:[];
  const start=async()=>{
    const record=await launch(['-Dterminal.jline=false',...scopes,...args],game,'server');
    record.port=port;await until(async()=>record.output,text=>/Done \(/.test(text),'server ready',record,180000);return record;
  };
  const fixture=await prepareServerWorld({repo,game,world:'Optional-Install',target,facts:join(repo,'versions',target,'target.properties'),start,stop:stopServer});
  // Reset only this runner's explicit permission fixture, never an external server's operators.
  await writeFile(join(game,'ops.json'),'[]\n');
  const record=await start();record.fixture=fixture;return record;
}
async function client(modded, port, name, delegated = false) {
  const game = await gameDirectory(name); await installProduct(game, modded);
  await writeFile(join(game, 'options.txt'), 'lang:en_us\nsoundCategory_master:0.0\npauseOnLostFocus:false\nonboardAccessibility:false\nautoJump:false\n');
  const libraries = new Map();
  for (const lib of [...vanilla.libraries, ...(modded ? profile.libraries : [])]) if (permitted(lib.rules)) {
    const [group, name, , classifier = ''] = lib.name.split(':'); libraries.set(group + ':' + name + ':' + classifier, lib);
  }
  const cp = [];
  for (const lib of libraries.values()) {
    const [group, name, version, classifier] = lib.name.split(':');
    const artifact = lib.downloads?.artifact;
    const path = artifact?.path ?? `${group.replaceAll('.', '/')}/${name}/${version}/${name}-${version}${classifier ? '-' + classifier : ''}.jar`;
    const local = join(installation, 'libraries', path);
    if (!await exists(local)) {
      const response = await fetch(artifact?.url ?? (lib.url ?? 'https://libraries.minecraft.net/') + path, {signal: AbortSignal.timeout(60000)});
      assert(response.ok, 'Official library download: ' + path);
      const bytes = Buffer.from(await response.arrayBuffer());
      assert(artifact?.sha1, 'Missing cached libraries must have official checksums');
      assert.equal(createHash('sha1').update(bytes).digest('hex'), artifact.sha1);
      await mkdir(dirname(local), {recursive: true}); await writeFile(local, bytes);
    }
    if (artifact?.sha1) assert.equal(createHash('sha1').update(await readFile(local)).digest('hex'), artifact.sha1);
    cp.push(local);
  }
  const selected = modded ? profile : vanilla;
  cp.push(join(installation, 'versions', selected.id, selected.id + '.jar'));
  for (const path of cp) assert(await exists(path), 'Official client library missing: ' + path);
  const substitutions = {auth_player_name: 'OptionalTest', version_name: selected.id, game_directory: game,
    assets_root: join(repo, 'validations/integration-packaged_runtime/instance/downloads/assets'), assets_index_name: vanilla.assetIndex.id,
    auth_uuid: randomUUID().replaceAll('-', ''), auth_access_token: '0', clientid: '', auth_xuid: '', user_type: 'legacy', version_type: 'release',
    natives_directory: join(installation, 'natives'), launcher_name: 'Mine-Craft-Protocol-optional-install', launcher_version: '1',
    classpath: cp.join(delimiter), library_directory: join(installation, 'libraries'), classpath_separator: delimiter};
  const expand = value => value.replace(/\$\{([^}]+)\}/g, (_, key) => { assert(Object.hasOwn(substitutions, key), key); return substitutions[key]; });
  const argumentsFor = values => values.flatMap(value => typeof value === 'string' ? [expand(value)] : permitted(value.rules) ? (Array.isArray(value.value) ? value.value : [value.value]).map(expand) : []);
  const scopes=modded?['-Dminecraft.protocol.scopes=read,event,control,input,ui,command,capture,recording,diagnostics,debug,debug.ui,debug.ui.read'+(delegated?',debug.world.read,debug.write,debug.world,debug.player,debug.entity,debug.menu,debug.block_entity':'')]:[];
  return launch([...scopes,...argumentsFor([...(vanilla.arguments?.jvm ?? []), ...(modded ? profile.arguments?.jvm ?? [] : [])]), selected.mainClass,
    ...argumentsFor([...(vanilla.arguments?.game ?? []), ...(modded ? profile.arguments?.game ?? [] : [])]),
    '--width', '1920', '--height', '1080', '--quickPlayMultiplayer', '127.0.0.1:' + port], game, 'client');
}
async function stopServer(record) {
  if (record.exited) return;
  record.child.stdin.write('stop\n');
  assert.equal(await deadline(record.exit, 45000, 'Owned server normal stop deadline'), 0);
  assert(!await exists(join(record.game, 'minecraft-protocol/endpoint.json')));
}
async function operation(call, path, body, headers) {
  const started = await call('POST', path, body, 200, headers);
  if (!started.operationId) return started;
  const ended = await until(() => call('GET', '/v0/operations/' + started.operationId), op => ['completed', 'failed', 'cancelled'].includes(op.state), 'Operation terminal');
  assert.equal(ended.state, 'completed', JSON.stringify(ended)); return ended;
}
async function exerciseAndQuit(record, api, peerExpected, sample) {
  const call = api.call;
  await until(() => call('GET', '/v0/player?category=USER'), player => player.available === true || player.position != null, 'client player ready', record, 90000);
  const peer = await until(() => call('GET', '/v0/server/peer'), value => value.connected === peerExpected, 'Peer availability', record);
  sample.peer = peer; assert.equal(peer.pendingRequests, 0);
  if (peerExpected) sample.probe = await call('POST', '/v0/server/peer/probe', {});
  else {
    sample.unavailable = await call('POST', '/v0/server/peer/probe', {}, 409);
    assert.equal(sample.unavailable.error, 'SERVER_PEER_UNAVAILABLE');
    sample.localDebugCapabilities = await call('GET', '/v0/debug/capabilities');
    assert.equal(sample.localDebugCapabilities.worldDiscovery.available, false);
    assert.equal(sample.localDebugCapabilities.worldDiscovery.errorCode, 'SERVER_PEER_UNAVAILABLE');
    assert(sample.localDebugCapabilities.operations.length>0);
    assert(sample.localDebugCapabilities.operations.every(operation=>operation.authority==='CLIENT_UI'&&operation.requiredScope==='debug.ui'&&operation.requiresArm));
    await sleep(2500); assert.equal((await call('GET', '/v0/server/peer')).connected, false);
  }
  sample.player = await call('GET', '/v0/player?category=USER'); assert.equal(sample.player.dataCategory, 'USER');
  let stableSince = Date.now(), previous;
  await until(() => call('GET', '/v0/ui/tree'), tree => {
    const key = [tree.screenIdentity, tree.screenRevision, tree.guiScale, tree.width, tree.height].join('/');
    if (tree.screenClass || tree.overlayIdentity || key !== previous) stableSince = Date.now();
    previous = key;
    return !tree.screenClass && !tree.overlayIdentity && tree.width > 0 && tree.height > 0 && Date.now() - stableSince >= 1000;
  }, 'stable unobscured gameplay before input', record);
  const mode = await call('GET', '/v0/control/mode');
  const lease = await call('POST', '/v0/control/acquire', automaticTakeoverRequest(mode, {ttlMs: 60000}));
  const headers = {'X-MCP-Control-Lease': lease.leaseId};
  await operation(call, '/v0/pipelines', {steps: [{type: 'key.tap', key: 69, holdMs: 80}]}, headers);
  const inventory = await until(() => call('GET', '/v0/ui/tree'), tree => /InventoryScreen/.test(tree.screenClass), 'native inventory', record);
  sample.inventory = {screenClass: inventory.screenClass, screenRevision: inventory.screenRevision, menuIdentity: inventory.menuIdentity};
  await operation(call, '/v0/pipelines', {steps: [{type: 'key.tap', key: 256, holdMs: 80}]}, headers);
  assert.equal((await call('GET', '/v0/control/mode')).reconsentRequired, false);
  sample.input = await call('GET', '/v0/input/state');
  assert.equal(sample.input.pressedKeyCount, 0); assert.equal(sample.input.pressedButtonCount, 0);
  await operation(call, '/v0/pipelines', {steps: [{type: 'key.tap', key: 256, holdMs: 80}]}, headers);
  await until(() => call('GET', '/v0/ui/tree'), tree => /PauseScreen/.test(tree.screenClass), 'pause', record);
  // Disconnect invalidates the input context: accept only the documented cancellation, then independently verify title.
  try { await operation(call, '/v0/ui/action', {action: 'click', selector: {translationKey: 'menu.disconnect'}}, headers); }
  catch (error) { assert(/REQUEST_CANCELLED|CONTROL_INPUT_GENERATION_CHANGED/.test(error.message), error.message); sample.disconnectUnconfirmed = true; }
  const returned = await until(() => call('GET', '/v0/ui/tree'), tree => /TitleScreen|JoinMultiplayerScreen/.test(tree.screenClass) && !tree.overlayIdentity, 'disconnected parent screen', record);
  if (/JoinMultiplayerScreen/.test(returned.screenClass)) {
    const nodes = node => [node, ...(node.children ?? []).flatMap(nodes)];
    const back = nodes(returned).filter(node => node.role === 'button' && ['gui.back', 'gui.cancel'].includes(node.translationKey));
    assert.equal(back.length, 1, 'Native multiplayer parent must expose one semantic return button');
    await operation(call, '/v0/ui/action', {action: 'click', selector: {nodeId: back[0].nodeId}}, headers);
  }
  const title = await until(() => call('GET', '/v0/ui/tree'), tree => /TitleScreen/.test(tree.screenClass) && !tree.overlayIdentity, 'disconnected title', record);
  assert.equal((await call('GET', '/v0/server/peer')).connected, false);
  assert.equal((await call('GET', '/v0/server/peer')).pendingRequests, 0);
  sample.disconnected = true;
  await call('POST', '/v0/control/release', {}, 200, headers);
  const renewed = await call('POST', '/v0/control/acquire', automaticTakeoverRequest(await call('GET', '/v0/control/mode'), {ttlMs: 15000}));
  try { await call('POST', '/v0/ui/action', {action: 'click', selector: {translationKey: 'menu.quit'}}, 200, {'X-MCP-Control-Lease': renewed.leaseId}); }
  catch (error) { assert(/RUNTIME_SHUTTING_DOWN|fetch failed|socket|terminated/.test(error.message), error.message); }
  assert.equal(await deadline(record.exit, 60000, 'Client quit deadline'), 0);
  assert(!await exists(join(record.game, 'minecraft-protocol/endpoint.json'))); sample.cleanClientShutdown = true;
  assert(title.windowWidth === 1920 && title.windowHeight === 1080);
}
try {
  for (const kind of ['client_only', 'server_only', 'both']) {
    console.log(target + ' optional installation: ' + kind);
    const sample = {kind, status: 'FAIL'}; report.cases.push(sample);
    const s = await server(kind !== 'client_only', kind + '-server',kind==='both');
    sample.fixture=s.fixture;
    const c = await client(kind !== 'server_only', s.port, kind + '-client',kind==='both');
    await until(async () => s.output, text => /OptionalTest joined the game/.test(text), 'native player joined', s, 180000);
    sample.joined = true;
    if (kind === 'server_only') {
      assert(!await exists(join(c.game, 'minecraft-protocol/endpoint.json')), 'Vanilla client must not load Runtime');
      const api = await apiFor(s); sample.serverSession = await api.call('GET', '/v0/session');
      assert.equal(sample.serverSession.deploymentRole, 'DEDICATED_SERVER'); assert.equal(sample.serverSession.inputSurfaceAvailable, false);
      sample.consoleDenied = await api.call('GET', '/v0/server/console?after=0&limit=1', undefined, 403);
      assert.equal((await api.call('POST', '/v0/control/acquire', {}, 409)).error, 'TAKEOVER_NOT_APPLICABLE');
      await stopServer(s);
      // A genuinely vanilla client has no Runtime quit API. The server saves normally first;
      // only this owned, now-disconnected client is terminated (never reported as a normal client exit).
      c.child.kill(); await c.exit; sample.vanillaClientCleanup = 'terminated_after_server_normal_stop_no_client_runtime';
    } else {
      const api = await apiFor(c);
      if(kind==='both'){
        await until(()=>api.call('GET','/v0/server/peer'),value=>value.connected,'Peer handshake',c);
        const owner=await apiFor(s);
        sample.authority=await checkPeerAuthority({client:api.call,server:owner.call,nativeConsole:command=>s.child.stdin.write(command+'\n'),until});
      }
      await exerciseAndQuit(c, api, kind === 'both', sample); await stopServer(s);
    }
    for (const record of [s, c]) {
      const crashes = join(record.game, 'crash-reports');
      if (await exists(crashes)) for (const file of await readdir(crashes))
        assert((await stat(join(crashes, file))).mtimeMs < record.startedAt, 'No new crash reports');
      assert(!/NoClassDefFoundError|Failed to handle packet|Unknown custom packet identifier/.test(await logOf(record)), 'No dependency or unknown payload errors');
    }
    sample.normalServerShutdown = true; sample.status = 'PASS'; await saveOwner();
  }
  assert.equal(createHash('sha256').update(await readFile(artifact)).digest('hex'), jarHash, 'Final JAR unchanged');
  report.status = 'PASS';
} catch (error) { report.status = 'FAIL'; report.error = String(error.stack ?? error); process.exitCode = 1; console.error(error); }
finally {
  for (const record of children.filter(c => !c.exited && c.name === 'server')) await stopServer(record).catch(() => {});
  for (const record of children.filter(c => !c.exited)) { record.child.kill(); await record.exit.catch(() => {}); }
  report.finishedAt = new Date().toISOString(); report.allOwnedProcessesExited = children.every(c => c.exited);
  await writeFile(join(base, 'result', target + '-optional-install.json'), JSON.stringify(report, null, 2) + '\n');
  await writeFile(ownerFile, JSON.stringify({owner: 'optional-install-smoke', target, pid: 0, children: []}));
}
