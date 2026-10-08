'use strict';
const $=id=>document.getElementById(id), W=$('world'), wc=W.getContext('2d'), D=$('drawing'), dc=D.getContext('2d');
const C={bg:'#07121e',ink:'#ecf5fa',mint:'#85f4cf',muted:'#9db4cb',gold:'#ffe5a1',blue:'#67abdf'};
const icons={gear:'<path d="m10 3 1-2h2l1 2 3 1 2-1 2 2-1 2 1 3 2 1v2l-2 1-1 3 1 2-2 2-2-1-3 1-1 2h-2l-1-2-3-1-2 1-2-2 1-2-1-3-2-1v-2l2-1 1-3-1-2 2-2 2 1z"/><circle cx="12" cy="12" r="3"/>',sound:'<path d="M3 9h4l5-4v14l-5-4H3zM16 8q5 4 0 8M19 5q8 7 0 14"/>'};
const svg=(body,box='0 0 24 24')=>`<svg viewBox="${box}" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
const drawings={bridge:'<path d="M5 44h90M10 40Q50-15 90 40M23 24v19M40 13v30M60 13v30M78 24v19"/>',boat:'<path d="M13 35h75L75 48H26zM49 6v29M52 6l29 24H52zM44 12 25 29h19M8 52q12-6 22 0t22 0t22 0t22 0"/>',jump:'<path d="M10 46Q40-25 86 25M72 23l15 3-1-15M5 51h17M79 51h17"/>'};
const names={bridge:'ponte',boat:'barco',jump:'salto',unclear:'ideia indefinida'};
const solutionSvg=choice=>svg(drawings[choice]||'<path d="M40 15q20-15 24 3 0 10-13 12v6M51 46v1"/>','0 0 100 60');
$('settings').innerHTML=svg(icons.gear);$('listen').innerHTML=svg(icons.sound);
let preferences={sound:true,effects:true,history:[],wins:0};try{preferences={...preferences,...JSON.parse(localStorage.getItem('lumi-river-v1')||'{}')}}catch{};
if(!Array.isArray(preferences.history))preferences.history=[];
const save=()=>{try{localStorage.setItem('lumi-river-v1',JSON.stringify(preferences))}catch{}};
let mode='drawing',strokes=[],active=null,attempt=null,choice=null,choiceSource=null,confidence=0,currentResult=null;
let worldSize={w:390,h:200},drawSize={w:340,h:180},clock=0,lastTime=0,animation=0,paused=false,particles=[],statusTimer=null,planTimer=null,waitToken=0;
let outcomeSyncFailed=false,outcomePromise=Promise.resolve(),minSequence=0,counter=0,requests=new Map(),historyRecorded=new Set();
let audioContext=null;
const configured=()=>!!window.Android?.isConfigured?.();
function sound(hz=523,duration=.16){if(!preferences.sound)return;try{audioContext ||=new (window.AudioContext||window.webkitAudioContext)();audioContext.resume();const o=audioContext.createOscillator(),g=audioContext.createGain();o.type='sine';o.frequency.value=hz;g.gain.setValueAtTime(.001,audioContext.currentTime);g.gain.exponentialRampToValueAtTime(.11,audioContext.currentTime+.01);g.gain.exponentialRampToValueAtTime(.001,audioContext.currentTime+duration);o.connect(g);g.connect(audioContext.destination);o.start();o.stop(audioContext.currentTime+duration)}catch{}}
function speak(text){if(window.LumiVoice?.active())return;if(window.Android?.speak)window.Android.speak(text);else if(window.speechSynthesis){speechSynthesis.cancel();let u=new SpeechSynthesisUtterance(text);u.lang='pt-BR';u.rate=.88;speechSynthesis.speak(u)}}
function uuid(){return crypto.randomUUID?crypto.randomUUID():'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{let r=Math.random()*16|0;return(c==='x'?r:(r&3)|8).toString(16)})}
function nativeRequest(path,method='GET',body={}){return new Promise((resolve,reject)=>{if(!window.Android?.request)return reject(Error('Conecte a OpenAI na área dos responsáveis.'));let id=String(++counter),timeout=setTimeout(()=>{requests.delete(id);reject(Error('A OpenAI demorou para responder. Tente novamente.'))},path==='/activate'?90000:path==='/v1/classify'?50000:30000);requests.set(id,{resolve,reject,timeout});window.Android.request(id,path,method,JSON.stringify(body))})}
window.LumiNativeResult=(id,json)=>{const p=requests.get(id);if(!p)return;clearTimeout(p.timeout);requests.delete(id);try{const v=JSON.parse(json);v.error?p.reject(Error(v.error)):p.resolve(v)}catch{p.reject(Error('Resposta inválida'))}};
function validChallenge(v){if(!v||typeof v.width!=='number'||!Number.isFinite(v.width)||v.width<.24||v.width>.5||typeof v.cargo!=='boolean'||!['calm','fast'].includes(v.current)||!['meadow','sunset','night'].includes(v.theme)||!['bridge','boat','jump'].includes(v.focus)||!['first','repeat_gently','try_new','more_room'].includes(v.reason))return null;if(!canCross(v,v.focus))return null;return {...v,source:v.source==='openai_agents'?'openai_agents':v.source==='voice'?'voice':'local'}}
function canCross(c,s){return s==='bridge'||s==='boat'&&c.current==='calm'||s==='jump'&&c.width<=.32&&!c.cargo}
function localChallenge(){const n=preferences.history.length,last=preferences.history.at(-1),theme=['meadow','sunset','night'][n%3];if(last&&!last.success){return {width:.27,current:'calm',cargo:false,theme,focus:['bridge','boat','jump'].includes(last.choice)?last.choice:'bridge',reason:'repeat_gently',source:'local'}}const levels=[{width:.28,current:'calm',cargo:false,focus:'bridge'},{width:.46,current:'calm',cargo:false,focus:'boat'},{width:.28,current:'fast',cargo:false,focus:'jump'},{width:.42,current:'fast',cargo:true,focus:'bridge'}];return {...levels[n%4],theme,reason:n?'try_new':'first',source:'local'}}
let challenge=localChallenge(),voiceUndo=[];
const plainChallenge=c=>({width:c.width,current:c.current,cargo:c.cargo,theme:c.theme,focus:c.focus,reason:c.reason});
function setMode(v){mode=v;window.LumiVoice?.contextChanged();document.body.dataset.mode=v;$('busy').classList.toggle('hidden',v!=='recognizing'&&v!=='waiting');$('outcome').classList.toggle('hidden',v!=='result');$('drawing-tools').classList.toggle('hidden',v==='result'||v==='waiting'||v==='animating');$('retry').classList.toggle('hidden',v!=='result');$('drawing').style.pointerEvents=v==='drawing'?'auto':'none';$('submit').disabled=v==='recognizing'||v==='animating';$('ideas').disabled=v!=='drawing';updateControls()}
function updateControls(){const has=strokes.length>0;$('undo').disabled=!has||mode!=='drawing';$('clear').disabled=!has||mode!=='drawing';$('draw-placeholder').classList.toggle('hidden',has||mode!=='drawing')}
function feedback(s){$('feedback').textContent=s}
function describeChallenge(){return `${challenge.width<=.32?'Rio estreito':'Rio largo'} · ${challenge.current==='calm'?'água calma':'água rápida'}${challenge.cargo?' · mochila':''}`}
function updateChallengeLabel(){window.LumiVoice?.contextChanged();
 $('conditions').textContent=describeChallenge();
 const reason={first:'primeira travessia',repeat_gently:'uma nova chance',try_new:'outra forma de pensar',more_room:'um pouco mais de espaço'}[challenge.reason];
 $('origin').textContent=challenge.source==='voice'?'Você mudou este rio por voz':challenge.source==='openai_agents'?`Agents criou este rio · ${reason}`:`Desafio local · ${configured()?'Agents disponível':'conecte a IA em ⚙'}`;
}
function resetDrawing(){voiceUndo=[];strokes=[];active=null;attempt=null;choice=null;currentResult=null;animation=0;particles=[];setMode('drawing');$('title').innerHTML='Como<br>atravessar?';$('subtitle').textContent=challenge.cargo?'Lumi também precisa levar a mochila.':'Desenhe uma ideia. Veja acontecer.';$('submit').innerHTML='EXPERIMENTAR <span>→</span>';$('footer-note').textContent='Desenhe uma ponte, um barco ou um salto.';feedback('');updateChallengeLabel();renderDrawing()}
function point(e){const r=D.getBoundingClientRect();return {x:Math.max(.01,Math.min(.99,(e.clientX-r.left)/r.width)),y:Math.max(.02,Math.min(.98,(e.clientY-r.top)/r.height))}}
D.addEventListener('pointerdown',e=>{if(mode!=='drawing'||active||strokes.length>=80)return;e.preventDefault();D.setPointerCapture(e.pointerId);active={pointer:e.pointerId,points:[point(e)]};strokes.push(active.points);attempt=null;feedback('');sound(350,.05);updateControls();renderDrawing()});
D.addEventListener('pointermove',e=>{if(!active||active.pointer!==e.pointerId)return;const p=point(e),last=active.points.at(-1);if(Math.hypot(p.x-last.x,p.y-last.y)>.003&&active.points.length<1000){active.points.push(p);renderDrawing()}});
function endStroke(e){if(active?.pointer===e.pointerId){active=null;renderDrawing()}}
D.addEventListener('pointerup',endStroke);D.addEventListener('pointercancel',endStroke);D.addEventListener('lostpointercapture',endStroke);
$('undo').onclick=()=>{strokes.pop();attempt=null;updateControls();renderDrawing()};$('clear').onclick=()=>{strokes=[];active=null;attempt=null;updateControls();renderDrawing()};
function drawStrokes(ctx,w,h,color,width){ctx.strokeStyle=color;ctx.fillStyle=color;ctx.lineWidth=width;ctx.lineCap='round';ctx.lineJoin='round';for(const stroke of strokes){if(stroke.length===1){ctx.beginPath();ctx.arc(stroke[0].x*w,stroke[0].y*h,width/2,0,Math.PI*2);ctx.fill();continue}ctx.beginPath();stroke.forEach((p,i)=>i?ctx.lineTo(p.x*w,p.y*h):ctx.moveTo(p.x*w,p.y*h));ctx.stroke()}}
function renderDrawing(){dc.clearRect(0,0,drawSize.w,drawSize.h);drawStrokes(dc,drawSize.w,drawSize.h,C.gold,3.5)}
function drawingImage(){const c=document.createElement('canvas');c.width=512;c.height=320;let x=c.getContext('2d');x.fillStyle='#fff';x.fillRect(0,0,512,320);drawStrokes(x,512,320,'#111',5);return c.toDataURL('image/png')}
function hasDrawing(){let distance=0;for(const s of strokes)for(let i=1;i<s.length;i++)distance+=Math.hypot(s[i].x-s[i-1].x,s[i].y-s[i-1].y);return distance>.09}
async function submit(){
 if(mode==='result'){nextChallenge();return}if(mode==='waiting'){++waitToken;clearTimeout(planTimer);challenge=localChallenge();resetDrawing();return}if(mode!=='drawing')return;
 if(!hasDrawing()){feedback('Primeiro, desenhe sua ideia com o dedo.');speak('Desenhe uma ponte, um barco ou uma seta de salto.');return}
 active=null;attempt ||=uuid();sound(660,.09);
 if(!configured()){manualChoice('A IA ainda não está conectada. Escolha o que você desenhou para brincar no modo manual.');return}
 setMode('recognizing');
 document.querySelector('#busy strong').textContent='Lendo sua ideia…';document.querySelector('#busy>span:last-child').textContent='Decisions está olhando o desenho.';
 try{
  const r=await nativeRequest('/v1/classify','POST',{attempt_id:attempt,image:drawingImage()});
  if(!['bridge','boat','jump','unclear'].includes(r.choice)||r.source!=='openai_decisions'||typeof r.confidence!=='number')throw Error('A resposta da IA não trouxe uma solução válida.');
  choiceSource='openai_decisions';confidence=r.confidence;
  if(r.choice==='unclear'){setMode('drawing');feedback('Ainda não entendi. Acrescente detalhes ou veja as ideias.');$('footer-note').textContent='Decisions não reconheceu uma solução. Seu desenho continua aqui.';recordOutcome('unclear','openai_decisions',false);attempt=null;speak('Não entendi ainda. Você pode desenhar mais um pouquinho.');return}
  beginAction(r.choice,'openai_decisions');
 }catch(e){setMode('drawing');feedback(e.message);manualChoice(e.message+' Você pode tentar novamente ou escolher sua ideia no modo manual.')}
}
$('submit').onclick=submit;
function beginAction(selected,source){choice=selected;choiceSource=source;currentResult=canCross(challenge,choice);animation=0;setMode('animating');$('title').textContent=choice==='bridge'?'Uma ponte!':choice==='boat'?'Um barco!':'Um salto!';$('subtitle').textContent='Vamos ver sua ideia acontecer.';$('submit').innerHTML='VAMOS ATRAVESSAR…';$('footer-note').textContent=source==='openai_decisions'?`Decisions reconheceu ${names[choice]} no desenho.`:'Solução escolhida por você · modo manual';feedback('');sound(659);speak($('title').textContent);}
function reasonForFailure(){if(choice==='boat')return 'A correnteza está forte. Uma ponte pode ajudar.';if(challenge.cargo&&choice==='jump')return 'Com a mochila, esse salto fica difícil. Pense em outra passagem.';return 'O rio ficou largo para um salto. Que tal uma ponte ou um barco?'}
function finishAction(){
 setMode('result');$('title').textContent=currentResult?'Sua ideia ganhou vida.':'Vamos tentar de novo?';$('subtitle').textContent=currentResult?'Lumi chegou à outra margem!':'Lumi ficou seguro na margem.';
 $('solution-icon').innerHTML=solutionSvg(choice);$('outcome-title').textContent=currentResult?({bridge:'Virou um caminho!',boat:'Vamos navegar!',jump:'Que salto!'}[choice]):'Outra ideia pode funcionar.';
 $('outcome-description').textContent=currentResult?({bridge:'Sua ponte conectou as duas margens.',boat:'Seu barco atravessou a água calma.',jump:'Seu salto alcançou o outro lado.'}[choice]):reasonForFailure();
 $('recognition').textContent=choiceSource==='openai_decisions'?`Decisions viu ${names[choice]} · ${Math.round(confidence*100)}% de confiança`:'Você escolheu a solução · modo manual';
 $('correct').classList.toggle('hidden',choiceSource!=='openai_decisions');$('submit').innerHTML='PRÓXIMO DESAFIO <span>→</span>';$('footer-note').textContent=configured()?'Agents prepara o próximo rio usando esta tentativa.':'A próxima travessia local usa esta tentativa.';
 recordOutcome(choice,choiceSource,currentResult);
 if(currentResult){preferences.wins++;save();for(let i=0;i<(preferences.effects?28:6);i++)particles.push({x:worldSize.w*.8,y:worldSize.h*.48,vx:(Math.random()-.5)*160,vy:-40-Math.random()*100,life:1+Math.random()});[523,659,784].forEach((f,i)=>setTimeout(()=>sound(f,.25),i*130))}else sound(392,.3);
}
function recordOutcome(selected,source,success){
 const id=attempt;if(!id||historyRecorded.has(id))return;historyRecorded.add(id);
 const entry={choice:selected,success,source,challenge:plainChallenge(challenge)};preferences.history.push(entry);preferences.history=preferences.history.slice(-8);save();
 outcomeSyncFailed=true;
 if(configured()){
  const payload={attempt_id:id,choice:selected,source,challenge:plainChallenge(challenge)};
  outcomePromise=outcomePromise.catch(()=>{}).then(()=>nativeRequest('/v1/outcomes','POST',payload)).then(r=>{outcomeSyncFailed=false;minSequence=r.sequence||minSequence;return r}).catch(e=>{if(mode==='result')$('footer-note').textContent='Resultado salvo no celular. Agents não recebeu: '+e.message;return null});
 }
}
$('retry').onclick=resetDrawing;
$('correct').onclick=()=>manualChoice('A IA pode interpretar um desenho de outro jeito. Qual era sua ideia?',true);
function manualChoice(message,correction=false){
 modal(`<h2 id="panel-title">Qual é sua ideia?</h2><p id="manual-message"></p><div class="choice-list">${['bridge','boat','jump'].map(k=>`<button data-choice="${k}">${solutionSvg(k)}${names[k]}</button>`).join('')}</div><p class="small-note">Esta escolha será marcada como manual. Nenhum reconhecimento de IA será simulado.</p><button id="manual-back">Voltar ao desenho</button>`);
 $('manual-message').textContent=message;
 document.querySelectorAll('[data-choice]').forEach(b=>b.onclick=()=>{dismiss();if(correction)attempt=uuid();attempt ||=uuid();beginAction(b.dataset.choice,'manual')});
 $('manual-back').onclick=()=>{dismiss();if(correction)resetDrawing()};
}
async function nextChallenge(){
 await outcomePromise;clearTimeout(planTimer);const token=++waitToken;strokes=[];renderDrawing();
 if(!configured()||outcomeSyncFailed){challenge=localChallenge();resetDrawing();if(outcomeSyncFailed&&configured())feedback('A última tentativa ficou só no celular. Este desafio é local.');return}
 setMode('waiting');$('title').textContent='Uma nova travessia.';$('subtitle').textContent='Agents está usando sua última ideia.';document.querySelector('#busy strong').textContent='Preparando o próximo rio…';document.querySelector('#busy>span:last-child').textContent='Você pode brincar enquanto ele fica pronto.';$('submit').innerHTML='BRINCAR ENQUANTO PREPARA';$('footer-note').textContent='Se escolher continuar agora, o desafio será local.';feedback('');pollChallenge(token,0,true);
}
async function pollChallenge(token,n,waiting){
 try{const r=await nativeRequest('/v1/challenge');if(token!==waitToken)return;const c=validChallenge(r.challenge);if(c&&(r.challenge.planned_after??0)>=minSequence){if(!waiting&&(mode!=='drawing'||strokes.length))return;challenge=c;resetDrawing();if(waiting)sound(784);return}
 if(waiting&&n<36&&r.busy){planTimer=setTimeout(()=>pollChallenge(token,n+1,waiting),3000);return}
 if(waiting){feedback(r.message||'O desafio ainda não ficou pronto.');document.querySelector('#busy strong').textContent='O rio ainda está em preparo.';document.querySelector('#busy>span:last-child').textContent='Continue com uma travessia local.'}
 }catch(e){if(token===waitToken&&waiting){feedback(e.message);document.querySelector('#busy strong').textContent='Vamos brincar no celular?';document.querySelector('#busy>span:last-child').textContent='A conexão não interrompe sua aventura.'}}
}
function modal(html){window.LumiVoice?.stop('Voz desligada ao abrir o menu.');clearTimeout(statusTimer);$('overlay').classList.remove('hidden');$('panel').innerHTML=html;active=null}
function dismiss(){clearTimeout(statusTimer);$('overlay').classList.add('hidden');$('panel').innerHTML=''}
$('ideas').onclick=()=>{modal(`<h2 id="panel-title">Uma ideia vira aventura.</h2><p>Use estes desenhos como inspiração. Depois desenhe do seu jeito.</p><div class="choice-list">${['bridge','boat','jump'].map(k=>`<button data-example="${k}">${solutionSvg(k)}${names[k]}</button>`).join('')}</div><div id="example-detail"></div><button id="back" class="primary">VOU DESENHAR</button>`);document.querySelectorAll('[data-example]').forEach(b=>b.onclick=()=>{const k=b.dataset.example;$('example-detail').innerHTML=`<div class="trace-example">${solutionSvg(k)}</div><p>${{bridge:'Um caminho com apoios liga as margens.',boat:'Desenhe o casco. A vela é opcional.',jump:'Uma seta curva mostra para onde saltar.'}[k]}</p>`;speak({bridge:'Uma ponte tem um caminho com apoios.',boat:'Um barco tem um casco. Pode ter uma vela.',jump:'Faça uma seta curva para mostrar o salto.'}[k])});$('back').onclick=dismiss};
$('listen').onclick=()=>{sound(523,.05);speak(`Como atravessar? ${describeChallenge().replaceAll(' · ','. ')}. Desenhe uma ponte, um barco ou uma seta de salto. Depois toque em experimentar.`)};
$('settings').onclick=settings;
function settings(){modal(`<h2 id="panel-title">Do seu jeito.</h2><div class="row">Som<button id="toggle-sound">${preferences.sound?'Ligado':'Desligado'}</button></div><div class="row">Movimento<button id="toggle-effects">${preferences.effects?'Suave':'Reduzido'}</button></div><p>Desenhe uma solução e toque em Experimentar. Cada ideia muda o que acontece no rio.</p><p>${preferences.wins} travessias concluídas neste celular.</p><button id="parent">Área dos responsáveis</button><button id="back" class="primary">VOLTAR</button>`);$('toggle-sound').onclick=()=>{preferences.sound=!preferences.sound;save();settings()};$('toggle-effects').onclick=()=>{preferences.effects=!preferences.effects;save();settings()};$('parent').onclick=parentGate;$('back').onclick=dismiss}
function parentGate(){const a=11+Math.floor(Math.random()*8),b=4+Math.floor(Math.random()*5);modal(`<h2 id="panel-title">Para os responsáveis.</h2><p>Para continuar, quanto é ${a} + ${b}?</p><label for="answer">Sua resposta</label><input id="answer" type="number" inputmode="numeric"><p id="gate-error" role="status"></p><button id="confirm" class="primary">CONTINUAR</button><button id="back">Voltar</button>`);$('confirm').onclick=()=>Number($('answer').value)===a+b?parentSettings():$('gate-error').textContent='Tente mais uma vez.';$('back').onclick=settings}
function parentSettings(){let connected=configured();modal(`<h2 id="panel-title">A IA nesta aventura.</h2><p class="small-note">LUMI Travessias · v0.4.0</p><p><b>Decisions</b> recebe somente o desenho e classifica ponte, barco, salto ou indefinido.<br><b>Agents</b> recebe até 8 tentativas, sem imagens, e prepara o próximo rio.</p><p><b>Voz · GPT-Live-1</b> conversa em português e chama ferramentas para mudar o rio. Toque em Falar na tela do jogo. O áudio vai direto para a OpenAI somente enquanto a voz estiver ligada. Voz de IA Bossa; comandos interpretados por GPT-6 Luna.</p><p id="connection-status" class="status"></p>${['decisions','session','environment','vault'].map(k=>`<div class="service-row"><b>${k==='decisions'?'Decisions · o desenho':k==='session'?'Agents / Session · próximo rio':k==='environment'?'Environment · preparo':'Vault · sem credenciais externas'}</b><span id="service-${k}">Não confirmado</span></div>`).join('')}<p id="service-evidence" class="evidence"></p><button id="connect" class="primary">${connected?'TROCAR CHAVE OPENAI':'CONECTAR OPENAI'}</button>${connected?'<button id="test-ai">TESTAR SERVIÇOS</button><button id="disconnect">Remover chave deste celular</button>':''}<p class="small-note">Ao experimentar, o desenho vai para a OpenAI. Evite nomes e dados pessoais. API cobrada na sua conta. Limites locais: 24 leituras, 12 sessões Agents e 6 conversas de voz de até 2 minutos por dia. Voz: US$ 0,05/minuto mais processamento de comandos; uma conexão iniciada pode cobrar 15 segundos. Áudio e legendas não são salvos pelo aplicativo. A chave fica criptografada no Android. Vault vazio, anexado às sessões.</p><p><b>Últimas ideias</b></p><div id="history"></div><button id="back">Voltar</button>`);
 $('connection-status').textContent=connected?'Consultando serviços…':'Modo manual. Conecte sua conta para reconhecer desenhos.';
 $('history').textContent='';for(const row of preferences.history.slice(-5).reverse()){let el=document.createElement('div');el.className='history-item';el.textContent=`${names[row.choice]||'Ideia'} · ${row.success?'atravessou':'nova tentativa'} · ${row.source==='openai_decisions'?'Decisions':'manual'}`;$('history').append(el)}
 $('connect').onclick=()=>{if(window.Android?.openOpenAISetup)window.Android.openOpenAISetup();else $('connection-status').textContent='A conexão segura está disponível no APK Android.'};
 if($('test-ai'))$('test-ai').onclick=async()=>{const button=$('test-ai');button.disabled=true;$('connection-status').textContent='Testando um desenho de exemplo e preparando um rio…';try{await nativeRequest('/activate','POST');await refreshConnectionStatus()}catch(e){if($('connection-status'))$('connection-status').textContent=e.message}finally{if(button.isConnected)button.disabled=false}};
 if($('disconnect'))$('disconnect').onclick=()=>{window.Android.clearConfig();parentSettings()};$('back').onclick=settings;if(connected)refreshConnectionStatus();
}
async function refreshConnectionStatus(){clearTimeout(statusTimer);if(!$('service-decisions'))return;try{let r=await nativeRequest('/health');if(!$('service-decisions'))return;for(let k of ['decisions','session','environment','vault'])$('service-'+k).textContent=r[k]||'Não confirmado';$('connection-status').textContent=r.ready?'Serviços verificados com chamadas reais.':r.busy?'OpenAI trabalhando.':'Confira o resultado de cada serviço.';$('service-evidence').textContent=[`Hoje: ${r.daily_decisions||0}/24 leituras · ${r.daily_sessions||0}/12 sessões`,r.request_id?'Requisição Decisions: '+r.request_id:'',r.session_id?'Sessão: '+r.session_id:'',r.environment_id?'Ambiente: '+r.environment_id:'',r.vault_id?'Vault: '+r.vault_id:''].filter(Boolean).join('\n')}catch(e){if($('connection-status'))$('connection-status').textContent=e.message}if($('service-decisions'))statusTimer=setTimeout(refreshConnectionStatus,3500)}
window.LumiConnectionChanged=message=>{if($('service-decisions')){parentSettings();$('connection-status').textContent=message}updateChallengeLabel()};
window.LumiPause=()=>{window.LumiVoice?.background();paused=true;active=null;audioContext?.suspend()};window.LumiResume=()=>{paused=false;lastTime=0};window.LumiBack=()=>{if(!$('overlay').classList.contains('hidden'))dismiss();else settings()};
document.addEventListener('visibilitychange',()=>document.hidden?window.LumiPause():window.LumiResume());
function resize(){for(const [canvas,key] of [[W,'world'],[D,'draw']]){let r=canvas.getBoundingClientRect(),dpr=Math.min(2,devicePixelRatio||1);canvas.width=Math.max(1,Math.round(r.width*dpr));canvas.height=Math.max(1,Math.round(r.height*dpr));canvas.getContext('2d').setTransform(dpr,0,0,dpr,0,0);if(key==='world')worldSize={w:r.width,h:r.height};else drawSize={w:r.width,h:r.height}}renderDrawing()}
new ResizeObserver(resize).observe($('app'));new ResizeObserver(resize).observe($('drawing-area'));
function line(path,color=C.mint,width=1.8){wc.beginPath();path();wc.strokeStyle=color;wc.lineWidth=width;wc.lineCap='round';wc.lineJoin='round';wc.stroke()}
function star(x,y,r){line(()=>{for(let i=0;i<=10;i++){let a=i*Math.PI/5-Math.PI/2,rr=i%2?r*.45:r;i?wc.lineTo(x+Math.cos(a)*rr,y+Math.sin(a)*rr):wc.moveTo(x+Math.cos(a)*rr,y+Math.sin(a)*rr)}},C.gold,2.2)}
function tree(x,base,h){line(()=>{wc.moveTo(x,base);wc.lineTo(x,base-h*.65);wc.moveTo(x,base-h*.28);wc.lineTo(x-h*.15,base-h*.43);wc.moveTo(x,base-h*.4);wc.lineTo(x+h*.16,base-h*.55);wc.moveTo(x-h*.17,base-h*.35);wc.bezierCurveTo(x-h*.46,base-h*.38,x-h*.43,base-h*.7,x-h*.24,base-h*.75);wc.bezierCurveTo(x-h*.22,base-h*1.14,x+h*.27,base-h*1.14,x+h*.28,base-h*.76);wc.bezierCurveTo(x+h*.55,base-h*.65,x+h*.34,base-h*.32,x-h*.17,base-h*.35)},C.mint,1.7)}
function character(x,y,walk=0){wc.save();wc.translate(x,y);line(()=>{wc.moveTo(-6,-7);wc.lineTo(-7-Math.sin(walk)*3,0);wc.moveTo(5,-7);wc.lineTo(6+Math.sin(walk)*3,0);wc.moveTo(-10,-20);wc.quadraticCurveTo(-21,-18,-17,-9);wc.moveTo(10,-20);wc.quadraticCurveTo(17,-19,15,-10)},C.mint,2.2);wc.fillStyle=C.bg;wc.beginPath();wc.ellipse(0,-27,16,17,0,0,Math.PI*2);wc.fill();line(()=>wc.ellipse(0,-27,16,17,0,0,Math.PI*2),C.mint,2.2);line(()=>{wc.moveTo(0,-44);wc.quadraticCurveTo(-10,-57,-12,-47);wc.quadraticCurveTo(-8,-44,0,-44);wc.quadraticCurveTo(12,-61,12,-50);wc.quadraticCurveTo(10,-43,0,-44)},C.mint,1.8);line(()=>{wc.moveTo(3,-30);wc.lineTo(3,-25);wc.moveTo(10,-30);wc.lineTo(10,-25)},C.mint,2.5);if(challenge.cargo){line(()=>wc.rect(-23,-24,10,14),C.gold,1.8)}wc.restore()}
function drawWorld(dt){const {w,h}=worldSize;wc.clearRect(0,0,w,h);let sceneH=Math.max(75,h-45),base=sceneH*.63,left=w*(.5-challenge.width/2),right=w*(.5+challenge.width/2),gap=right-left,water=base+22,depth=Math.min(35,sceneH*.2);let p=Math.min(1,animation/3.4),travel=Math.max(0,Math.min(1,(p-.18)/.70));let completed=mode==='result',acting=mode==='animating'||completed;
 if(challenge.theme==='night'){for(let i=0;i<7;i++){wc.fillStyle='#809dbc';wc.beginPath();wc.arc(w*(.12+i*.13),h*(.11+(i%3)*.055),1,0,7);wc.fill()}line(()=>wc.arc(w*.76,h*.13,9,.4,5.5),C.gold,1.4)}else if(challenge.theme==='sunset'){wc.fillStyle='#ffe5a115';wc.beginPath();wc.arc(w*.5,h*.19,18,0,7);wc.fill();line(()=>wc.arc(w*.5,h*.19,18,0,7),'#d9b792',1.2)}
 tree(w*.075,base,Math.min(sceneH*.68,97));tree(w*.94,base,Math.min(sceneH*.58,84));
 wc.fillStyle='#163d5c55';wc.beginPath();wc.moveTo(left,water);wc.lineTo(right,water);wc.lineTo(right-9,water+depth);wc.lineTo(left+10,water+depth);wc.closePath();wc.fill();
 wc.save();wc.beginPath();wc.rect(left+1,water-3,gap-2,depth+9);wc.clip();for(let j=0;j<4;j++){line(()=>{for(let x=left-12;x<=right+14;x+=3){let y=water+j*depth/3+Math.sin(x*.065+clock*(challenge.current==='fast'?4:1.3)+j)*2.3;x===left-12?wc.moveTo(x,y):wc.lineTo(x,y)}},j===0?'#4f91ca':'#75b5e0',j===0?1.8:1.2)}wc.restore();
 line(()=>{wc.moveTo(0,base);wc.lineTo(left-8,base);wc.quadraticCurveTo(left+5,base+2,left,base+13);wc.quadraticCurveTo(left-6,base+17,left+9,water+depth+3);wc.moveTo(right-9,water+depth+3);wc.quadraticCurveTo(right+3,base+20,right,base+12);wc.quadraticCurveTo(right-6,base+1,right+8,base);wc.lineTo(w,base)},C.mint,2.1);
 for(let x of [w*.03,w*.18,right+12,w*.88])line(()=>{wc.moveTo(x,base);wc.lineTo(x-3,base-6);wc.moveTo(x+3,base);wc.lineTo(x+5,base-8)},C.mint,1.4);
 let cx=left-28,cy=base-2;
 if(acting&&choice==='bridge'){
  const reveal=Math.min(1,p/.22);wc.save();wc.beginPath();wc.rect(left-12,base-52,(gap+24)*reveal,70);wc.clip();line(()=>{wc.moveTo(left-9,base-2);wc.lineTo(right+9,base-2);wc.moveTo(left-6,base-6);wc.quadraticCurveTo(w*.5,base-62,right+6,base-6);for(let j=1;j<=5;j++){let x=left+gap*j/6,y=base-6-28*Math.sin(Math.PI*j/6);wc.moveTo(x,base-3);wc.lineTo(x,y)}},C.gold,2.3);wc.restore();cx=left-28+(gap+65)*travel;cy=base-2;
 }else if(acting&&choice==='boat'){
  let sail=currentResult?travel:Math.sin(travel*Math.PI)*.24;let bx=left+14+(gap-27)*sail,by=water+3+Math.sin(clock*3)*1.5;
  line(()=>{wc.moveTo(bx-22,by);wc.lineTo(bx+23,by);wc.lineTo(bx+14,by+12);wc.lineTo(bx-13,by+12);wc.closePath();wc.moveTo(bx+6,by);wc.lineTo(bx+6,by-43);wc.lineTo(bx+28,by-9);wc.lineTo(bx+6,by-9)},C.gold,1.9);
  cx=p<.18?left-28:bx-5;cy=p<.18?base-2:by-2;if(p>.88){cx=currentResult?right+31:left-28;cy=base-2}
 }else if(acting&&choice==='jump'){
  let move=currentResult?travel:Math.sin(travel*Math.PI)*.20;cx=left-28+(gap+60)*move;cy=base-2-Math.sin(travel*Math.PI)*Math.min(h*.38,72);
  line(()=>{wc.setLineDash([3,6]);wc.moveTo(left-22,base-12);wc.quadraticCurveTo(w*.5,base-110,currentResult?right+27:left+10,base-12);wc.setLineDash([])},'#b8a57e',1.1);
 }
 if(!acting||!currentResult||p<.97)star(right+37,base-20,11);else{wc.globalAlpha=.65;star(right+37,base-46+Math.sin(clock*3)*3,7);wc.globalAlpha=1}
 character(cx,cy,mode==='animating'?clock*13:0);
 for(const pt of particles){pt.x+=pt.vx*dt;pt.y+=pt.vy*dt;pt.vy+=60*dt;pt.life-=dt;wc.globalAlpha=Math.max(0,Math.min(1,pt.life));line(()=>{wc.moveTo(pt.x,pt.y);wc.lineTo(pt.x+3,pt.y-3)},C.gold,1.5)}particles=particles.filter(x=>x.life>0);wc.globalAlpha=1;
}
function tick(t){let dt=lastTime?Math.min(.04,(t-lastTime)/1000):0;lastTime=t;if(!paused&&$('overlay').classList.contains('hidden')){clock+=dt*(preferences.effects?1:.35);if(mode==='animating'){animation+=dt;if(animation>=3.4)finishAction()}drawWorld(dt)}requestAnimationFrame(tick)}
window.lumiState=()=>({mode,challenge:{...challenge},choice,source:choiceSource,success:currentResult,strokes:strokes.length,history:preferences.history.slice(),animation,paused});
function voiceRequest(action,body={}){return new Promise((resolve,reject)=>{
 const method={prepare:'prepareVoice',start:'startVoice'}[action];
 if(!method||!window.Android?.[method])return reject(Error('Instale o APK atualizado para conversar por voz.'));
 const id=String(++counter),timeout=setTimeout(()=>{requests.delete(id);reject(Error('A conexão de voz demorou. Tente novamente.'))},action==='prepare'?60000:30000);
 requests.set(id,{resolve,reject,timeout});if(action==='start')window.Android[method](id,JSON.stringify(body));else window.Android[method](id);
})}
function voiceRiver(){return {status:'ok',mode,river:plainChallenge(challenge),description:describeChallenge(),hint:challenge.current==='fast'?'A correnteza está forte. Pense em um caminho que fique acima da água.':challenge.width>.32?'O rio está largo. Pense em algo que flutue ou que ligue as duas margens.':challenge.cargo?'Lumi leva uma mochila. Pense em uma passagem firme.':'O rio é estreito e calmo. Você pode experimentar ideias diferentes.',has_drawing:strokes.length>0}}
window.LumiVoiceGame={request:voiceRequest,configured,read:voiceRiver,notice:feedback,
 intro:()=>{modal(`<h2 id="panel-title">Converse com a Lumi.</h2><p>“Fica de noite”, “deixa mais fácil” ou “me dá uma dica”. Sua voz pode transformar o rio.</p><p>A Lumi tem uma voz de IA. Enquanto estiver ligada, seu áudio vai para a OpenAI. Evite nomes e dados pessoais.</p><p class="small-note">Usa a chave já conectada. API cobrada na sua conta. Até 2 minutos por conversa e 6 conversas por dia. Você pode desligar a qualquer momento.</p><button id="start-talking" class="primary">LIGAR MICROFONE</button><button id="back">Agora não</button>`);$('start-talking').onclick=()=>{preferences.voiceIntro=true;save();dismiss();window.LumiVoice.start()};$('back').onclick=dismiss},
 seenIntro:()=>preferences.voiceIntro===true,
 apply:(name,args)=>{
  if(!args||typeof args!=='object'||Array.isArray(args))return {status:'invalid'};
  if(name==='get_river'&&Object.keys(args).length===0)return voiceRiver();
  if(name!=='change_river'||Object.keys(args).length!==1||typeof args.command!=='string')return {status:'unsupported'};
  if(paused||mode!=='drawing'||active||!$('overlay').classList.contains('hidden'))return {status:'busy',message:'Espere a travessia terminar e volte ao desenho para mudar o rio.'};
  const command=args.command,changes={night:{theme:'night'},day:{theme:'meadow'},sunset:{theme:'sunset'},calm:{current:'calm'},fast:{current:'fast'},narrow:{width:.26},wide:{width:.48},easier:{width:.26,current:'calm',cargo:false},harder:{width:.48,current:'fast',cargo:true},add_backpack:{cargo:true},remove_backpack:{cargo:false}};
  const messages={night:'A noite chegou. Olhe as estrelas!',day:'O dia voltou ao rio.',sunset:'O pôr do sol apareceu.',calm:'A água ficou calma.',fast:'A correnteza ficou mais rápida.',narrow:'As margens ficaram mais perto.',wide:'O rio ficou mais largo.',easier:'Rio estreito, água calma e sem mochila.',harder:'Rio largo, água rápida e uma mochila!',add_backpack:'Lumi colocou a mochila.',remove_backpack:'Lumi guardou a mochila.',undo:'A última mudança foi desfeita.'};
  if(command==='undo'){if(!voiceUndo.length)return {status:'unchanged',message:'Não há mudança por voz para desfazer neste rio.'};challenge=voiceUndo.pop()}
  else {if(!Object.hasOwn(changes,command))return {status:'unsupported'};const next={...challenge,...changes[command],source:'voice'};if(!canCross(next,next.focus))next.focus='bridge';if(!validChallenge(next))return {status:'invalid'};voiceUndo.push({...challenge});voiceUndo=voiceUndo.slice(-10);challenge=next;}
  ++waitToken;clearTimeout(planTimer);attempt=null;choice=null;currentResult=null;animation=0;particles=[];updateChallengeLabel();$('subtitle').textContent=challenge.cargo?'Lumi também precisa levar a mochila.':'Desenhe uma ideia. Veja acontecer.';feedback(window.LumiVoice?.active()?'':messages[command]);sound(660,.08);
  return {...voiceRiver(),status:'applied',command,message:messages[command]};
 }
};
resetDrawing();resize();requestAnimationFrame(tick);
if(configured())pollChallenge(++waitToken,0,false);
