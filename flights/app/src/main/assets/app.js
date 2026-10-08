'use strict';
const $=id=>document.getElementById(id);
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=name=>`<svg aria-hidden="true"><use href="#${name}"/></svg>`;
const native=()=>typeof window.Android!=='undefined';
let state={configured:false,running:false,phase:'idle',offers:[]},view='home',sort='best',initialized=false,submitting=false,paused=false,polling=false,pollTimer=0,sequence=0,approvalId='',toastTimer=0,lastHistory=0;
const pending=new Map();
function storageGet(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback;}catch{return fallback;}}
function storageSet(key,value){try{localStorage.setItem(key,JSON.stringify(value));}catch{}}
function showToast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,7000);}
function api(operation,body={}){
  if(!native())return Promise.reject(new Error('Instale o APK para conectar a OpenAI. Esta tela é uma prévia da interface.'));
  return new Promise((resolve,reject)=>{const id=String(++sequence);const timer=setTimeout(()=>{pending.delete(id);reject(new Error('A conexão demorou. Consulte a sessão antes de repetir.'));},120000);pending.set(id,{resolve,reject,timer});window.Android.request(id,operation,JSON.stringify(body));});
}
window.DecisionNativeResult=(id,raw)=>{
  const p=pending.get(id);if(!p)return;pending.delete(id);clearTimeout(p.timer);
  try{const out=JSON.parse(raw);if(out.state)applyState(out.state);if(out.error&&!out.phase)p.reject(new Error(out.error));else{applyState(out);p.resolve(out);}}catch(e){p.reject(e);}
};
window.DecisionState=raw=>{try{applyState(JSON.parse(raw));}catch{}};
window.DecisionConnection=message=>{showToast(message);if(native())api('state').catch(()=>{});};
function setView(next){view=next;$('homeView').hidden=next!=='home';$('workView').hidden=next!=='work';window.scrollTo({top:0,behavior:'instant'});}
function dateString(date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;}
function friendlyDate(s){if(!s)return 'Sem volta';const d=new Date(s+'T12:00:00');return Number.isNaN(d.getTime())?'Escolher':d.toLocaleDateString('pt-BR',{day:'numeric',month:'short'}).replace('.','').replace(' de ',' ');}
function labels(){
  $('departureLabel').textContent=friendlyDate($('departure').value);$('returningLabel').textContent=$('tripType').value==='oneway'?'Sem volta':friendlyDate($('returning').value);
  $('returning').disabled=$('tripType').value==='oneway';$('returning').parentElement.classList.toggle('disabled',$('returning').disabled);
  const n=Number($('passengers').value);$('travelersLabel').textContent=`${n} ${n===1?'adulto':'adultos'} · ${$('cabin').selectedOptions[0].textContent}`;
}
function trip(){return {from:$('from').value.trim(),to:$('to').value.trim(),departure:$('departure').value,returning:$('tripType').value==='oneway'?'':$('returning').value,passengers:Number($('passengers').value),cabin:$('cabin').value,preferences:$('preferences').value.trim()};}
function fillTrip(t){if(!t)return;for(const f of ['from','to','departure','returning','passengers','cabin','preferences'])if(t[f]!==undefined)$(f).value=t[f];$('tripType').value=t.returning?'roundtrip':'oneway';labels();}
function renderHistory(){
  const history=storageGet('decision-history',[]);if(!history.length)return;
  $('historyList').innerHTML=history.map((h,i)=>`<button class="history-item" data-history="${i}"><strong>${esc(h.route)}</strong><span>${esc(friendlyDate(h.trip.departure))} · ${esc(h.trip.passengers)} ${h.trip.passengers===1?'adulto':'adultos'} · Consultar novamente</span></button>`).join('');
}
function recordHistory(s){
  if(s.phase!=='complete'||!s.trip||!s.checked_at||s.checked_at===lastHistory)return;
  lastHistory=s.checked_at;let history=storageGet('decision-history',[]).filter(h=>h.route!==s.route||h.trip.departure!==s.trip.departure);
  history.unshift({route:s.route,trip:s.trip,time:s.checked_at});storageSet('decision-history',history.slice(0,5));renderHistory();
}
function settings(){
  $('connectionStatus').textContent=state.configured?(state.decisions_status?.includes('real')?'Conexão testada':'Chave salva'):'Não conectada';
  $('decisionsStatus').textContent=state.decisions_status||'Não testado';$('vaultStatus').textContent=state.vault_status||'Não conectado';$('environmentStatus').textContent=state.environment_status==='connected'?'Navegador conectado':state.environment_status||'Criado na busca';
  $('connectButton').textContent=state.configured?'Trocar chave OpenAI':'Conectar OpenAI';$('testButton').hidden=!state.configured;$('disconnectButton').hidden=!state.configured;$('closeSessionButton').hidden=!state.session_id;
  $('resourceDetails').innerHTML=[['Session',state.session_id],['Environment',state.environment_id],['Vault',state.vault_id],['Modelo do agente',state.configured?state.model:''],['Modelo Decisions',state.configured?state.decision_model:''],['Request Decisions',state.decision_request_id],['Tokens da sessão',state.usage?.total_tokens],['Decisions · comparação',state.ranking?.latency_ms!==undefined?`${state.ranking.latency_ms} ms`:'']].filter(p=>p[1]!==undefined&&p[1]!=='').map(p=>`<dt>${esc(p[0])}</dt><dd>${esc(p[1])}</dd>`).join('');
}
function price(o){try{return new Intl.NumberFormat('pt-BR',{style:'currency',currency:o.currency,maximumFractionDigits:o.price%1===0?0:2}).format(o.price);}catch{return `${o.currency} ${o.price}`;}}
function scope(o){return {trip_per_person:'viagem completa · por pessoa',trip_all_passengers:'viagem completa · total',one_way_per_person:'somente ida · por pessoa'}[o.price_scope]||'';}
function duration(m){return `${Math.floor(m/60)}h${m%60?String(m%60).padStart(2,'0'):''}`;}
function orderedOffers(){
  const offers=[...(state.offers||[])];if(sort==='duration')offers.sort((a,b)=>a.duration_minutes-b.duration_minutes);
  else if(sort==='price')offers.sort((a,b)=>`${a.currency}:${a.price_scope}`.localeCompare(`${b.currency}:${b.price_scope}`)||a.price-b.price);
  else if(state.ranking?.status==='ok')offers.sort((a,b)=>(b.id===state.ranking.offer_id)-(a.id===state.ranking.offer_id));
  return offers;
}
function renderOffers(){
  const offers=orderedOffers();$('offerList').innerHTML=offers.map(o=>{
    const best=state.ranking?.status==='ok'&&o.id===state.ranking.offer_id;
    return `<article class="offer"><div class="offer-top"><div>${best?'<div class="best-label">ESCOLHA DECISIONS</div>':''}<div class="airline">${esc(o.airline)}</div></div><div class="offer-price">${esc(price(o))}<span class="price-scope">${esc(scope(o))}</span></div></div><p class="offer-route">${esc(o.departure)} → ${esc(o.arrival)}</p><div class="offer-meta"><span>${esc(duration(o.duration_minutes))} na ida</span><span>${o.stops===0?'Sem escalas':`${esc(o.stops)} ${o.stops===1?'escala':'escalas'}`}</span></div><p class="offer-baggage">Bagagem: ${esc(o.baggage)}</p><details><summary>Detalhes da tarifa</summary><p>${esc(o.details)}</p></details><button class="offer-source" data-offer="${esc(o.id)}"><span>Ver oferta no Google Flights</span>${icon('arrow')}</button></article>`;
  }).join('');
  const bases=new Set(offers.map(o=>`${o.currency}:${o.price_scope}`));$('fareNote').textContent=bases.size>1?'Há moedas ou bases de preço diferentes. A ordem agrupa tarifas comparáveis. Confirme preço e bagagem no site.':'Tarifas observadas no site. Confirme preço e bagagem antes de comprar.';
  document.querySelectorAll('[data-sort]').forEach(b=>b.classList.toggle('selected',b.dataset.sort===sort));
}
function elapsed(){const end=state.running?Date.now():(state.completed_at||Date.now());return Math.max(0,Math.round((end-(state.started_at||end))/1000));}
function applyState(s){
  state={configured:false,running:false,phase:'idle',offers:[],...s};if(!initialized){initialized=true;if(state.session_id){setView('work');fillTrip(state.trip);}}
  settings();$('searchButton').disabled=submitting;$('searchButton').querySelector('span').textContent=state.running?'Ver busca em andamento':'Buscar passagens';
  $('workRoute').textContent=state.route||`${$('from').value} → ${$('to').value}`;
  const running=state.running||submitting;$('progressArea').hidden=!running;$('resultsArea').hidden=running||state.phase!=='complete'||!(state.offers||[]).length;
  $('retryButton').hidden=!state.pending_input;
  const messages=!running&&(state.phase==='error'||state.phase==='cancelled'||state.phase==='complete'&&!(state.offers||[]).length);$('messageArea').hidden=!messages;
  const phase={starting:'Preparando navegador',browsing:'Pesquisando passagens',permission:'Aguardando sua permissão',checking:'Validando as ofertas',recovering:'Recuperando a sessão'};
  $('progressLabel').textContent=phase[state.phase]||'Pesquisando passagens';$('timer').textContent=`${elapsed()} s`;$('activityLabel').textContent=state.activity||'A sessão está sendo preparada na OpenAI.';
  if(state.screenshot){if($('browserImage').src!==state.screenshot)$('browserImage').src=state.screenshot;$('browserImage').hidden=false;$('browserEmpty').hidden=true;}else{$('browserImage').hidden=true;$('browserEmpty').hidden=false;}
  $('resultSummary').textContent=state.summary||'Ofertas observadas no Google Flights.';
  $('resultsTime').textContent=state.checked_at?`Consultado ${new Date(state.checked_at).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'})} · ${elapsed()} s de busca`:'';
  if(!running&&state.phase==='complete')renderOffers();
  if(messages){$('messageTitle').textContent=state.phase==='cancelled'?'Busca interrompida':state.phase==='error'?'A busca precisa de atenção':state.result_status==='blocked'?'O site limitou o acesso':state.result_status==='needs_input'?'Falta um detalhe da viagem':'Nenhuma oferta confirmada';$('messageText').textContent=state.error||state.summary||'Consulte a sessão ou ajuste sua pesquisa.';}
  $('refinementForm').hidden=running||!state.session_id;$('refinementInput').disabled=running;
  $('activityList').innerHTML=(state.activities||[]).map(a=>`<li>${esc(a.title||'Navegação no Google Flights')}</li>`).join('');
  const approval=(state.approvals||[])[0];
  if(approval){approvalId=approval.request_id;$('approvalOrigin').textContent=approval.origin;$('approvalReason').textContent=approval.allowed?(approval.reason&&approval.reason!=='null'?approval.reason:'Esse acesso permite pesquisar sua viagem.'):'Este domínio está fora da busca pública no Google Flights.';$('approveButton').disabled=!approval.allowed;if(!$('approvalDialog').open)$('approvalDialog').showModal();}
  else{approvalId='';if($('approvalDialog').open)$('approvalDialog').close();}
  recordHistory(state);schedulePoll();
}
function schedulePoll(){clearTimeout(pollTimer);if(!paused&&native()&&state.running&&!polling&&!submitting)pollTimer=setTimeout(poll,state.phase==='checking'?250:state.streaming?4000:1600);}
async function poll(){if(polling||paused||!native()||!state.session_id)return;polling=true;try{await api('poll');}catch(e){showToast(e.message);}finally{polling=false;schedulePoll();}}
async function search(e){
  e.preventDefault();if(submitting)return;if(state.running){setView('work');return;}if(!native()||!state.configured){$('settingsDialog').showModal();showToast(native()?'Conecte sua chave OpenAI para pesquisar.':'A conexão está disponível no APK.');return;}
  const t=trip();if(!t.from||!t.to)return;if(t.returning&&t.returning<t.departure){showToast('A volta deve ser no dia da ida ou depois.');return;}
  submitting=true;state={...state,phase:'starting',running:false,route:`${t.from} → ${t.to}`,started_at:Date.now(),screenshot:'',offers:[],activities:[],activity:''};setView('work');applyState(state);
  try{await api('search',t);}catch(e){showToast(e.message);if(!state.running){state.phase='error';state.error=e.message;}}
  finally{submitting=false;applyState(state);schedulePoll();}
}
async function refine(e){e.preventDefault();if(state.running||submitting)return;const text=$('refinementInput').value.trim();if(!text)return;submitting=true;state.phase='starting';state.started_at=Date.now();applyState(state);try{await api('search',{followup:true,text});$('refinementInput').value='';}catch(e){showToast(e.message);}finally{submitting=false;applyState(state);}}
function openLink(url){if(native())window.Android.openUrl(url);else showToast('Abra o APK para acessar a oferta.');}
function closeDialogs(){document.querySelectorAll('dialog[open]').forEach(d=>d.close());}
window.DecisionPause=()=>{paused=true;clearTimeout(pollTimer);};window.DecisionResume=()=>{paused=false;if(native())api('state').then(()=>state.running&&poll()).catch(()=>{});};
window.DecisionBack=()=>{if($('approvalDialog').open){showToast('Permita ou recuse o acesso ao site.');return;}if(document.querySelector('dialog[open]'))closeDialogs();else if(view==='work')setView('home');else $('settingsDialog').showModal();};
$('searchForm').addEventListener('submit',search);$('refinementForm').addEventListener('submit',refine);
$('settingsButton').addEventListener('click',()=>$('settingsDialog').showModal());$('inspectButton').addEventListener('click',()=>$('settingsDialog').showModal());
$('homeButton').addEventListener('click',()=>setView('home'));$('backButton').addEventListener('click',()=>setView('home'));
$('swapButton').addEventListener('click',()=>{const value=$('from').value;$('from').value=$('to').value;$('to').value=value;});
$('travelersButton').addEventListener('click',()=>$('travelersDialog').showModal());$('travelersDone').addEventListener('click',()=>{labels();$('travelersDialog').close();});
for(const id of ['departure','returning','passengers','cabin','tripType'])$(id).addEventListener('change',labels);
document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>$(b.dataset.close).close()));
document.querySelectorAll('[data-sort]').forEach(b=>b.addEventListener('click',()=>{sort=b.dataset.sort;renderOffers();}));
$('offerList').addEventListener('click',e=>{const b=e.target.closest('[data-offer]');if(b){const offer=(state.offers||[]).find(o=>o.id===b.dataset.offer);if(offer)openLink(offer.source_url);}});
$('historyList').addEventListener('click',e=>{const b=e.target.closest('[data-history]');if(b){fillTrip(storageGet('decision-history',[])[Number(b.dataset.history)]?.trip);showToast('Viagem preenchida. Busque para consultar tarifas atuais.');}});
$('connectButton').addEventListener('click',()=>{if(native()){window.Android.openOpenAISetup();$('settingsDialog').close();}else showToast('A chave é conectada pelo diálogo seguro do APK.');});
$('testButton').addEventListener('click',async()=>{const b=$('testButton');b.disabled=true;try{await api('test');showToast('Decisions e Vault testados na sua conta.');}catch(e){showToast(e.message);}finally{b.disabled=false;}});
$('cancelButton').addEventListener('click',async()=>{try{await api('cancel');}catch(e){showToast(e.message);}});
$('retryButton').addEventListener('click',async()=>{const b=$('retryButton');b.disabled=true;try{await api('retry');}catch(e){showToast(e.message);}finally{b.disabled=false;}});
$('refreshButton').addEventListener('click',()=>poll());
$('closeSessionButton').addEventListener('click',async()=>{try{await api('close');closeDialogs();setView('home');showToast('Sessão encerrada.');}catch(e){showToast(e.message);}});
$('disconnectButton').addEventListener('click',async()=>{try{await api('disconnect');state={configured:false,running:false,phase:'idle',offers:[]};applyState(state);closeDialogs();setView('home');showToast('Conexão removida deste celular.');}catch(e){showToast(e.message);}});
$('platformButton').addEventListener('click',()=>openLink('https://platform.openai.com/agents'));
async function approval(decision){if(!approvalId)return;const b=$('approveButton');b.disabled=true;try{await api('approve',{request_id:approvalId,decision});}catch(e){showToast(e.message);await poll();}finally{b.disabled=!(state.approvals||[])[0]?.allowed;}}
$('approveButton').addEventListener('click',()=>approval('approve'));$('denyButton').addEventListener('click',()=>approval('deny'));
$('approvalDialog').addEventListener('cancel',e=>{e.preventDefault();showToast('Permita ou recuse o acesso ao site.');});
const today=new Date();today.setHours(12,0,0,0);$('departure').min=dateString(today);$('returning').min=dateString(today);const departing=new Date(today);departing.setDate(today.getDate()+33);const returning=new Date(departing);returning.setDate(departing.getDate()+10);$('departure').value=dateString(departing);$('returning').value=dateString(returning);labels();renderHistory();
setInterval(()=>{if(!paused&&state.running)$('timer').textContent=`${elapsed()} s`;},1000);
if(native()){state.configured=window.Android.isConfigured();api('state').catch(e=>showToast(e.message));}else settings();
