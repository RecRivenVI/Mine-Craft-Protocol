import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const template=await readFile('fixtures/lifetime_probe/Entry.java.template','utf8');
for(const side of ['left','right']) {
    const namespace='io.github.recrivenvi.minecraftprotocol.extensions.lifetime_'+side;
    const folder=resolve('build/lifecycle-sources',...namespace.split('.'));
    await mkdir(folder,{recursive:true});await writeFile(resolve(folder,'Entry.java'),template.replaceAll('@NAMESPACE@',namespace));
}
