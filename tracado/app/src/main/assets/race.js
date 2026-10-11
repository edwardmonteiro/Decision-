(function(root){
  'use strict';
  const W=360,H=480,DT=1/120,TAU=Math.PI*2;
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v)),dist=(a,b)=>Math.hypot(a[0]-b[0],a[1]-b[1]);
  const wrap=a=>Math.atan2(Math.sin(a),Math.cos(a));
  const MODES={cruise:{label:'Passeio',speed:92,grip:10,look:26,turn:2.9},race:{label:'Corrida',speed:138,grip:7,look:34,turn:3.2},drift:{label:'Drift',speed:128,grip:2.7,look:40,turn:3.4}};
  function circuit(nodes){const out=[];for(let i=0;i<nodes.length;i++){const a=nodes[(i+nodes.length-1)%nodes.length],b=nodes[i],c=nodes[(i+1)%nodes.length],d=nodes[(i+2)%nodes.length];for(let j=0;j<18;j++){const t=j/18,t2=t*t,t3=t2*t;out.push([0,1].map(k=>.5*((2*b[k])+(-a[k]+c[k])*t+(2*a[k]-5*b[k]+4*c[k]-d[k])*t2+(-a[k]+3*b[k]-3*c[k]+d[k])*t3)));}}return route(out,true);}
  function route(points,closed=true){const p=points.map(a=>a.slice());if(closed&&dist(p[0],p[p.length-1])>.01)p.push(p[0].slice());const segments=[];let length=0;for(let i=1;i<p.length;i++){const a=p[i-1],b=p[i],len=dist(a,b);if(len>.001){segments.push({a,b,len,s:length,angle:Math.atan2(b[1]-a[1],b[0]-a[0])});length+=len;}}return {points:p,segments,length,closed};}
  function at(r,s){if(!r.segments.length)return {x:0,y:0,angle:0};s=r.closed?((s%r.length)+r.length)%r.length:clamp(s,0,r.length);const e=r.segments.find(e=>e.s+e.len>=s)||r.segments[r.segments.length-1],t=clamp((s-e.s)/e.len,0,1);return {x:e.a[0]+(e.b[0]-e.a[0])*t,y:e.a[1]+(e.b[1]-e.a[1])*t,angle:e.angle};}
  function nearest(r,x,y){let best={distance:Infinity,s:0,x:0,y:0,angle:0};for(const e of r.segments){const dx=e.b[0]-e.a[0],dy=e.b[1]-e.a[1],t=clamp(((x-e.a[0])*dx+(y-e.a[1])*dy)/(e.len*e.len),0,1),px=e.a[0]+t*dx,py=e.a[1]+t*dy,d=Math.hypot(x-px,y-py);if(d<best.distance)best={distance:d,s:e.s+t*e.len,x:px,y:py,angle:e.angle};}return best;}
  function delta(a,b,len){let d=a-b;if(d>len/2)d-=len;if(d< -len/2)d+=len;return d;}
  function onRoad(track,p,margin=0){return nearest(track.road,...p).distance<=track.width/2-margin;}
  function segmentOnRoad(track,a,b){const n=Math.max(1,Math.ceil(dist(a,b)/4));for(let i=0;i<=n;i++)if(!onRoad(track,[a[0]+(b[0]-a[0])*i/n,a[1]+(b[1]-a[1])*i/n],2))return false;return true;}
  function resample(points,spacing=4){if(points.length<2)return points;const r=route(points,false),out=[];for(let s=0;s<r.length;s+=spacing){const p=at(r,s);out.push([p.x,p.y]);}out.push(points[points.length-1].slice());return out;}
  function validate(track,points){
    const fail=message=>({ok:false,message});
    if(!Array.isArray(points)||points.length<6)return fail('Comece na bandeira e dê uma volta pela pista.');
    if(points.length>1600||points.some(p=>!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite)))return fail('Apague o traçado e desenhe uma nova volta.');
    const start=track.road.points[0];if(dist(points[0],start)>30)return fail('Comece seu traçado na bandeira.');
    for(let i=1;i<points.length;i++)if(!segmentOnRoad(track,points[i-1],points[i]))return fail('Mantenha a linha dentro da pista.');
    let total=0,last=nearest(track.road,...points[0]).s,maxBack=0,dir=0,gate=0;
    for(let i=1;i<points.length;i++){const n=nearest(track.road,...points[i]),d=delta(n.s,last,track.road.length);last=n.s;total+=d;if(!dir&&Math.abs(total)>25)dir=Math.sign(total);if(dir){maxBack=Math.max(maxBack,-d*dir);if(Math.abs(total)>=track.road.length*(gate+1)/4&&gate<3)gate++;}}
    if(maxBack>35)return fail('Continue em um só sentido até voltar à bandeira.');
    if(Math.abs(total)<track.road.length*.9||gate<3)return fail('Continue seu traçado até dar a volta completa.');
    if(Math.abs(total)>track.road.length*1.15)return fail('Basta uma volta. Termine na bandeira.');
    if(dist(points[points.length-1],start)>34)return fail('Falta pouco! Feche o traçado na bandeira.');
    if(!segmentOnRoad(track,points[points.length-1],start)||!segmentOnRoad(track,start,points[0]))return fail('Ligue as pontas dentro da pista.');
    const fixed=[start.slice(),...points,start.slice()];let sampled=resample(fixed,4);
    // A tiny symmetric smoothing removes finger jitter without changing road boundaries.
    const smooth=sampled.map((p,i)=>i>0&&i<sampled.length-1?[(sampled[i-1][0]+p[0]*2+sampled[i+1][0])/4,(sampled[i-1][1]+p[1]*2+sampled[i+1][1])/4]:p);
    if(smooth.every((p,i)=>!i||segmentOnRoad(track,smooth[i-1],p)))sampled=smooth;
    return {ok:true,message:'Traçado pronto. Vamos dar uma volta!',route:route(sampled,true),direction:dir||1};
  }
  function create(track,validated,mode){const m=MODES[mode]||MODES.cruise,p=at(validated.route,0),aim=at(validated.route,20);return {track,route:validated.route,direction:validated.direction,mode:MODES[mode]?mode:'cruise',config:m,car:{x:p.x,y:p.y,angle:Math.atan2(aim.y-p.y,aim.x-p.x),vx:0,vy:0,slip:0},time:0,status:'running',progress:0,lastRoad:0,gates:0,driftTime:0,offroadTime:0,maxSlip:0,events:[]};}
  function step(s){
    if(s.status!=='running')return s;const c=s.car,m=s.config,dt=DT;s.time+=dt;s.events=[];
    const near=nearest(s.route,c.x,c.y),speed=Math.hypot(c.vx,c.vy),look=m.look+speed*.1,target=at(s.route,near.s+look),ahead=at(s.route,near.s+look+25),here=at(s.route,near.s+3);
    const curvature=Math.abs(wrap(ahead.angle-here.angle)),desired=Math.atan2(target.y-c.y,target.x-c.x),error=wrap(desired-c.angle);
    const road=nearest(s.track.road,c.x,c.y),off=road.distance>s.track.width/2-5;
    let targetSpeed=m.speed/(1+curvature*.58);if(near.distance>20)targetSpeed*=.75;if(off)targetSpeed*=.48;
    // The steering turns the body; lateral velocity relaxes separately, so drift is physical slip.
    c.angle=wrap(c.angle+clamp(error*4,-m.turn,m.turn)*dt);
    const fx=Math.cos(c.angle),fy=Math.sin(c.angle),forward=c.vx*fx+c.vy*fy,lateral=-c.vx*fy+c.vy*fx;
    const f=forward+(targetSpeed-forward)*(1-Math.exp(-3.8*dt)),lat=lateral*Math.exp(-(off?5:m.grip)*dt);
    c.vx=fx*f-fy*lat;c.vy=fy*f+fx*lat;c.x+=c.vx*dt;c.y+=c.vy*dt;
    const velocityAngle=Math.atan2(c.vy,c.vx);c.slip=Math.abs(wrap(c.angle-velocityAngle));s.maxSlip=Math.max(s.maxSlip,c.slip);
    const nowRoad=nearest(s.track.road,c.x,c.y),outside=nowRoad.distance>s.track.width/2-5;
    if(outside)s.offroadTime+=dt;
    if(s.mode==='drift'&&!outside&&Math.hypot(c.vx,c.vy)>40&&c.slip>.1){s.driftTime+=dt;s.events.push('drift');}
    const change=delta(nowRoad.s,s.lastRoad,s.track.road.length)*s.direction;s.lastRoad=nowRoad.s;
    if(Math.abs(change)<12)s.progress+=change;
    if(s.gates<3&&s.progress>=s.track.road.length*(s.gates+1)/4&&!outside){s.gates++;s.events.push('gate');}
    if(s.gates===3&&s.progress>=s.track.road.length&&nowRoad.distance<s.track.width/2){s.status='finished';s.events.push('finish');}
    if(s.time>=60||c.x< -60||c.x>W+60||c.y< -60||c.y>H+60){s.status='stuck';s.events.push('stuck');}
    return s;
  }
  function simulate(track,points,mode){const v=validate(track,points);if(!v.ok)return {error:v.message};const s=create(track,v,mode),trail=[];while(s.status==='running'){step(s);if(Math.round(s.time/DT)%12===0)trail.push([s.car.x,s.car.y]);}return {state:s,trail};}
  const api={W,H,DT,MODES,clamp,dist,wrap,circuit,route,at,nearest,delta,onRoad,segmentOnRoad,resample,validate,create,step,simulate};
  if(typeof module!=='undefined')module.exports=api;else root.LumiRace=api;
})(typeof window!=='undefined'?window:globalThis);
