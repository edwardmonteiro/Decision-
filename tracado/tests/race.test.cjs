const assert=require('node:assert/strict');
const R=require('../app/src/main/assets/race.js'),{tracks,normalize}=require('../app/src/main/assets/tracks.js');

// All circuits must work in every mode and both directions, with physical checkpoint crossings.
for(const track of tracks){
  for(const direction of [1,-1])for(const mode of Object.keys(R.MODES)){
    const points=direction===1?track.road.points:track.road.points.slice().reverse();
    const result=R.simulate(track,points,mode);
    assert.equal(result.error,undefined,`${track.name} ${mode}: valid route`);
    assert.equal(result.state.status,'finished',`${track.name} ${mode} ${direction}`);
    assert.equal(result.state.gates,3);assert(result.state.progress>=track.road.length);
    assert(result.state.time>5&&result.state.time<35,'lap takes a playable amount of time');
    if(mode==='drift'){assert(result.state.driftTime>2,'slip really occurs');assert(result.state.maxSlip>.2,'body and velocity diverge');}
    const before={...result.state.car};R.step(result.state);assert.deepEqual(result.state.car,before,'finished cars stop simulating');
  }
}

const track=tracks[0],center=track.road.points;
assert(!R.validate(track,[]).ok);
assert(!R.validate(track,center.slice(0,40)).ok,'open partial route cannot race');
assert(!R.validate(track,center.map(p=>[p[0]+180,p[1]])).ok,'outside route cannot race');
assert(!R.validate(track,[[NaN,404],...center]).ok);
assert(!R.validate(track,center.slice(50).concat(center.slice(0,50))).ok,'must start at the flag');
assert(!R.validate(track,[center[0],center[40],center[110],center[0]]).ok,'infield shortcuts rejected');
assert(!R.validate(track,[...center,...center]).ok,'only one circuit is accepted');
assert(!R.segmentOnRoad(track,[65,240],[295,240]),'crossing the infield is rejected even with endpoints on the road');

// Offset racing lines remain distinct; the vehicle is controlled by the user's route.
const offset=amount=>center.map((p,i)=>{if(i===0||i===center.length-1)return p.slice();const a=R.nearest(track.road,...p).angle;return [p[0]-Math.sin(a)*amount,p[1]+Math.cos(a)*amount];});
const inside=R.simulate(track,offset(-8),'race'),outside=R.simulate(track,offset(8),'race');
assert.equal(inside.state.status,'finished');assert.equal(outside.state.status,'finished');
assert(Math.abs(inside.state.time-outside.state.time)>.2,'line changes lap time');
assert(Math.hypot(inside.trail[20][0]-outside.trail[20][0],inside.trail[20][1]-outside.trail[20][1])>5,'line changes trajectory');
const cruise=R.simulate(track,center,'cruise').state,fast=R.simulate(track,center,'race').state,drift=R.simulate(track,center,'drift').state;
assert(fast.time<cruise.time,'racing mode is faster');assert(drift.maxSlip>cruise.maxSlip*2,'drift has lower lateral grip');
assert.equal(cruise.driftTime,0,'non-drift mode does not earn drift statistics');
const again=R.simulate(track,center,'drift').state;assert.equal(again.time,drift.time);assert.deepEqual(again.car,drift.car,'fixed-step determinism');

const blank=normalize(null);assert.equal(blank.unlocked,0);assert.equal(blank.selected,0);assert.equal(blank.mode,'cruise');
assert.equal(normalize({selected:5}).selected,0);
assert.equal(normalize({records:[{cruise:12},{race:10}],selected:5}).selected,2);
assert.equal(normalize({records:[{cruise:NaN,race:-1,drift:Infinity}]}).unlocked,0);
assert.equal(normalize({mode:'unknown',sound:false}).mode,'cruise');assert.equal(normalize({sound:false}).sound,false);
assert.equal(normalize({records:Array.from({length:6},()=>({drift:20})),selected:5}).unlocked,5);
console.log('PASS: 36 mode/direction/circuit laps, 108 actual checkpoint crossings, drift slip, racing-line influence, route validation, deterministic dynamics and progression.');
