const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const {pathToFileURL}=require('node:url');
const root=path.resolve(process.env.UI_QA_OUTPUT||path.join(__dirname,'../build/ui-qa'));
fs.mkdirSync(root,{recursive:true});
const url=pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/index.html')).href;
let checks=0;
function check(value,message){assert.ok(value,message);checks++;}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE_PATH,headless:true,args:['--no-sandbox']});
 const errors=[];
 const page=await browser.newPage({viewport:{width:393,height:830}});
 page.on('pageerror',e=>errors.push(e.message));
 await page.goto(url);await page.screenshot({path:root+'/home.png',fullPage:true});
 check(await page.title()==='Decision Flights','Page title');
 await page.locator('#swapButton').click();check(await page.locator('#from').inputValue()==='Lisboa'&&await page.locator('#to').inputValue()==='São Paulo','Swap origin and destination');
 await page.locator('#travelersButton').click();await page.locator('#passengers').selectOption('2');await page.locator('#cabin').selectOption('business');await page.locator('#tripType').selectOption('oneway');await page.locator('#travelersDone').click();
 check((await page.locator('#travelersLabel').textContent()).includes('2 adultos · Executiva'),'Traveler controls');check(await page.locator('#returning').isDisabled(),'One-way disables return date');
 await page.locator('#searchButton').click();check(await page.locator('#settingsDialog').isVisible(),'Missing connection opens setup');
 check(await page.locator('.offer').count()===0,'Preview ships no fabricated results');await page.locator('[data-close="settingsDialog"]').click();
 await page.locator('[data-mode="advanced"]').click();await page.locator('#advancedFilters summary').click();
 await page.locator('#priority').selectOption('price');await page.locator('#maxPrice').fill('5000');await page.locator('#nonstop').check();
 check(await page.locator('#advancedFilters').isVisible()&&(await page.locator('#modeNote').textContent()).includes('Pode demorar mais'),'Advanced mode exposes filters and time tradeoff');
 check(await page.evaluate(()=>{const t=trip();return t.search_mode==='advanced'&&t.priority==='price'&&t.max_price_brl===5000&&t.nonstop;}),'Advanced filters become a validated native search request');
 await page.screenshot({path:root+'/advanced-form.png',fullPage:true});
 for(const width of [320,360,393,540,1280]){await page.setViewportSize({width,height:830});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Advanced form fits '+width);}
 await page.setViewportSize({width:393,height:830});
 await page.locator('[data-mode="quick"]').click();check(await page.locator('#advancedFilters').isHidden()&&await page.evaluate(()=>trip().max_price_brl===0&&!trip().nonstop),'Quick mode omits advanced filters');
 for(const width of [320,360,393,540,1280]){
  await page.setViewportSize({width,height:830});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No horizontal overflow at '+width);
 }
 await page.setViewportSize({width:393,height:830});
 check(await page.locator('#swapButton').evaluate(b=>b.getBoundingClientRect().height>=44),'Swap touch target');
 const p=await browser.newPage({viewport:{width:393,height:830}});p.on('pageerror',e=>errors.push(e.message));
 await p.addInitScript(()=>{
  window.calls=[];window.opened=[];
  window.peers=[];window.microphones=[];window.liveCounter=0;
  Object.defineProperty(navigator,'mediaDevices',{value:{getUserMedia:async()=>{
   if(window.denyMic)throw new DOMException('permission fixture','NotAllowedError');
   const track={enabled:true,stopped:false,stop(){this.stopped=true;}};window.microphones.push(track);
   return {getTracks:()=>[track],getAudioTracks:()=>[track]};
  }}});
  window.RTCPeerConnection=class extends EventTarget {
   constructor(){super();this.iceGatheringState='complete';this.connectionState='connected';window.peers.push(this);}
   addTrack(){} async createOffer(){return {type:'offer',sdp:'v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n'};}
   async setLocalDescription(offer){this.localDescription=offer;}
   async setRemoteDescription(){this.emit({type:'session.started',session:{id:window.lastLiveID}});}
   createDataChannel(label){this.label=label;const channel=new EventTarget();channel.readyState='open';channel.sent=[];
    channel.send=raw=>{const event=JSON.parse(raw);channel.sent.push(event);if(event.type==='session.close')setTimeout(()=>this.emit({type:'session.closed',usage:{seconds:12.5}}),20);};
    channel.close=()=>{channel.readyState='closed';channel.dispatchEvent(new Event('close'));};this.channel=channel;return channel;
   }
   emit(event){this.channel.dispatchEvent(new MessageEvent('message',{data:JSON.stringify(event)}));}
   close(){this.connectionState='closed';}
  };
  window.fixture={configured:true,model:'gpt-6-astra',decision_model:'gpt-6-luna',decisions_status:'Disponível · chamada real',vault_status:'Vinculado · sem credenciais externas',vault_id:'vault_fixture',phase:'idle',running:false,offers:[]};
  window.Android={isConfigured:()=>true,openOpenAISetup:()=>{},openUrl:u=>window.opened.push(u),request:(id,operation,raw)=>{
   const body=JSON.parse(raw);window.calls.push({operation,body});
   if(operation==='live_start'){window.lastLiveID='live_fixture_'+(++window.liveCounter);const answer={live:{session:{id:window.lastLiveID},transport:{type:'webrtc',sdp:'v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n'}}};setTimeout(()=>window.DecisionNativeResult(id,JSON.stringify(answer)),window.delayLive||0);return;}
   if(operation==='live_finish'||operation==='live_close'){setTimeout(()=>window.DecisionNativeResult(id,JSON.stringify({live:{status:'Encerrada'}})),0);return;}
   if(operation==='live_action'){
    if(body.name==='search_flights')window.fixture={...window.fixture,running:true,phase:'browsing',offers:[],session_id:'ses_fixture',environment_id:'env_fixture',trip:body.arguments,route:body.arguments.from+' → '+body.arguments.to,started_at:Date.now(),activities:[],approvals:[]};
    if(body.name==='refine_search')window.fixture={...window.fixture,running:true,phase:'browsing'};
    if(body.name==='cancel_search')window.fixture={...window.fixture,running:false,phase:'cancelled'};
    const answer={live:{phase:window.fixture.phase,running:window.fixture.running,summary:window.fixture.summary||'',offers:[]},state:window.fixture};setTimeout(()=>window.DecisionNativeResult(id,JSON.stringify(answer)),30);return;
   }
   if(operation==='search')window.fixture={...window.fixture,phase:'browsing',running:true,offers:[],session_id:'ses_fixture',environment_id:'env_fixture',started_at:Date.now(),trip:body.followup?window.fixture.trip:body,route:'São Paulo → Lisboa',activities:[{title:'Conferindo datas'}],approvals:[]};
   if(operation==='approve')window.fixture={...window.fixture,phase:'browsing',approvals:[]};
   if(operation==='cancel')window.fixture={...window.fixture,phase:'cancelled',running:false,error:'Busca interrompida.',completed_at:Date.now()};
   if(operation==='close')window.fixture={configured:true,phase:'idle',running:false,offers:[]};
   if(operation==='poll'&&window.pollError){const answer={error:window.pollError,state:window.fixture};setTimeout(()=>window.DecisionNativeResult(id,JSON.stringify(answer)),window.pollDelay||0);return;}
   if(operation==='poll'&&window.recoverPoll)window.fixture={...window.fixture,poll_blocked:false,poll_warning:'',phase:'browsing',last_poll_at:Date.now(),last_activity_at:Date.now(),progress:'Navegador conectado. O agente está pesquisando sua viagem.'};
   setTimeout(()=>window.DecisionNativeResult(id,JSON.stringify(window.fixture)),operation==='poll'?window.pollDelay||0:0);
  }};
 });
 await p.goto(url);await p.waitForFunction(()=>document.querySelector('#connectionStatus').textContent==='Conexão testada');
 await p.locator('#searchButton').click();await p.waitForFunction(()=>document.querySelector('#progressArea').hidden===false);
 check(await p.locator('#workView').isVisible(),'Search displays work');await p.locator('#homeButton').click();await p.locator('#searchButton').click();check(await p.locator('#workView').isVisible(),'Return to running search without resubmission');
 check((await p.evaluate(()=>window.calls.filter(c=>c.operation==='search'))).length===1,'Single search submission');
 await p.evaluate(()=>{window.fixture={...window.fixture,phase:'starting',environment_status:'pending',activity:'',progress:'A OpenAI está preparando o navegador hospedado.',started_at:Date.now()-64000,last_activity_at:0,last_poll_at:Date.now(),poll_blocked:true};window.DecisionState(JSON.stringify(window.fixture));});
 check((await p.locator('#activityLabel').textContent()).includes('preparando'),'Preparation caption uses confirmed environment state');
 check(await p.locator('#progressWarning').isVisible()&&(await p.locator('#progressWarning').textContent()).includes('sem nova atividade'),'64 seconds without activity shows persistent guidance');
 check((await p.locator('#lastUpdateLabel').textContent()).includes('Sessão consultada'),'Confirmed consultation time is visible');
 await p.evaluate(()=>{window.fixture={...window.fixture,phase:'recovering',poll_warning:'A OpenAI rejeitou a consulta (400).',progress:'Não foi possível confirmar o andamento da busca.'};window.pollError='A OpenAI rejeitou a consulta (400).';window.DecisionState(JSON.stringify(window.fixture));});
 check((await p.locator('#progressLabel').textContent())==='Acompanhamento interrompido'&&await p.locator('#progressDot.paused').count()===1,'Permanent polling failure stops animated progress');
 const beforeBlocked=await p.evaluate(()=>window.calls.filter(c=>c.operation==='poll').length);
 await p.waitForTimeout(1800);check(await p.evaluate(()=>window.calls.filter(c=>c.operation==='poll').length)===beforeBlocked,'Permanent failure stops automatic polling');
 await p.locator('#checkProgressButton').click();await p.waitForFunction(()=>document.querySelector('#toast').textContent.includes('(400)'));
 check((await p.locator('#progressWarning').textContent()).includes('(400)'),'Polling error persists after callback rejects');
 await p.evaluate(()=>{window.pollError='';window.recoverPoll=true;window.pollDelay=220;});
 await p.locator('#checkProgressButton').click();check(await p.locator('#checkProgressButton').isDisabled(),'Prevent simultaneous manual consultations');
 await p.waitForFunction(()=>!document.querySelector('#checkProgressButton').disabled);
 check(await p.locator('#progressWarning').isHidden()&&(await p.locator('#activityLabel').textContent()).includes('conectado'),'Same-session consultation clears recovered error');
 check((await p.evaluate(()=>window.calls.filter(c=>c.operation==='search'))).length===1,'Manual consultation never resubmits the trip');
 await p.evaluate(()=>{window.recoverPoll=false;window.pollDelay=0;window.fixture={...window.fixture,phase:'recovering',poll_blocked:true,poll_warning:'Sem atualização confirmada. Confira a conexão.',progress:'Não foi possível confirmar o andamento da busca.'};window.DecisionState(JSON.stringify(window.fixture));});
 await p.evaluate(()=>document.querySelector('#toast').hidden=true);await p.screenshot({path:root+'/connection-recovery-fixture.png',fullPage:true});
 for(const width of [320,360,393,540,1280]){await p.setViewportSize({width,height:830});check(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Recovery controls fit '+width);}
 await p.setViewportSize({width:393,height:830});await p.evaluate(()=>{window.fixture={...window.fixture,poll_blocked:false,poll_warning:'',last_activity_at:Date.now()};});
 await p.evaluate(()=>{window.fixture={...window.fixture,phase:'permission',approvals:[{request_id:'req_fixture',origin:'https://www.google.com',reason:'Pesquisar viagem',allowed:true}]};window.DecisionState(JSON.stringify(window.fixture));});
 check(await p.locator('#approvalDialog').isVisible(),'Origin approval displayed');await p.locator('#approveButton').click();await p.waitForFunction(()=>!document.querySelector('#approvalDialog').open);
 check(await p.evaluate(()=>window.calls.some(c=>c.operation==='approve'&&c.body.request_id==='req_fixture')),'Approval targets request');
 await p.evaluate(()=>{
  const offer=(id,price,duration,stops)=>({id,airline:id==='f1'?'Companhia fixture':'<img src=x onerror=alert(1)>',price,currency:'BRL',price_scope:'trip_per_person',duration_minutes:duration,stops,departure:'GRU 22:00',arrival:'LIS 11:40 +1',baggage:'Não informado',source_url:'https://www.google.com/travel/flights/search?tfs=fixture',details:'Tarifa fictícia para teste da interface.'});
  window.fixture={...window.fixture,running:false,phase:'complete',summary:'Dados de teste da interface.',completed_at:Date.now(),checked_at:Date.now(),ranking:{status:'ok',offer_id:'f2'},offers:[offer('f1',5000,580,0),offer('f2',4500,700,1)]};window.DecisionState(JSON.stringify(window.fixture));
 });
 await p.evaluate(()=>{window.fixture={...window.fixture,search_mode:'advanced',sandbox_reused:true,environment_status:'connected',environment_ready_at:window.fixture.started_at+1500,browser_operations:4,first_browser_at:window.fixture.started_at+2200,web_operations:1,first_web_at:window.fixture.started_at+3000,ranking:{status:'comparing'}};window.DecisionState(JSON.stringify(window.fixture));});
 check(await p.locator('#resultsArea').isVisible()&&await p.locator('.offer').count()===2&&await p.locator('.best-label').count()===0,'Verified fares are visible during Decisions comparison, without a fabricated best label');
 check((await p.locator('#serviceStages').textContent()).includes('Comparando as tarifas')&&(await p.locator('#serviceStages').textContent()).includes('4 operações observadas')&&(await p.locator('#serviceStages').textContent()).includes('Navegador reaproveitado'),'Visible service panel explains sandbox, actual browser calls and Decisions');
 await p.screenshot({path:root+'/services-comparing-fixture.png',fullPage:true});
 await p.evaluate(()=>{window.fixture={...window.fixture,ranking:{status:'ok',offer_id:'f2',confidence:.92,latency_ms:450}};window.DecisionState(JSON.stringify(window.fixture));});
 check((await p.locator('#serviceStages').textContent()).includes('92% de confiança')&&(await p.locator('#serviceStages').textContent()).includes('0,5 s'),'Actual Decisions confidence and measured latency render');
 await p.evaluate(()=>{window.fixture={...window.fixture,web_operations:0,first_web_at:0};window.DecisionState(JSON.stringify(window.fixture));});
 check((await p.locator('#serviceStages').textContent()).includes('Disponível · não utilizado'),'Enabled Web Search is not falsely reported as used');
 await p.locator('#servicesButton').click();check(await p.locator('#resourceDetails').textContent().then(t=>t.includes('Session')&&t.includes('Environment')&&t.includes('Vault')),'Resource details are reachable from the visible service panel');await p.locator('[data-close="settingsDialog"]').click();
 check(await p.locator('.offer').count()===2&&await p.locator('.best-label').count()===1,'Offers and actual ranking selection render');
 check(await p.locator('.offer img').count()===0,'Model text cannot insert markup');
 await p.locator('[data-sort="price"]').click();check((await p.locator('.offer').first().textContent()).includes('4.500'),'Comparable price order');
 await p.locator('[data-sort="duration"]').click();check((await p.locator('.offer').first().textContent()).includes('9h40'),'Duration order');
 await p.locator('.offer-source').first().click();check((await p.evaluate(()=>window.opened))[0].startsWith('https://www.google.com/travel/flights'),'Offer opens observed source');
 await p.screenshot({path:root+'/fixture-results.png',fullPage:true});
 for(const width of [320,360,393,540,1280]){await p.setViewportSize({width,height:830});check(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Results fit at '+width);}
 await p.setViewportSize({width:393,height:830});await p.locator('#refinementInput').fill('Tente Campinas');await p.locator('#refinementForm button').click();
 await p.waitForFunction(()=>document.querySelector('#progressArea').hidden===false);check(await p.evaluate(()=>window.calls.some(c=>c.operation==='search'&&c.body.followup===true&&c.body.text==='Tente Campinas')),'Same-session refinement request');
 await p.locator('#cancelButton').click();await p.waitForFunction(()=>document.querySelector('#messageArea').hidden===false);check((await p.locator('#messageTitle').textContent())==='Busca interrompida','Cancellation state');
 await p.locator('.activity-log summary').click();await p.locator('#inspectButton').click();await p.locator('#closeSessionButton').click();await p.waitForFunction(()=>document.querySelector('#homeView').hidden===false);
 check(await p.locator('#refinementForm').isHidden(),'Close clears stale session');
 await p.locator('#voiceButton').click();check(await p.locator('#voiceDialog').isVisible(),'Voice entry opens Brazilian conversation');
 check(await p.locator('#voiceChoice').inputValue()==='bossa','Brazilian Bossa default');
 await p.evaluate(()=>window.denyMic=true);await p.locator('#voiceStart').click();await p.waitForFunction(()=>document.querySelector('#voiceStatus').textContent.includes('Permita'));
 check(await p.evaluate(()=>!window.calls.some(c=>c.operation==='live_start')),'Permission denial creates no billed voice session');
 await p.evaluate(()=>window.denyMic=false);await p.locator('#voiceStart').click();await p.waitForFunction(()=>!document.querySelector('#voiceControls').hidden);
 check(await p.locator('#voiceStart').isHidden()&&await p.locator('#voiceChoice').isDisabled(),'Connected voice locks startup voice');
 check(await p.evaluate(()=>window.peers.at(-1).label==='oai-events'&&window.calls.find(c=>c.operation==='live_start').body.voice==='bossa'),'SDP and Brazilian voice sent through native bridge');
 check(await p.evaluate(()=>!window.peers.at(-1).channel.sent.some(e=>e.type==='session.start')),'WebRTC never sends a second session.start');
 await p.evaluate(()=>{const peer=window.peers.at(-1);peer.emit({type:'session.input_transcript.delta',delta:'Quero ir ',start_ms:100,end_ms:400});peer.emit({type:'session.input_transcript.delta',delta:'para Lisboa.',start_ms:400,end_ms:700});peer.emit({type:'session.output_transcript.delta',delta:'Qual é a data? <img src=x>',start_ms:800,end_ms:1200});});
 check((await p.locator('[data-speaker="user"]').textContent()).includes('Quero ir para Lisboa.'),'Transcript preserves fragment spacing');
 check(await p.locator('#voiceTranscript img').count()===0,'Voice captions cannot insert HTML');
 await p.locator('#voiceMute').click();check(await p.evaluate(()=>!window.microphones.at(-1).enabled&&window.peers.at(-1).channel.sent.at(-1).type==='session.input_audio.mute'),'Mute disables actual microphone track');
 await p.locator('#voiceMute').click();check(await p.evaluate(()=>window.microphones.at(-1).enabled),'Unmute enables capture');
 for(const width of [320,393,1280]){await p.setViewportSize({width,height:830});check(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Voice sheet fits '+width);}
 await p.setViewportSize({width:393,height:830});await p.screenshot({path:root+'/voice-fixture.png',fullPage:true});
 await p.evaluate(()=>{
  const peer=window.peers.at(-1),delegation_id='delegation_fixture',t=window.calls.find(c=>c.operation==='live_start').body.trip;
  const call={type:'response.event',delegation_id,event:{type:'response.output_item.done',item:{type:'function_call',call_id:'call_voice_search',name:'search_flights',arguments:JSON.stringify(t)}}};
  peer.emit({type:'response.event',delegation_id,event:{type:'response.created',response:{id:'resp_fixture'}}});peer.emit(call);peer.emit(call);
  peer.emit({type:'response.event',delegation_id,event:{type:'response.completed',response:{id:'resp_fixture',output:[]}}});
 });
 await p.waitForFunction(()=>window.peers.at(-1).channel.sent.some(e=>e.type==='response.create'));
 check(await p.evaluate(()=>window.calls.filter(c=>c.operation==='live_action'&&c.body.call_id==='call_voice_search').length===1),'Duplicate tool event launches one native flight action');
 check(await p.evaluate(()=>{const sent=window.peers.at(-1).channel.sent;return sent.filter(e=>e.type==='response.create').length===1&&sent.findIndex(e=>e.type==='response.item.create')<sent.findIndex(e=>e.type==='response.create');}),'Responses resumes once, after completed tool output');
 check(await p.locator('#workView').isVisible(),'Voice search shows hosted search progress');
 await p.locator('#voiceStop').click();await p.waitForFunction(()=>!document.querySelector('#voiceStart').hidden);
 check(await p.evaluate(()=>window.microphones.at(-1).stopped&&window.peers.at(-1).connectionState==='closed'),'Graceful close releases capture and WebRTC');
 check(await p.evaluate(()=>window.calls.some(c=>c.operation==='live_finish'&&c.body.seconds===12.5)),'Final usage uses terminal session event');
 await p.locator('#voiceChoice').selectOption('tempo');await p.locator('#voiceStart').click();await p.waitForFunction(()=>!document.querySelector('#voiceControls').hidden);
 check(await p.evaluate(()=>window.calls.filter(c=>c.operation==='live_start').at(-1).body.voice==='tempo'),'Tempo used only in new conversation');
 await p.evaluate(()=>window.DecisionPause());check(await p.evaluate(()=>window.microphones.at(-1).stopped),'Background stops microphone immediately');
 await p.waitForFunction(()=>window.calls.some(c=>c.operation==='live_close'&&c.body.session_id==='live_fixture_2'));
 check(await p.locator('#voiceStart').isVisible(),'Background closure restores start control');
 await p.evaluate(()=>{window.DecisionResume();window.delayLive=120;});await p.locator('#voiceStart').click();await p.waitForFunction(()=>window.calls.filter(c=>c.operation==='live_start').length===3);
 await p.locator('[data-close="voiceDialog"]').click();await p.waitForFunction(()=>window.calls.some(c=>c.operation==='live_close'&&c.body.session_id==='live_fixture_3'));
 check(await p.evaluate(()=>window.microphones.every(t=>t.stopped)),'Closing during handshake leaks no microphone');
 check(await p.locator('#voiceControls').isHidden(),'Stale handshake cannot reopen voice controls');
 check(errors.length===0,'No JavaScript runtime errors: '+errors.join('; '));
 console.log(JSON.stringify({status:'PASS',checks,viewports:[320,360,393,540,1280],liveAPI:false,notes:'Native bridge fixtures only; screenshots with fixtures are not real fares.'}));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
