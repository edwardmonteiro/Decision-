(function(root){
  'use strict';
  const R=typeof module!=='undefined'?require('./race.js'):root.LumiRace;
  const tracks=[
    {name:'A primeira volta',kind:'Oval',width:66,nodes:[[180,404],[96,385],[64,300],[64,180],[95,88],[180,67],[269,94],[296,183],[296,303],[266,386]],hint:'Comece na bandeira, contorne a pista e volte ao mesmo lugar.'},
    {name:'Curvas de algodão',kind:'Curvas abertas',width:64,nodes:[[180,409],[92,395],[60,320],[82,247],[65,146],[104,78],[189,63],[277,100],[301,185],[267,263],[298,338],[260,401]],hint:'Faça curvas suaves. O carro acompanha melhor uma linha sem pontas.'},
    {name:'Vai e vem',kind:'Chicane',width:63,nodes:[[180,415],[90,393],[57,310],[104,233],[61,153],[89,82],[178,62],[272,94],[300,174],[255,247],[299,323],[266,396]],hint:'Siga o vai e vem da pista. Evite cortar pelo meio das curvas.'},
    {name:'O grande cotovelo',kind:'Curva fechada',width:62,nodes:[[176,410],[92,394],[62,315],[62,170],[99,88],[200,63],[291,111],[291,192],[228,220],[165,167],[156,253],[263,287],[290,366],[248,407]],hint:'Na curva fechada, use a largura da pista para arredondar seu traçado.'},
    {name:'Dança das curvas',kind:'Sequência de curvas',width:62,nodes:[[181,411],[92,394],[59,315],[118,256],[74,187],[83,108],[156,67],[253,74],[296,140],[245,207],[295,276],[293,355],[257,402]],hint:'No modo Drift, o carro vira e desliza. Deixe espaço para a traseira.'},
    {name:'Circuito Lumi',kind:'Volta completa',width:62,nodes:[[182,415],[83,391],[62,316],[101,246],[58,160],[91,87],[182,62],[275,92],[297,168],[247,224],[292,287],[296,356],[262,407]],hint:'Experimente outro traçado e compare seu tempo no mesmo modo.'}
  ].map((t,id)=>({...t,id,road:R.circuit(t.nodes)}));
  function normalize(raw){const records=tracks.map((_,i)=>{const r={};for(const key of Object.keys(R.MODES)){const n=raw?.records?.[i]?.[key];r[key]=typeof n==='number'&&Number.isFinite(n)&&n>=3&&n<=60?n:null;}return r;});let unlocked=0;while(unlocked<tracks.length-1&&Object.values(records[unlocked]).some(n=>n!==null))unlocked++;return {records,unlocked,selected:Number.isInteger(raw?.selected)?Math.max(0,Math.min(unlocked,raw.selected)):0,mode:R.MODES[raw?.mode]?raw.mode:'cruise',sound:raw?.sound!==false};}
  const api={tracks,normalize};if(typeof module!=='undefined')module.exports=api;else root.LumiTracks=api;
})(typeof window!=='undefined'?window:globalThis);
