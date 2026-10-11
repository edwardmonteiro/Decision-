(function(root){
  'use strict';
  const levels=[
    {name:'Uma linha, um caminho',chapter:'Primeiros traços',start:[65,80],goal:[282,380],hint:'Comece abaixo da bolinha. Desenhe uma rampa descendo até a casa.',solution:[[[40,130],[295,390]]]},
    {name:'Para o outro lado',chapter:'Primeiros traços',start:[294,70],goal:[76,378],hint:'A bolinha rola para o lado mais baixo. Faça uma rampa para a esquerda.',solution:[[[315,130],[65,395]]]},
    {name:'Uma curva suave',chapter:'Primeiros traços',start:[65,65],goal:[295,407],hint:'Experimente uma curva que comece inclinada e termine perto da casa.',solution:[[[40,115],[80,190],[135,265],[215,337],[315,420]]]},
    {name:'Pelo lado de fora',chapter:'Pequenos desvios',start:[55,60],goal:[292,427],hint:'Passe por baixo da pedra. Deixe a rampa inclinada até a casa.',rails:[[[135,130],[255,130],[255,235],[135,235],[135,130]]],solution:[[[25,105],[88,290],[310,442]]]},
    {name:'Dois andares',chapter:'Pequenos desvios',start:[65,55],goal:[100,435],hint:'Uma rampa leva à direita. A outra recebe a bolinha e leva de volta.',rails:[[[328,185],[328,340]]],solution:[[[40,102],[254,210]],[[329,300],[75,457]]]},
    {name:'A passagem',chapter:'Pequenos desvios',start:[300,55],goal:[66,432],hint:'Passe à direita da pedra. Depois faça uma curva para a esquerda.',rails:[[[100,128],[225,128],[225,240],[100,240],[100,128]]],solution:[[[325,102],[275,294],[45,450]]]},
    {name:'Uma brisa amiga',chapter:'Vento no caminho',start:[55,60],goal:[288,405],hint:'As setas mostram o vento. Uma rampa suave ajuda a aproveitar o empurrão.',fans:[{x:25,y:125,w:300,h:160,force:100}],solution:[[[30,115],[307,422]]]},
    {name:'Contra o vento',chapter:'Vento no caminho',start:[290,60],goal:[70,410],hint:'O vento sopra para a direita. Faça a rampa mais inclinada para a esquerda.',fans:[{x:28,y:120,w:302,h:180,force:80}],solution:[[[315,112],[50,430]]]},
    {name:'Vento na curva',chapter:'Vento no caminho',start:[60,45],goal:[100,437],hint:'Use duas rampas: vá com o vento e depois volte para a casa.',fans:[{x:30,y:100,w:300,h:135,force:60}],rails:[[[328,185],[328,340]]],solution:[[[35,100],[250,204]],[[329,300],[75,457]]]},
    {name:'Pula, bolinha!',chapter:'Pequenos saltos',start:[90,55],goal:[290,265],hint:'Caia na mola dourada. Ela dá o salto; uma rampa pode guiar a chegada.',springs:[{x:62,y:390,w:80,power:445,push:155}],solution:[[[324,174],[300,302]]]},
    {name:'Salto para casa',chapter:'Pequenos saltos',start:[270,55],goal:[75,270],hint:'A mola empurra para a esquerda. Desenhe uma rampa de chegada desse lado.',springs:[{x:232,y:390,w:80,power:445,push:-150}],solution:[[[40,174],[62,315]]]},
    {name:'O grande caminho',chapter:'Pequenos saltos',start:[60,45],goal:[288,286],hint:'Leve a bolinha até a mola. Depois desenhe a rampa de chegada à direita.',springs:[{x:115,y:393,w:95,power:420,push:85}],rails:[[[260,365],[326,365]]],solution:[[[35,105],[160,344]],[[240,224],[324,319]]]}
  ];
  levels.forEach((l,i)=>{l.id=i;l.stars=[];});
  // Hand-authored positions are populated from verified routes, never from player drawings.
  const starPositions=[[[85,159],[190,267]],[[277,153],[183,253]],[[76,159],[176,286]],[[56,160],[163,328]],[[178,159],[277,318]],[[296,170],[189,338]],[[72,145],[187,272]],[[273,144],[165,274]],[[170,153],[272,322]],[[90,337],[226,171]],[[270,337],[139,171]],[[67,141],[166,331]]];
  levels.forEach((l,i)=>{l.stars=starPositions[i]||[];});
  function normalize(raw){const stars=levels.map((_,i)=>Number.isInteger(raw?.stars?.[i])?Math.max(0,Math.min(3,raw.stars[i])):0);let unlocked=0;while(unlocked<levels.length-1&&stars[unlocked]>0)unlocked++;const selected=Number.isInteger(raw?.selected)?Math.max(0,Math.min(unlocked,raw.selected)):0;return {stars,selected,unlocked,sound:raw?.sound!==false};}
  const api={levels,normalize};if(typeof module!=='undefined')module.exports=api;else root.PathLevels=api;
})(typeof window!=='undefined'?window:globalThis);
