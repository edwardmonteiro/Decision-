const assert=require('node:assert/strict');
const P=require('../app/src/main/assets/physics.js');
const {levels,normalize}=require('../app/src/main/assets/levels.js');

// Every authored route must physically reach home and collect both optional stars.
for(const level of levels){
  const result=P.simulate(level,level.solution).state;
  assert.equal(result.status,'won',level.name);
  assert.deepEqual(result.collected,[true,true],level.name+' collectibles');
  assert(result.time<10,level.name+' should not need a long wait');
  assert.equal(P.simulate(level,[]).state.status,'lost',level.name+' requires a drawing');
  assert.deepEqual(P.simulate(level,level.solution).state.ball,result.ball,'deterministic fixed time step');
}

const base={start:[90,30],goal:[330,450],stars:[]};
let s=P.create(base,[]);for(let i=0;i<60;i++)P.step(s);
assert(Math.abs(s.ball.x-90)<.001,'gravity has no horizontal component');
assert(s.ball.y>80&&s.ball.vy>200,'free fall accelerates');

s=P.create({...base,start:[180,45]},[[[0,150],[360,150]]]);
s.ball.vy=460;for(let i=0;i<240;i++)P.step(s);
assert(s.ball.y<140,'fast ball cannot tunnel through a thin horizontal line');
assert(Math.abs(s.ball.vy)<5,'resting contact remains stable');

for(const sign of [-1,1]){
  s=P.create({...base,start:[180,60]},[[[20,sign===1?90:250],[340,sign===1?250:90]]]);
  for(let i=0;i<120;i++)P.step(s);
  assert(s.ball.vx*sign>20,'slope produces motion toward the lower side');
}

const springLevel=levels[9];s=P.create(springLevel,[]);let fired=false;
while(s.status==='running'){P.step(s);if(s.events.includes('spring')){assert(s.ball.vy<0);assert(s.ball.vx>0);fired=true;}}
assert(fired,'spring actually gives an upward impulse');
s=P.create({...base,fans:[{x:0,y:0,w:360,h:500,force:100}]},[]);
for(let i=0;i<60;i++)P.step(s);assert(s.ball.vx>40,'wind applies a force');

// Stars are earned only on reaching the goal; collection alone never marks success.
s=P.simulate({...base,stars:[[90,130],[90,250]]},[]).state;
assert.equal(s.status,'lost');assert.deepEqual(s.collected,[true,true]);

assert.deepEqual(normalize(null),{stars:Array(12).fill(0),selected:0,unlocked:0,sound:true});
assert.equal(normalize({selected:11}).selected,0);
assert.equal(normalize({stars:[3,1,0,3],selected:11}).unlocked,2);
assert.deepEqual(normalize({stars:[Infinity,-1,4,1.3,'3']}).stars.slice(0,5),[0,0,3,0,0]);
assert.equal(normalize({stars:Array(12).fill(3),selected:11,sound:false}).selected,11);
assert.equal(normalize({stars:Array(12).fill(3),selected:11,sound:false}).sound,false);
console.log('PASS: 12 solvable routes, all 24 collectibles, gravity, ramps, high-speed contact, wind, spring impulse, deterministic simulation and saved progression.');
