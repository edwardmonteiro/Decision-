const assert=require('node:assert/strict');
const L=require('../app/src/main/assets/levels.js');
let checks=0;function check(value,msg){checks++;assert(value,msg)}
let p=L.normalize({});check(L.unlocked(p)===1&&L.total(p)===0,'new game starts at level one');
for(let level=1;level<=6;level++){
 p.selected=level;
 for(let round=0;round<3;round++){
  const run=L.create(p),c=run.challenge;check(run.round===round,'next unearned stage');
  const choices=['bridge','boat','jump'].filter(s=>L.crosses(c,s)&&L.accepts(run.goal,s));check(choices.length>0,'every stage solvable');
  const saved=p.stars[level-1];check(L.grade(p,run,c,'unclear')==='failed'&&p.stars[level-1]===saved,'failed idea preserves stars');
  const wrong=['bridge','boat','jump'].find(s=>L.crosses(c,s)&&!L.accepts(run.goal,s));if(wrong)check(L.grade(p,run,c,wrong)==='goal'&&p.stars[level-1]===saved,'bridge cannot bypass a boat or jump lesson');
  const custom={...c,current:c.current==='calm'?'fast':'calm'};check(!L.eligible(run,custom),'voice rule change enters practice');check(L.eligible(run,{...c,theme:'night'}),'sky change keeps eligibility');
  check(['star','level_complete'].includes(L.grade(p,run,c,choices[0])),'stage grants star');
  check(L.grade(p,run,c,choices[0])==='replay'&&p.stars[level-1]===saved+1,'duplicate result cannot farm stars');
  const restored=L.normalize(JSON.parse(JSON.stringify(p)));check(restored.stars[level-1]===saved+1,'progress survives storage');
 }
 check(L.unlocked(p)===Math.min(6,level+1),'three unique lessons unlock next level');
}
check(L.total(p)===18,'campaign has exactly eighteen stars');
p.selected=2;let r=L.create(p);check(L.grade(p,r,r.challenge,'boat')==='replay'&&L.total(p)===18,'replaying completed levels adds no stars');
for(let level=1;level<=6;level++)for(let round=0;round<3;round++)for(const width of [.24,.50])for(const current of ['calm','fast'])for(const cargo of [false,true]){
 let state={stars:[3,3,3,3,3,3],selected:level};let run=L.create(state,{width,current,cargo,theme:'night',reason:'try_new',source:'openai_agents'},round);
 check(['bridge','boat','jump'].some(s=>L.crosses(run.challenge,s)&&L.accepts(run.goal,s)),'agent candidate projection keeps goal feasible');
 check(run.challenge.width>=.24&&run.challenge.width<=.50,'agent candidate respects native bounds');
 check(L.crosses(run.challenge,run.challenge.focus),'native focus validator accepts projected lesson');
}
let fresh=L.normalize({});r=L.create(fresh);L.grade(fresh,r,r.challenge,'bridge');L.revoke(fresh,r);check(fresh.stars[0]===0&&!r.awarded,'explicit recognition correction revokes mistaken award');L.grade(fresh,r,r.challenge,'boat');check(fresh.stars[0]===1,'corrected valid idea can earn once');
check(L.normalize({stars:[-5,'3',99,3,3,3],selected:6}).selected===1,'bad stored progression cannot unlock later levels');
for(const value of [null,{},[],{stars:'garbage'},{stars:[NaN,Infinity],selected:NaN}]){const clean=L.normalize(value);check(L.total(clean)===0&&clean.selected===1,'malformed progress safely resets progression only');}
console.log(`PASS ${checks} curriculum assertions; 18 goals, unlocks, replay, voice eligibility, correction and all agent-condition combinations.`);
