import assert from 'node:assert/strict';

// No focus injection, automatic pause/resume, input replay or diagnostic camera mutation.
export async function firstCameraActions({api,target,acquire,release,click,clickNode,wait,nodes,check,actionSamples}) {
  let owned=false;
  const sample={case:'first_background_world_camera',target,inputReplayed:false,hostFocusChangedByTest:false,
    pauseResumeBeforeCamera:false,directCameraMutation:false,performanceMeasured:false};
  actionSamples.push(sample);
  try {
    await check('first camera starts TAKEOVER at the title in a real unfocused window',async()=>{
      const title=await api('GET','/v0/ui/tree');assert(title.screenClass.endsWith('TitleScreen'));assert(!title.overlayIdentity);
      sample.titleInput=await api('GET','/v0/input/state');assert.equal(sample.titleInput.hostFocused,false,'Background window creation did not retain actual host focus');
      await acquire();owned=true;
      sample.titleControl=await api('GET','/v0/control/status');assert.equal(sample.titleControl.mode,'TAKEOVER');
    });
    await check('first camera enters only the fixed world through semantic GUI',async()=>{
      await click('menu.singleplayer','Singleplayer');
      const list=await wait(value=>value.screenClass.endsWith('SelectWorldScreen')&&nodes(value).some(node=>node.worldName==='World-Read-Alpha'),'first camera fixed world row');
      await clickNode(nodes(list).find(node=>node.worldName==='World-Read-Alpha'));
      await click('selectWorld.select','Play Selected World');
      await wait(value=>!value.screenClass&&!value.overlayIdentity,'first camera initial gameplay');
      const end=Date.now()+10000;
      do{sample.beforePlayer=await api('GET','/v0/player?category=USER');if(sample.beforePlayer.available)break;await new Promise(done=>setTimeout(done,100));}while(Date.now()<end);
      assert.equal(sample.beforePlayer.available,true);
      sample.beforeInput=await api('GET','/v0/input/state');assert.equal(sample.beforeInput.hostFocused,false);
      assert.equal(sample.beforeInput.pressedKeyCount,0);assert.equal(sample.beforeInput.pressedButtonCount,0);
    });
    await check('first relative mouse input changes the camera after native W input',async()=>{
      sample.steps=[{type:'key.tap',key:87,holdMs:700},{type:'mouse.delta',dx:36,dy:-12}];
      sample.accepted=await api('POST','/v0/pipelines',{steps:sample.steps,timeoutMs:10000,cleanupOnComplete:true});
      const end=Date.now()+12000;
      do{sample.operation=await api('POST',`/v0/operations/${sample.accepted.operationId}/wait`,{timeoutMs:1000});}while(!['completed','failed','cancelled','timed_out'].includes(sample.operation.state)&&Date.now()<end);
      assert.equal(sample.operation.state,'completed',JSON.stringify(sample.operation));
      sample.observations=[];
      for(let index=0;index<5;index++){
        sample.observations.push(await api('GET','/v0/player?category=USER'));
        if(index<4)await new Promise(done=>setTimeout(done,100));
      }
      sample.afterInput=await api('GET','/v0/input/state');
      sample.cameraChanged=sample.observations.some(value=>Math.abs(((value.yaw-sample.beforePlayer.yaw+540)%360)-180)>0.01||Math.abs(value.pitch-sample.beforePlayer.pitch)>0.01);
      sample.playerMoved=sample.observations.some(value=>Math.hypot(value.x-sample.beforePlayer.x,value.z-sample.beforePlayer.z)>0.05);
      assert.equal(sample.afterInput.hostCursorCaptured,false);assert.equal(sample.afterInput.pressedKeyCount,0);assert.equal(sample.afterInput.pressedButtonCount,0);
      assert.equal(sample.playerMoved,true,'Native W did not move the isolated player');
      assert.equal(sample.cameraChanged,true,'Completed first relative input did not reach the native camera');
    });
  } finally {
    if(owned){await release();owned=false;}
  }
}
