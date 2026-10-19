import assert from 'node:assert/strict';

// Stationary adults retain AI so native feeding and BreedGoal produce the offspring.
// Separate selectors prevent both attribute commands from selecting the same cow.
export function breedingFixtureCommands({target,tag,position}) {
  const attribute=new Map([
    ['1.20.1-forge','minecraft:generic.movement_speed'],
    ['1.21.1-neoforge','minecraft:generic.movement_speed'],
    ['26.1.2-neoforge','minecraft:movement_speed'],
    ['26.3-neoforge','minecraft:movement_speed'],
    ['26.3-fabric','minecraft:movement_speed']
  ]).get(target);
  assert(attribute,'Supported fixture Target required');
  assert(typeof tag==='string'&&/^[a-z0-9_]{1,32}$/.test(tag),'Owned fixture tag required');
  assert(Array.isArray(position)&&position.length===3,'Finite fixture position required');
  assert(position.every(value=>Number.isFinite(value)&&Math.abs(value)<=30000000),'Finite bounded fixture position required');
  const commands=[];
  for(const [index,offset] of [-1,1].entries()) {
    const adultTag=`${tag}_breed${index}`;
    const at=[position[0],position[1],position[2]+offset];
    assert(at.every(value=>Math.abs(value)<=30000000),'Both adult positions must stay within the fixture bound');
    commands.push(`summon minecraft:cow ${at.join(' ')} {Silent:1b,PersistenceRequired:1b,NoAI:0b,Age:0,Tags:["${tag}","${adultTag}"]}`);
    commands.push(`attribute @e[type=minecraft:cow,tag=${adultTag},limit=1] ${attribute} base set 0`);
  }
  assert(commands.every(command=>command.length+1<=256&&!/[\r\n]/.test(command)),'Fixture must fit native Chat including slash');
  return commands;
}

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
