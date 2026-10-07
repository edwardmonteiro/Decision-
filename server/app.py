"""Personal/family pilot backend. Run behind an HTTPS reverse proxy. No third-party dependencies."""
import concurrent.futures
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlparse
from provider import OpenAI,ProviderError

ROOT=Path(__file__).resolve().parent
CATALOG=json.loads((ROOT/'catalog.json').read_text())
CATALOG_BY_ID={m['id']:m for m in CATALOG['missions']}
TOKEN=os.environ.get('LUMI_DEVICE_TOKEN','')
CATALOG_TOKEN=os.environ.get('LUMI_CATALOG_TOKEN','')
PUBLIC_URL=os.environ.get('LUMI_PUBLIC_URL','').rstrip('/')
VAULT_ID=os.environ.get('LUMI_VAULT_ID','')
DB=Path(os.environ.get('LUMI_DB',str(ROOT/'runtime'/'lumi.sqlite')))
DB.parent.mkdir(parents=True,exist_ok=True)
provider=OpenAI()
POOL=concurrent.futures.ThreadPoolExecutor(max_workers=3)

class RequestError(Exception):
    def __init__(self,status,code):self.status=status;self.code=code

def db():
    con=sqlite3.connect(DB,timeout=10);con.row_factory=sqlite3.Row
    return con

def init_db():
    with db() as c:
        c.executescript('''PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS runs(id TEXT PRIMARY KEY,owner TEXT NOT NULL,created REAL NOT NULL,status TEXT NOT NULL,session_id TEXT,mission TEXT,closed INTEGER NOT NULL DEFAULT 0,error TEXT);
        CREATE TABLE IF NOT EXISTS decisions(run_id TEXT,wave INTEGER,result TEXT,PRIMARY KEY(run_id,wave));
        CREATE TABLE IF NOT EXISTS budget(day TEXT PRIMARY KEY,runs INTEGER DEFAULT 0,decisions INTEGER DEFAULT 0);
        CREATE TABLE IF NOT EXISTS audit(at REAL,run_id TEXT,event TEXT,code TEXT);
        ''')

def ready():
    u=urlparse(PUBLIC_URL)
    return bool(provider.key and len(TOKEN)>=24 and len(CATALOG_TOKEN)>=24 and TOKEN!=CATALOG_TOKEN and u.scheme=='https' and u.hostname and u.port in (None,443,8443) and not u.username and not u.query and not u.fragment and VAULT_ID)

def audit(run_id,event,code=''):
    with db() as c:c.execute('INSERT INTO audit VALUES(?,?,?,?)',(time.time(),run_id,event,code))

def reserve(c,kind):
    day=time.strftime('%Y-%m-%d',time.gmtime())
    c.execute('INSERT OR IGNORE INTO budget(day) VALUES(?)',(day,))
    limit=int(os.environ.get('LUMI_MAX_DAILY_RUNS','12') if kind=='runs' else os.environ.get('LUMI_MAX_DAILY_DECISIONS','24'))
    cur=c.execute(f'UPDATE budget SET {kind}={kind}+1 WHERE day=? AND {kind}<?',(day,limit))
    if cur.rowcount!=1:raise RequestError(429,'daily_budget_reached')

def cleanup_session(run_id,sid):
    if not sid:return
    try:
        provider.delete_session(sid)
        with db() as c:c.execute('UPDATE runs SET session_id=NULL WHERE id=? AND session_id=?',(run_id,sid))
        audit(run_id,'session_deleted')
    except ProviderError as e:audit(run_id,'cleanup_pending',str(e))

def plan(run_id):
    sid=None
    try:
        created=provider.create_session(PUBLIC_URL,VAULT_ID);sid=created.get('id')
        if not isinstance(sid,str) or not sid:raise ProviderError('missing_session_id')
        with db() as c:c.execute('UPDATE runs SET session_id=? WHERE id=?',(sid,run_id))
        audit(run_id,'session_created')
        deadline=time.monotonic()+85
        while time.monotonic()<deadline:
            with db() as c:row=c.execute('SELECT closed FROM runs WHERE id=?',(run_id,)).fetchone()
            if not row or row['closed']:return
            mission=provider.fetch_mission(sid)
            if mission:
                with db() as c:c.execute("UPDATE runs SET status='ready',mission=? WHERE id=? AND closed=0",(mission,run_id))
                audit(run_id,'mission_validated',mission);return
            time.sleep(2)
        raise ProviderError('mission_timeout')
    except ProviderError as e:
        with db() as c:c.execute("UPDATE runs SET status='failed',error=? WHERE id=?",(str(e),run_id))
        audit(run_id,'mission_failed',str(e))
    except Exception:
        with db() as c:c.execute("UPDATE runs SET status='failed',error='invalid_provider_response' WHERE id=?",(run_id,))
        audit(run_id,'mission_failed','invalid_provider_response')
    finally:cleanup_session(run_id,sid)

def sanitize_summary(body):
    if not isinstance(body,dict):raise RequestError(400,'invalid_summary')
    bounds={'wave':(1,2),'stars':(0,30),'hits':(0,3),'shots':(0,500),'cleared':(0,150),'elapsed':(0,60)}
    if set(body)!=set(bounds):raise RequestError(400,'invalid_summary')
    for k,(lo,hi) in bounds.items():
        if type(body[k]) is not int or not lo<=body[k]<=hi:raise RequestError(400,'invalid_summary')
    return {k:body[k] for k in bounds}

class Handler(BaseHTTPRequestHandler):
    server_version='Lumi/1'
    def log_message(self,*args):pass
    def setup(self):super().setup();self.connection.settimeout(20)
    def reply(self,status,body):
        data=json.dumps(body,ensure_ascii=False,separators=(',',':')).encode()
        self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers()
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError):pass
    def body(self):
        try:n=int(self.headers.get('Content-Length','0'))
        except ValueError:raise RequestError(400,'invalid_length')
        if n<0 or n>8192:raise RequestError(413,'body_too_large')
        try:return json.loads(self.rfile.read(n)) if n else {}
        except (ValueError,UnicodeError):raise RequestError(400,'invalid_json')
    def authenticate(self,catalog=False):
        expected=CATALOG_TOKEN if catalog else TOKEN
        if len(expected)<24 or not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+expected):raise RequestError(401,'unauthorized')
        return hashlib.sha256(expected.encode()).hexdigest()
    def handle_api(self):
        path=urlparse(self.path).path
        if path=='/catalog' and self.command=='GET':self.authenticate(True);return self.reply(200,CATALOG)
        owner=self.authenticate()
        if path=='/health' and self.command=='GET':return self.reply(200,{'ready':ready(),'version':'0.1.0','mode':'openai' if ready() else 'unconfigured'})
        if path=='/v1/runs' and self.command=='POST':
            if self.body()!={}:raise RequestError(400,'unexpected_fields')
            if not ready():raise RequestError(503,'openai_not_configured')
            rid=str(uuid.uuid4())
            with db() as c:
                c.execute('BEGIN IMMEDIATE')
                live=c.execute('SELECT count(*) FROM runs WHERE closed=0 AND owner=? AND created>?',(owner,time.time()-120)).fetchone()[0]
                if live>=2:raise RequestError(429,'too_many_active_runs')
                reserve(c,'runs');c.execute('INSERT INTO runs(id,owner,created,status) VALUES(?,?,?,?)',(rid,owner,time.time(),'planning'))
            audit(rid,'run_created');POOL.submit(plan,rid);return self.reply(201,{'id':rid,'status':'planning'})
        match=re.fullmatch(r'/v1/runs/([a-f0-9-]{36})(/decision)?',path)
        if not match:raise RequestError(404,'not_found')
        rid,sub=match.groups()
        with db() as c:row=c.execute('SELECT * FROM runs WHERE id=? AND owner=?',(rid,owner)).fetchone()
        if not row:raise RequestError(404,'not_found')
        if self.command=='DELETE' and not sub:
            with db() as c:c.execute('UPDATE runs SET closed=1 WHERE id=?',(rid,))
            if row['session_id']:POOL.submit(cleanup_session,rid,row['session_id'])
            return self.reply(200,{'closed':True})
        if row['closed'] or time.time()-row['created']>120:raise RequestError(410,'run_expired')
        if self.command=='GET' and not sub:return self.reply(200,{'id':rid,'status':row['status'],'mission':CATALOG_BY_ID.get(row['mission'])})
        if self.command=='POST' and sub:
            summary=sanitize_summary(self.body());wave=summary['wave']
            with db() as c:
                c.execute('BEGIN IMMEDIATE')
                cached=c.execute('SELECT result FROM decisions WHERE run_id=? AND wave=?',(rid,wave)).fetchone()
                if cached:
                    if cached['result']:return self.reply(200,json.loads(cached['result']))
                    raise RequestError(409,'decision_in_progress_or_failed')
                reserve(c,'decisions');c.execute('INSERT INTO decisions VALUES(?,?,NULL)',(rid,wave))
            try:value=provider.decision(summary)
            except ProviderError as e:audit(rid,'decision_failed',str(e));raise RequestError(503,'decision_unavailable')
            # Bound and sanitize even successfully classified results.
            if value['choice']=='bright' and (summary['hits']>0 or summary['stars']<4):value['choice']='steady'
            with db() as c:c.execute('UPDATE decisions SET result=? WHERE run_id=? AND wave=?',(json.dumps(value),rid,wave))
            audit(rid,'decision',value['choice']);return self.reply(200,value)
        raise RequestError(405,'method_not_allowed')
    def dispatch(self):
        try:self.handle_api()
        except RequestError as e:self.reply(e.status,{'error':e.code})
        except Exception:self.reply(500,{'error':'internal_error'})
    do_GET=dispatch
    do_POST=dispatch
    do_DELETE=dispatch

def janitor():
    while True:
        try:
            with db() as c:
                c.execute('UPDATE runs SET closed=1 WHERE created<?',(time.time()-120,))
                rows=c.execute('SELECT id,session_id FROM runs WHERE closed=1 AND session_id IS NOT NULL').fetchall()
            for row in rows:cleanup_session(row['id'],row['session_id'])
            with db() as c:
                c.execute('DELETE FROM decisions WHERE run_id IN (SELECT id FROM runs WHERE created<? AND session_id IS NULL)',(time.time()-86400,))
                c.execute('DELETE FROM runs WHERE created<? AND session_id IS NULL',(time.time()-86400,))
                c.execute('DELETE FROM audit WHERE at<?',(time.time()-7*86400,))
        except Exception:pass
        time.sleep(30)

if __name__=='__main__':
    init_db()
    if len(TOKEN)<24:raise SystemExit('A private LUMI_DEVICE_TOKEN (24+ characters) is required.')
    threading.Thread(target=janitor,daemon=True).start()
    # Bind to loopback by default. Container deployment must put TLS/auth rate limiting in front.
    server=ThreadingHTTPServer((os.environ.get('HOST','127.0.0.1'),int(os.environ.get('PORT','8080'))),Handler)
    print('LUMI backend listening; provider configured:',ready(),flush=True)
    server.serve_forever()
