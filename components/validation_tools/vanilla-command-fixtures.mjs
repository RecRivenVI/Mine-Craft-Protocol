import assert from 'node:assert/strict';

// Diagnostic Arrange only: each command still uses native Chat and player/server permissions.
// Do not split arbitrary user commands or silently switch to an internal writer.
export function villagerFixtureCommands({target,tag,position,sells,invulnerable=false,zeroPriceMultiplier=false}) {
  assert(/^[a-z0-9_]{1,32}$/.test(tag),'Owned fixture tag required');
  assert.equal(position.length,3);
  assert(position.every(value=>Number.isFinite(value)&&Math.abs(value)<=30000000));
  assert(sells.length>0&&sells.length<=12);
  assert(sells.every(item=>/^[a-z0-9_]+$/.test(item)));
  const selector=`@e[type=minecraft:villager,tag=${tag},limit=1]`;
  const commands=[
    `summon minecraft:villager ${position.join(' ')} {Tags:["${tag}"],NoAI:1b,Silent:1b${invulnerable?',Invulnerable:1b':''}}`,
    `data merge entity ${selector} {VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"}}`,
    `data merge entity ${selector} {Offers:{Recipes:[]}}`
  ];
  const count=target.startsWith('1.20.')?'Count:1b':'count:1';
  for(const item of sells){
    commands.push(`data modify entity ${selector} Offers.Recipes append value {buy:{id:"minecraft:emerald",${count}},sell:{id:"minecraft:${item}",${count}}}`);
    commands.push(`data modify entity ${selector} Offers.Recipes[-1].maxUses set value 999`);
    if(zeroPriceMultiplier)commands.push(`data modify entity ${selector} Offers.Recipes[-1].priceMultiplier set value 0f`);
  }
  assert(commands.every(command=>command.length+1<=256&&!/[\r\n]/.test(command)),'Fixture must fit native Chat including slash');
  return commands;
}
