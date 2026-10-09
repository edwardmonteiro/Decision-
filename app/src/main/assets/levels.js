'use strict';
// Local curriculum: Agents may vary a river within the current lesson's bounds.
// Star awards depend on a distinct lesson, not repeated crossings of one river.
(()=>{
 const steps=(widths,current,cargo,goal)=>widths.map(width=>({width,current,cargo,goal}));
 const levels=[
  {name:'Primeiros passos',description:'Um rio calmo. Experimente suas ideias.',theme:'meadow',steps:steps([.25,.28,.30],'calm',false,'any')},
  {name:'Vamos navegar',description:'Rios largos para aprender a usar o barco.',theme:'meadow',steps:steps([.36,.42,.48],'calm',false,'boat')},
  {name:'Salto certeiro',description:'Observe a distância e desenhe seu salto.',theme:'sunset',steps:[{width:.25,current:'calm',cargo:false,goal:'jump'},{width:.28,current:'calm',cargo:false,goal:'jump'},{width:.31,current:'fast',cargo:false,goal:'jump'}]},
  {name:'Contra a corrente',description:'A água está rápida. Construa uma passagem.',theme:'sunset',steps:steps([.36,.42,.49],'fast',false,'bridge')},
  {name:'Com a mochila',description:'Lumi precisa levar a bagagem com segurança.',theme:'night',steps:[{width:.35,current:'calm',cargo:true,goal:'boat'},{width:.41,current:'fast',cargo:true,goal:'bridge'},{width:.47,current:'calm',cargo:true,goal:'any'}]},
  {name:'Grande explorador',description:'Uma ideia descansa. Encontre outra solução.',theme:'night',steps:[{width:.45,current:'calm',cargo:true,goal:'no_bridge'},{width:.30,current:'fast',cargo:false,goal:'no_bridge'},{width:.49,current:'fast',cargo:true,goal:'no_boat'}]}
 ];
 const labels={any:'Atravesse do seu jeito',bridge:'Desafio: use uma ponte',boat:'Desafio: use um barco',jump:'Desafio: faça um salto',no_bridge:'Desafio: atravesse sem ponte',no_boat:'Desafio: atravesse sem barco'};
 function normalize(value){
  const stars=Array.from({length:6},(_,i)=>Number.isInteger(value?.stars?.[i])?Math.max(0,Math.min(3,value.stars[i])):0);
  // Unlocks always follow completed lessons, including after malformed storage.
  let unlocked=1;while(unlocked<6&&stars[unlocked-1]===3)unlocked++;
  for(let i=unlocked;i<6;i++)stars[i]=0;
  return {stars,selected:Number.isInteger(value?.selected)?Math.max(1,Math.min(unlocked,value.selected)):1};
 }
 function unlocked(p){let n=1;while(n<6&&p.stars[n-1]===3)n++;return n}
 const total=p=>p.stars.reduce((a,b)=>a+b,0);
 const crosses=(c,s)=>s==='bridge'||s==='boat'&&c.current==='calm'||s==='jump'&&c.width<=.32&&!c.cargo;
 const accepts=(goal,s)=>goal==='any'||goal===s||goal==='no_bridge'&&s!=='bridge'||goal==='no_boat'&&s!=='boat';
 function create(p,candidate=null,replay=0){
  const level=p.selected,round=p.stars[level-1]<3?p.stars[level-1]:replay%3,def=levels[level-1],step=def.steps[round];
  const min=step.width<=.32?.24:.34,max=step.width<=.32?.32:.5;
  const width=candidate?Math.max(min,step.width-.015,Math.min(max,step.width+.015,candidate.width)):step.width;
  const challenge={width,current:step.current,cargo:step.cargo,theme:candidate?.theme||def.theme,focus:'bridge',reason:candidate?.reason||'first',source:candidate?.source==='openai_agents'?'openai_agents':'local'};
  challenge.focus=['bridge','boat','jump'].find(s=>crosses(challenge,s)&&accepts(step.goal,s));
  return {level,round,replay:p.stars[level-1]===3,goal:step.goal,challenge,baseline:{width,current:step.current,cargo:step.cargo},awarded:false,failures:0};
 }
 const eligible=(run,c)=>c.width===run.baseline.width&&c.current===run.baseline.current&&c.cargo===run.baseline.cargo;
 function grade(p,run,c,solution){
  if(!crosses(c,solution))return 'failed';
  if(!eligible(run,c))return 'practice';
  if(!accepts(run.goal,solution))return 'goal';
  if(run.awarded||p.stars[run.level-1]!==run.round)return 'replay';
  p.stars[run.level-1]++;run.awarded=true;return p.stars[run.level-1]===3?'level_complete':'star';
 }
 function revoke(p,run){if(run.awarded&&p.stars[run.level-1]===run.round+1){p.stars[run.level-1]--;run.awarded=false;}}
 function hint(run,c){
  if(!eligible(run,c))return 'Você está explorando um rio personalizado. Use Voltar ao desafio para ganhar estrelas.';
  const solution=['bridge','boat','jump'].find(s=>crosses(c,s)&&accepts(run.goal,s));
  return {bridge:'Faça um caminho com apoios que ligue as duas margens.',boat:'Desenhe um casco para flutuar. A vela é opcional.',jump:'Desenhe uma seta curva de uma margem à outra.'}[solution];
 }
 const api={levels,labels,normalize,unlocked,total,create,eligible,grade,revoke,hint,crosses,accepts};
 if(typeof module!=='undefined')module.exports=api;else window.LumiLevels=api;
})();
