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
 for(const width of [320,360,393,540,1280]){
  await page.setViewportSize({width,height:830});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No horizontal overflow at '+width);
 }
 await page.setViewportSize({width:393,height:830});
 check(await page.locator('#swapButton').evaluate(b=>b.getBoundingClientRect().height>=44),'Swap touch target');
 const p=await browser.newPage({viewport:{width:393,height:830}});p.on('pageerror',e=>errors.push(e.message));
 await p.addInitScript(()=>{
  window.calls=[];window.opened=[];
  window.fixture={configured:true,model:'gpt-6-astra',decision_model:'gpt-6-luna',decisions_status:'Disponível · chamada real',vault_status:'Vinculado · sem credenciais externas',vault_id:'vault_fixture',phase:'idle',running:false,offers:[]};
  window.Android={isConfigured:()=>true,openOpenAISetup:()=>{},openUrl:u=>window.opened.push(u),request:(id,operation,raw)=>{
   const body=JSON.parse(raw);window.calls.push({operation,body});
   if(operation==='search')window.fixture={...window.fixture,phase:'browsing',running:true,offers:[],session_id:'ses_fixture',environment_id:'env_fixture',started_at:Date.now(),trip:body.followup?window.fixture.trip:body,route:'São Paulo → Lisboa',activities:[{title:'Conferindo datas'}],approvals:[]};
   if(operation==='approve')window.fixture={...window.fixture,phase:'browsing',approvals:[]};
   if(operation==='cancel')window.fixture={...window.fixture,phase:'cancelled',running:false,error:'Busca interrompida.',completed_at:Date.now()};
   if(operation==='close')window.fixture={configured:true,phase:'idle',running:false,offers:[]};
   setTimeout(()=>window.DecisionNativeResult(id,JSON.stringify(window.fixture)),0);
  }};
 });
 await p.goto(url);await p.waitForFunction(()=>document.querySelector('#connectionStatus').textContent==='Conexão testada');
 await p.locator('#searchButton').click();await p.waitForFunction(()=>document.querySelector('#progressArea').hidden===false);
 check(await p.locator('#workView').isVisible(),'Search displays work');await p.locator('#homeButton').click();await p.locator('#searchButton').click();check(await p.locator('#workView').isVisible(),'Return to running search without resubmission');
 check((await p.evaluate(()=>window.calls.filter(c=>c.operation==='search'))).length===1,'Single search submission');
 await p.evaluate(()=>{window.fixture={...window.fixture,phase:'permission',approvals:[{request_id:'req_fixture',origin:'https://www.google.com',reason:'Pesquisar viagem',allowed:true}]};window.DecisionState(JSON.stringify(window.fixture));});
 check(await p.locator('#approvalDialog').isVisible(),'Origin approval displayed');await p.locator('#approveButton').click();await p.waitForFunction(()=>!document.querySelector('#approvalDialog').open);
 check(await p.evaluate(()=>window.calls.some(c=>c.operation==='approve'&&c.body.request_id==='req_fixture')),'Approval targets request');
 await p.evaluate(()=>{
  const offer=(id,price,duration,stops)=>({id,airline:id==='f1'?'Companhia fixture':'<img src=x onerror=alert(1)>',price,currency:'BRL',price_scope:'trip_per_person',duration_minutes:duration,stops,departure:'GRU 22:00',arrival:'LIS 11:40 +1',baggage:'Não informado',source_url:'https://www.google.com/travel/flights/search?tfs=fixture',details:'Tarifa fictícia para teste da interface.'});
  window.fixture={...window.fixture,running:false,phase:'complete',summary:'Dados de teste da interface.',completed_at:Date.now(),checked_at:Date.now(),ranking:{status:'ok',offer_id:'f2'},offers:[offer('f1',5000,580,0),offer('f2',4500,700,1)]};window.DecisionState(JSON.stringify(window.fixture));
 });
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
 check(errors.length===0,'No JavaScript runtime errors: '+errors.join('; '));
 console.log(JSON.stringify({status:'PASS',checks,viewports:[320,360,393,540,1280],liveAPI:false,notes:'Native bridge fixtures only; screenshots with fixtures are not real fares.'}));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
