import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';

// Fixed 1.20.1 native ABI, reviewed against namedToIntermediate and verified by installed Loader cases.
const bindings=JSON.parse(await readFile('authoring/legacy-bindings.json','utf8'));
const entries=Object.entries(bindings).sort(([a],[b])=>a.localeCompare(b)).map(([key,value])=>`values.put(${JSON.stringify(key)}, ${JSON.stringify(value)});`).join('\n');
for(const id of JSON.parse(await readFile('extensionpacks/index.json','utf8'))){
  const spec=JSON.parse(await readFile(`extensionpacks/${id}/package.json`,'utf8'));if(!spec.support)continue;
  const directory=resolve('build/authoring',id,...spec.javaNamespace.split('.'),'bridge');await mkdir(directory,{recursive:true});
  for(const name of ['NativeAccess','ReadProvider','OwnerTasks']){
    const source=(await readFile(`authoring/${name}.java.template`,'utf8')).replaceAll('@NAMESPACE@',spec.javaNamespace).replace('@LEGACY_METHODS@',entries);
    await writeFile(resolve(directory,name+'.java'),source);
  }
}
