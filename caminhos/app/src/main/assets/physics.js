(function(root){
  'use strict';
  const W=360,H=500,R=10,DT=1/120;
  const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
  function segments(paths){const out=[];for(const path of paths)for(let i=1;i<path.length;i++){const a=path[i-1],b=path[i];if(Math.hypot(b[0]-a[0],b[1]-a[1])>.1)out.push([a[0],a[1],b[0],b[1]]);}return out;}
  function nearest(x,y,s){const dx=s[2]-s[0],dy=s[3]-s[1],t=clamp(((x-s[0])*dx+(y-s[1])*dy)/(dx*dx+dy*dy||1),0,1);return [s[0]+t*dx,s[1]+t*dy];}
  function create(level,paths){return {level,ball:{x:level.start[0],y:level.start[1],vx:0,vy:0,angle:0},rails:segments([...(level.rails||[]),...paths]),time:0,status:'running',collected:[false,false],springCooldown:0,events:[]};}
  function contact(b,s){const p=nearest(b.x,b.y,s);let dx=b.x-p[0],dy=b.y-p[1],d=Math.hypot(dx,dy),radius=R+1.5;if(d>=radius)return;
    if(d<.001){const len=Math.hypot(s[2]-s[0],s[3]-s[1])||1;dx=(s[3]-s[1])/len;dy=-(s[2]-s[0])/len;if(dy>0){dx=-dx;dy=-dy;}d=1;}
    const nx=dx/d,ny=dy/d,penetration=radius-Math.hypot(b.x-p[0],b.y-p[1]);b.x+=nx*penetration;b.y+=ny*penetration;
    const normal=b.vx*nx+b.vy*ny;if(normal<0){b.vx-=normal*nx*1.025;b.vy-=normal*ny*1.025;const tangent=b.vx*(-ny)+b.vy*nx;b.vx+=ny*tangent*.0015;b.vy-=nx*tangent*.0015;}
  }
  function step(s){
    if(s.status!=='running')return s;
    const b=s.ball,l=s.level;s.events=[];s.time+=DT;s.springCooldown=Math.max(0,s.springCooldown-DT);
    // Two collision substeps keep travel below the ball radius at the speed cap.
    for(let sub=0;sub<2;sub++){
      const dt=DT/2;let ax=0;
      for(const f of l.fans||[])if(b.x>f.x&&b.x<f.x+f.w&&b.y>f.y&&b.y<f.y+f.h)ax+=f.force;
      b.vx=clamp((b.vx+ax*dt)*.9997,-360,360);b.vy=clamp(b.vy+470*dt,-460,460);
      const oldY=b.y;b.x+=b.vx*dt;b.y+=b.vy*dt;b.angle+=b.vx*dt/R;
      for(let iter=0;iter<3;iter++)for(const rail of s.rails)contact(b,rail);
      for(const pad of l.springs||[])if(!s.springCooldown&&b.vy>0&&b.x>pad.x-R&&b.x<pad.x+pad.w+R&&oldY+R<=pad.y+4&&b.y+R>=pad.y){b.y=pad.y-R;b.vy=-pad.power;b.vx+=pad.push||0;s.springCooldown=.22;s.events.push('spring');}
      for(let i=0;i<(l.stars||[]).length;i++)if(!s.collected[i]&&Math.hypot(b.x-l.stars[i][0],b.y-l.stars[i][1])<R+13){s.collected[i]=true;s.events.push('star');}
      if(Math.hypot(b.x-l.goal[0],b.y-l.goal[1])<23){s.status='won';s.events.push('win');break;}
      if(b.y>H+R*3||b.x<-R*3||b.x>W+R*3||b.y<-180){s.status='lost';break;}
    }
    if(s.time>20&&s.status==='running')s.status='lost';
    return s;
  }
  function simulate(level,paths){const s=create(level,paths);const trail=[];while(s.status==='running'){step(s);if(Math.round(s.time/DT)%6===0)trail.push([s.ball.x,s.ball.y]);}return {state:s,trail};}
  const api={W,H,R,DT,clamp,nearest,segments,create,step,simulate};
  if(typeof module!=='undefined')module.exports=api;else root.PathPhysics=api;
})(typeof window!=='undefined'?window:globalThis);
