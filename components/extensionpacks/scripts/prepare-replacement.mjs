import {readFile,readdir,mkdir,writeFile} from 'node:fs/promises';
import {resolve,relative,dirname} from 'node:path';
import assert from 'node:assert/strict';
const source=resolve('fixtures/platform_probe/java'),output=resolve('build/replacement-sources');
let changed=0;
for(const entry of await readdir(source,{recursive:true,withFileTypes:true}))if(entry.isFile()){
  const file=resolve(entry.parentPath,entry.name),path=resolve(output,relative(source,file));
  let text=await readFile(file,'utf8');
  if(entry.name==='PlatformProbeExtension.java'){
    assert.equal(text.split('"managed_async_preparation_v3"').length,2);
    text=text.replace('"managed_async_preparation_v3"','"replacement_before_v1"');changed++;
  }
  await mkdir(dirname(path),{recursive:true});
  if(await readFile(path,'utf8').catch(error=>{if(error.code==='ENOENT')return null;throw error;})!==text)await writeFile(path,text);
}
assert.equal(changed,1,'A separate compiled package must change real Provider behavior');
