'use strict';
// GPT-Live media stays on WebRTC. The Android bridge only exchanges the SDP;
// the long-lived API key never enters this script or the data channel.
(()=>{
 const $=id=>document.getElementById(id),game=window.LumiVoiceGame;
 const mic='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><rect x="9" y="2" width="6" height="13" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/></svg>';
 $('voice-icon').innerHTML=mic;
 let current=null,serial=0,contextTimer;
 const active=()=>current!==null;
 function paint(status,caption){$('voice-status').textContent=status;if(caption!==undefined)$('voice-caption').textContent=caption;$('voice-bar').classList.remove('hidden')}
 function send(s,event){if(current!==s||!s.ready||s.closing||s.events?.readyState!=='open')return false;s.events.send(JSON.stringify(event));return true}
 function cleanup(s){
  if(current!==s)return;current=null;clearTimeout(contextTimer);for(const t of [s.deadline,s.startTimeout,s.closeTimeout,s.disconnectTimer])clearTimeout(t);
  s.microphone?.getTracks().forEach(t=>t.stop());s.audio.pause();s.audio.srcObject=null;s.audio.remove();s.events?.close();s.peer?.close();
  $('voice-toggle').classList.remove('on');$('voice-toggle').setAttribute('aria-pressed','false');$('voice-label').textContent='Falar';$('voice-play').classList.add('hidden');$('voice-end').disabled=false;
  s.responses.clear();s.seen.clear();s.input='';s.output='';
 }
 function stop(message='Voz desligada.',fromNative=false){
  const s=current;if(!s)return;
  if(s.closing)return;s.closing=true;clearTimeout(s.startTimeout);clearTimeout(s.deadline);
  // Stop capture/playback immediately. Keep only the channel for final usage.
  s.microphone?.getTracks().forEach(t=>{t.enabled=false;t.stop()});s.audio.pause();s.audio.srcObject=null;
  $('voice-end').disabled=true;paint('Microfone desligado',message||'Conversa encerrada.');
  if(s.ready&&s.events?.readyState==='open')try{s.events.send(JSON.stringify({type:'session.close'}))}catch{}
  if(!fromNative)window.Android?.stopVoice?.();
  s.closeTimeout=setTimeout(()=>{if(current!==s)return;cleanup(s);paint('Voz desligada',message||'Conversa encerrada.');},1500);
 }
 function fail(s,message){if(current!==s)return;stop(message);game.notice(message)}
 function tool(s,item){
  if(!item||item.type!=='function_call'||typeof item.call_id!=='string'||item.call_id.length>200)return null;
  if(s.seen.has(item.call_id))return null;
  s.seen.add(item.call_id);
  if(++s.calls>24)return {call_id:item.call_id,result:{status:'limit',message:'Limite de comandos desta conversa. Desligue a voz.'}};
  let result;
  try{
   if(typeof item.arguments!=='string'||item.arguments.length>512)throw Error();
   const args=JSON.parse(item.arguments);
   if(item.name==='end_voice'&&args&&typeof args==='object'&&!Array.isArray(args)&&Object.keys(args).length===0){result={status:'applied',message:'Conversa encerrada.'};setTimeout(()=>{if(current===s)stop()},100)}
   else result=game.apply(item.name,args);
  }catch{result={status:'invalid',message:'Comando inválido. O rio não mudou.'}}
  if(result.status==='applied'&&item.name==='change_river')paint('Lumi mudou o rio',result.message);
  return {call_id:item.call_id,result};
 }
 function backend(s,envelope){
  const e=envelope.event;if(!e||typeof e.type!=='string')return;
  const key=envelope.delegation_id||'default';
  if(e.type==='response.created'){
   if(s.responses.size>32){fail(s,'A conversa ficou longa. Ligue a voz novamente.');return}
   s.responses.set(key,{id:e.response?.id,calls:[],finished:false});return;
  }
  const r=s.responses.get(key);if(!r||r.finished)return;
  if(e.type==='response.output_item.done'&&e.item?.type==='function_call'){
   if(r.calls.length<8)r.calls.push(e.item);return;
  }
  if(e.type==='response.failed'||e.type==='response.incomplete'||e.type==='response.cancelled'){r.finished=true;paint('Não consegui mudar o rio','Pode pedir de novo. Seu desenho continua aqui.');return}
  if(e.type!=='response.completed')return;
  if(e.response?.id&&r.id&&e.response.id!==r.id)return;
  r.finished=true;
  // No action from partial arguments: only completed, successful responses.
  if(e.response?.status&&e.response.status!=='completed')return;
  const results=r.calls.map(item=>tool(s,item)).filter(Boolean);
  for(const item of results)send(s,{type:'response.item.create',item:{type:'function_call_output',call_id:item.call_id,output:JSON.stringify(item.result)}});
  if(results.length)send(s,{type:'response.create'});
 }
 function event(s,data){
  if(current!==s||typeof data!=='string'||data.length>200000)return;
  let e;try{e=JSON.parse(data)}catch{return}
  if(e.type==='session.closed'){
   s.finalized=true;window.Android?.voiceClosed?.(s.id||e.session?.id||'');const message=e.reason==='content'?'Vamos continuar desenhando.':'Toque em Falar para uma nova conversa.';
   cleanup(s);paint('Voz desligada',message);return;
  }
  if(s.closing)return;
  if(e.type==='session.started'){
   s.ready=true;clearTimeout(s.startTimeout);paint('Lumi · voz de IA · ouvindo','Pode falar. Diga: “Fica de noite”.');
   send(s,{type:'session.instructions.append',delegation_id:null,content:'Cumprimente agora em português brasileiro: Sou a voz de IA da Lumi. Como vamos mudar este rio? Depois espere a pessoa falar.'});contextChanged();return;
  }
  if(e.type==='session.input_transcript.delta'||e.type==='session.output_transcript.delta'){
   if(typeof e.delta!=='string')return;const input=e.type==='session.input_transcript.delta',k=input?'input':'output',last=k+'End';
   if(typeof e.start_ms==='number'&&e.start_ms-(s[last]||0)>1800)s[k]='';
   s[k]=(s[k]+e.delta).slice(-240);s[last]=e.end_ms||0;
   paint(input?'Você · microfone ligado':'Lumi · voz de IA',s[k]);return;
  }
  if(e.type==='response.event'){backend(s,e);return}
  if(e.type==='error'){
   const code=e.error?.code||'';
   const message=/rate|quota|limit/.test(code)?'Limite de voz na OpenAI. Continue desenhando.':/permission|model|auth/.test(code)?'Sua conta não liberou esta conexão de voz. Confira a chave e o acesso.':'A OpenAI interrompeu a voz. Toque em Falar para tentar novamente.';
   fail(s,message);
  }
 }
 async function gather(peer){if(peer.iceGatheringState==='complete')return;await new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>{peer.removeEventListener('icegatheringstatechange',check);reject(Error('Não foi possível preparar o áudio nesta rede.'))},8000);
  function check(){if(peer.iceGatheringState==='complete'){clearTimeout(timer);peer.removeEventListener('icegatheringstatechange',check);resolve()}}
  peer.addEventListener('icegatheringstatechange',check);check();
 })}
 async function start(){
  if(current)return;
  if(!game.configured()){game.notice('Conecte a OpenAI na área dos responsáveis para conversar.');return}
  if(!window.Android?.prepareVoice){game.notice('A conversa por voz está disponível no APK atualizado.');return}
  if(!navigator.mediaDevices?.getUserMedia||!window.RTCPeerConnection){game.notice('Atualize o Android System WebView para usar a voz.');return}
  if(!game.seenIntro()){game.intro();return}
  const s={number:++serial,ready:false,closing:false,finalized:false,audio:new Audio(),responses:new Map(),seen:new Set(),calls:0,input:'',output:''};current=s;
  s.audio.autoplay=true;s.audio.setAttribute('playsinline','');
  $('voice-toggle').classList.add('on');$('voice-toggle').setAttribute('aria-pressed','true');$('voice-label').textContent='Ligada';paint('Preparando a voz…','Autorize o microfone para conversar.');
  s.startTimeout=setTimeout(()=>fail(s,'A voz demorou para conectar. Tente novamente.'),65000);
  try{
   s.permissionPending=true;await game.request('prepare');s.permissionPending=false;if(current!==s||s.closing)return;if(document.hidden)throw Error('Volte ao jogo e toque em Falar.');
   const microphone=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true},video:false});
   if(current!==s||s.closing){microphone.getTracks().forEach(t=>t.stop());return}s.microphone=microphone;
   s.deadline=setTimeout(()=>stop('Dois minutos de conversa. Toque em Falar para conversar de novo.'),115000);
   paint('Conectando à Lumi…','Microfone ligado · você pode desligar a qualquer momento.');
   const peer=new RTCPeerConnection();s.peer=peer;
   peer.addEventListener('track',e=>{if(current!==s||s.closing)return;s.audio.srcObject=new MediaStream([e.track]);s.audio.play().catch(()=>{if(current===s&&!s.closing)$('voice-play').classList.remove('hidden')})});
   microphone.getAudioTracks().forEach(t=>{peer.addTrack(t,microphone);t.addEventListener('ended',()=>{if(current===s&&!s.closing)fail(s,'O microfone foi desligado.')})});
   s.events=peer.createDataChannel('oai-events');s.events.addEventListener('message',e=>event(s,e.data));
   s.events.addEventListener('close',()=>{if(current===s&&!s.closing&&!s.finalized)fail(s,'A conexão de voz caiu. O jogo continua.')});
   peer.addEventListener('connectionstatechange',()=>{
    if(current!==s||s.closing)return;
    clearTimeout(s.disconnectTimer);
    if(peer.connectionState==='failed')fail(s,'Não foi possível manter o áudio nesta rede.');
    else if(peer.connectionState==='disconnected')s.disconnectTimer=setTimeout(()=>fail(s,'A conexão de voz caiu.'),5000);
   });
   await peer.setLocalDescription(await peer.createOffer());await gather(peer);if(current!==s||s.closing)return;
   const r=await game.request('start',{sdp:peer.localDescription.sdp,challenge:game.read().river});
   if(current!==s||s.closing)return;
   s.id=r.session_id;if(typeof r.sdp!=='string'||!r.sdp.startsWith('v=0'))throw Error('Resposta de voz inválida.');
   await peer.setRemoteDescription({type:'answer',sdp:r.sdp});
   // session.started, not the SDP HTTP success, is our readiness signal.
  }catch(e){if(current!==s||s.closing)return;fail(s,e?.name==='NotAllowedError'?'Microfone não autorizado. Você pode continuar desenhando.':e?.message||'Não foi possível ligar a voz.')}
 }
 function contextChanged(){clearTimeout(contextTimer);const s=current;if(!s?.ready||s.closing)return;contextTimer=setTimeout(()=>send(s,{type:'session.thinking.append',delegation_id:null,content:'Estado verificado do jogo: '+JSON.stringify(game.read())}),150)}
 $('voice-toggle').onclick=()=>current?stop():start();$('voice-end').onclick=()=>stop();
 $('voice-play').onclick=()=>{const s=current;if(s&&!s.closing)s.audio.play().then(()=>$('voice-play').classList.add('hidden')).catch(()=>game.notice('Não foi possível reproduzir. Confira o volume do celular.'))};
 window.LumiVoice={start,stop,active,contextChanged,background:()=>{if(!current?.permissionPending)stop('Voz desligada ao sair do jogo.')}};window.LumiVoiceStop=message=>stop(message,true);
 window.addEventListener('pagehide',()=>stop('Voz desligada.'));
})();
