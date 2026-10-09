'use strict';
// Real GPT Live media. Audio and transcripts stay in this conversation; no local recordings.
let conversation=null,voiceEpoch=0;
function voiceStatus(text){$('voiceStatus').textContent=text;}
function voiceSend(v,event){if(conversation!==v||v.closing||!v.ready||v.channel?.readyState!=='open')return false;v.channel.send(JSON.stringify(event));return true;}
function voiceTranscript(v,event,speaker){
  if(typeof event.delta!=='string'||!event.delta)return;
  let paragraph=v.lastParagraph;
  if(!paragraph||paragraph.dataset.speaker!==speaker||Number(event.start_ms)-v.lastEnd>2500){
    paragraph=document.createElement('p');paragraph.dataset.speaker=speaker;
    const label=document.createElement('small');label.textContent=speaker==='user'?'Você':'Decision';paragraph.append(label,document.createTextNode(''));
    $('voiceTranscript').append(paragraph);v.lastParagraph=paragraph;
    while($('voiceTranscript').children.length>10)$('voiceTranscript').firstElementChild.remove();
  }
  paragraph.lastChild.textContent=(paragraph.lastChild.textContent+event.delta).slice(-3000);v.lastEnd=Number(event.end_ms)||0;
  $('voiceTranscript').scrollTop=$('voiceTranscript').scrollHeight;
}
function releaseVoice(v){
  v.closing=true;
  clearTimeout(v.deadline);clearTimeout(v.closeTimeout);clearTimeout(v.readyTimeout);clearInterval(v.clock);
  v.media?.getTracks().forEach(track=>track.stop());v.channel?.close();v.peer?.close();
  if(conversation!==v)return;conversation=null;voiceEpoch++;
  $('voiceAudio').pause();$('voiceAudio').srcObject=null;$('voiceAudio').hidden=true;
  $('voiceChoice').disabled=false;$('voiceStart').hidden=false;$('voiceStart').disabled=false;
  $('voiceControls').hidden=true;$('voiceMute').disabled=false;$('voiceStop').disabled=false;
  $('voiceMute').textContent='Silenciar microfone';$('voiceMute').setAttribute('aria-pressed','false');
  $('voiceTimer').textContent='';
}
async function endVoice(graceful=true){
  const v=conversation;if(!v||v.closing)return;v.closing=true;voiceEpoch++;
  v.media?.getAudioTracks().forEach(track=>track.enabled=false);$('voiceAudio').pause();
  $('voiceMute').disabled=true;$('voiceStop').disabled=true;voiceStatus('Encerrando conversa…');
  if(graceful&&v.ready&&v.channel?.readyState==='open'){
    v.channel.send(JSON.stringify({type:'session.close'}));
    v.closeTimeout=setTimeout(()=>forceCloseVoice(v),15000);
  }else await forceCloseVoice(v);
}
async function forceCloseVoice(v){
  // Release capture immediately, including backgrounding and failed startup.
  releaseVoice(v);voiceStatus('Conversa encerrada.');
  if(v.creating)try{await api('live_close',{start_token:v.token,...(v.id?{session_id:v.id}:{})});}catch(e){if(!conversation)voiceStatus('Microfone desligado. Encerramento na OpenAI pendente.');showToast(e.message);}
}
function voicePublicState(s){
  return {phase:s.phase,running:!!s.running,route:s.route||'',checked_at:s.checked_at||null,summary:(s.summary||'').slice(0,500),error:(s.error||'').slice(0,250),
    offers:s.phase==='complete'?(s.offers||[]).slice(0,3).map(o=>({airline:o.airline,price:o.price,currency:o.currency,price_scope:o.price_scope,stops:o.stops})):[]};
}
window.DecisionVoiceState=s=>{
  const v=conversation;if(!v?.ready||v.closing)return;
  const summary=voicePublicState(s),fingerprint=JSON.stringify(summary);if(fingerprint===v.lastState)return;v.lastState=fingerprint;
  const content=('Estado verificado do aplicativo, como dados de referência: '+fingerprint).slice(0,1700);
  voiceSend(v,{type:'session.thinking.append',delegation_id:null,content});
  const completedKey=`${s.phase}:${s.checked_at||s.completed_at||''}`;
  if(['complete','error','cancelled'].includes(s.phase)&&completedKey!==v.announced){
    v.announced=completedKey;voiceSend(v,{type:'session.commentary.append',delegation_id:null,
      content:('Informe brevemente este resultado verificado da busca. Não acrescente tarifas nem dados: '+fingerprint).slice(0,1700)});
  }
};
async function continueVoiceResponse(v,response){
  if(!response.finished||response.continued||!response.calls.size)return;
  if([...response.calls.values()].some(c=>!c.done))return;
  response.continued=true;voiceSend(v,{type:'response.create'});
}
async function runVoiceFunction(v,response,item){
  if(response.calls.has(item.call_id))return;
  const call={done:false};response.calls.set(item.call_id,call);
  let output;
  try{
    if(typeof item.call_id!=='string'||!item.call_id.match(/^[A-Za-z0-9_-]{1,200}$/)||typeof item.arguments!=='string'||item.arguments.length>10000)throw new Error('A ação por voz não foi reconhecida.');
    if(!['search_flights','refine_search','get_search_status','cancel_search'].includes(item.name))throw new Error('Essa ação não faz parte da busca.');
    if(item.name==='search_flights'||item.name==='refine_search'){setView('work');voiceStatus('Preparando sua busca…');}
    output=await api('live_action',{session_id:v.id,call_id:item.call_id,name:item.name,arguments:JSON.parse(item.arguments)});
    if(conversation!==v||v.closing)return;
    if(state.trip)fillTrip(state.trip);voiceStatus(v.muted?'Microfone silenciado.':'Conectado. Pode falar.');
  }catch(e){output={status:'error',message:e.message};if(conversation===v&&!v.closing){showToast(e.message);voiceStatus('Confira os detalhes da viagem.');}}
  if(conversation!==v||v.closing)return;
  voiceSend(v,{type:'response.item.create',item:{type:'function_call_output',call_id:item.call_id,output:JSON.stringify(output)}});
  call.done=true;await continueVoiceResponse(v,response);
}
function voiceResponseEvent(v,envelope){
  const event=envelope.event;if(!event||typeof envelope.delegation_id!=='string')return;
  if(event.type==='response.created'){
    v.responses.set(envelope.delegation_id,{id:event.response?.id,calls:new Map(),finished:false,continued:false});return;
  }
  const response=v.responses.get(envelope.delegation_id);if(!response)return;
  if(event.type==='response.output_item.done'&&event.item?.type==='function_call'){
    runVoiceFunction(v,response,event.item).catch(()=>{});
  }else if(event.type==='response.completed'){
    response.finished=true;continueVoiceResponse(v,response).catch(()=>{});
  }else if(event.type==='response.failed'){
    voiceStatus('Não foi possível concluir essa ação.');showToast('A ação por voz falhou. Confira sua viagem antes de repetir.');
  }
}
function voiceEvent(v,event){
  if(conversation!==v)return;
  if(event.type==='session.closed'){
    const id=v.id,seconds=event.usage?.seconds;v.closing=true;releaseVoice(v);voiceStatus('Conversa encerrada.');
    api('live_finish',{session_id:id,seconds:typeof seconds==='number'?seconds:-1}).catch(()=>{});return;
  }
  if(v.closing)return;
  if(event.type==='session.started'){
    if(v.ready)return;
    const id=event.session?.id;if(!id||id!==v.id){showToast('A sessão de voz não foi confirmada.');endVoice(false);return;}
    v.ready=true;clearTimeout(v.readyTimeout);v.started=Date.now();$('voiceStart').hidden=true;$('voiceControls').hidden=false;
    voiceStatus('Conectado. Pode falar.');
    v.clock=setInterval(()=>{if(conversation===v)$('voiceTimer').textContent=`${Math.floor((Date.now()-v.started)/1000)} s`;},1000);
    v.deadline=setTimeout(()=>{showToast('A conversa atingiu 4 minutos. Você pode iniciar outra.');endVoice();},240000);
    voiceSend(v,{type:'session.instructions.append',delegation_id:null,content:'Comece agora, em português brasileiro: apresente-se brevemente como a assistente de voz da Decision, diga que sua voz é gerada por IA, pergunte para onde a pessoa quer viajar e pare para ouvir.'});
    window.DecisionVoiceState(state);
  }else if(event.type==='session.input_transcript.delta')voiceTranscript(v,event,'user');
  else if(event.type==='session.output_transcript.delta')voiceTranscript(v,event,'assistant');
  else if(event.type==='response.event')voiceResponseEvent(v,event);
  else if(event.type==='error'){
    // Provider text is not surfaced here; native HTTP diagnostics handle connection errors.
    showToast('A OpenAI encontrou um erro na conversa. Encerre e tente novamente.');voiceStatus('A conversa precisa de atenção.');
  }
}
function gatherVoiceIce(v){
  if(v.peer.iceGatheringState==='complete')return Promise.resolve();
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{v.peer.removeEventListener('icegatheringstatechange',change);reject(new Error('Não foi possível preparar a conexão de voz.'));},10000);
    function change(){if(v.peer.iceGatheringState==='complete'){clearTimeout(timer);v.peer.removeEventListener('icegatheringstatechange',change);resolve();}}
    v.peer.addEventListener('icegatheringstatechange',change);change();
  });
}
async function startVoice(){
  if(conversation)return;
  if(!native()||!state.configured){showToast('Conecte a OpenAI no APK para conversar por voz.');return;}
  if(paused)return;
  if(!navigator.mediaDevices?.getUserMedia||!window.RTCPeerConnection){voiceStatus('Atualize o Android System WebView para usar voz.');return;}
  const v={epoch:++voiceEpoch,token:`${Date.now()}_${voiceEpoch}`,id:'',ready:false,closing:false,muted:false,responses:new Map()};conversation=v;
  $('voiceStart').disabled=true;$('voiceChoice').disabled=true;$('voiceTranscript').replaceChildren();voiceStatus('Permitindo o microfone…');
  try{
    v.media=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true}});
    if(conversation!==v||v.epoch!==voiceEpoch){v.media.getTracks().forEach(t=>t.stop());return;}
    const peer=new RTCPeerConnection();v.peer=peer;
    peer.addEventListener('track',event=>{
      if(conversation!==v||v.closing)return;
      $('voiceAudio').srcObject=new MediaStream([event.track]);$('voiceAudio').hidden=false;
      $('voiceAudio').play().catch(()=>voiceStatus('Toque em reproduzir para ouvir a Decision.'));
    });
    peer.addEventListener('connectionstatechange',()=>{
      if(conversation===v&&!v.closing&&['failed','closed'].includes(peer.connectionState)){showToast('A conexão de voz foi interrompida.');endVoice(false);}
    });
    v.media.getAudioTracks().forEach(t=>peer.addTrack(t,v.media));
    v.channel=peer.createDataChannel('oai-events');
    v.channel.addEventListener('message',({data})=>{try{if(typeof data==='string'&&data.length<200000)voiceEvent(v,JSON.parse(data));}catch{}});
    v.channel.addEventListener('close',()=>{if(conversation===v&&!v.closing){showToast('A conversa perdeu a conexão.');endVoice(false);}});
    voiceStatus('Conectando o GPT Live 1…');
    await peer.setLocalDescription(await peer.createOffer());await gatherVoiceIce(v);
    if(conversation!==v||v.epoch!==voiceEpoch)return;
    v.creating=true;
    const result=await api('live_start',{start_token:v.token,sdp:peer.localDescription.sdp,voice:$('voiceChoice').value,trip:trip()});
    v.id=result.session.id;
    if(conversation!==v||v.epoch!==voiceEpoch){await api('live_close',{start_token:v.token,session_id:v.id});return;}
    await peer.setRemoteDescription({type:'answer',sdp:result.transport.sdp});
    if(!v.ready)v.readyTimeout=setTimeout(()=>{if(conversation===v&&!v.ready){showToast('A sessão de voz não iniciou. Confira a conexão.');endVoice(false);}},25000);
  }catch(e){
    if(conversation!==v)return;
    await forceCloseVoice(v);
    voiceStatus(e.name==='NotAllowedError'?'Permita o microfone para conversar.':e.name==='NotFoundError'?'Não foi encontrado um microfone.':e.message||'Não foi possível conectar a voz.');
  }
}
function openVoice(){
  if(!native()||!state.configured){$('settingsDialog').showModal();showToast('Conecte a OpenAI para buscar por voz.');return;}
  if(!$('voiceDialog').open)$('voiceDialog').showModal();
}
window.DecisionVoiceEnd=()=>{endVoice(false).catch(()=>{});};
$('voiceButton').addEventListener('click',openVoice);$('workVoiceButton').addEventListener('click',openVoice);
$('voiceStart').addEventListener('click',startVoice);$('voiceStop').addEventListener('click',()=>endVoice());
$('voiceDialog').addEventListener('close',()=>endVoice());
$('voiceMute').addEventListener('click',()=>{
  const v=conversation;if(!v?.ready||v.closing)return;v.muted=!v.muted;
  v.media.getAudioTracks().forEach(track=>track.enabled=!v.muted);
  voiceSend(v,{type:v.muted?'session.input_audio.mute':'session.input_audio.unmute'});
  $('voiceMute').textContent=v.muted?'Ativar microfone':'Silenciar microfone';$('voiceMute').setAttribute('aria-pressed',String(v.muted));
  voiceStatus(v.muted?'Microfone silenciado.':'Conectado. Pode falar.');
});
