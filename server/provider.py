"""OpenAI REST adapter. Official contracts reviewed 2026-10-07; live access not yet verified."""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

class ProviderError(Exception):
    pass

class OpenAI:
    def __init__(self, api_key=None):
        self.key=api_key or os.environ.get('OPENAI_API_KEY','')
    def call(self,path,payload=None,method=None):
        if not self.key: raise ProviderError('missing_api_key')
        req=urllib.request.Request('https://api.openai.com/v1'+path,
            data=None if payload is None else json.dumps(payload).encode(),
            headers={'Authorization':'Bearer '+self.key,'Content-Type':'application/json','OpenAI-Beta':'agents=v1'},method=method or ('POST' if payload is not None else 'GET'))
        try:
            with urllib.request.urlopen(req,timeout=12) as response:
                raw=response.read(2_000_001)
                if len(raw)>2_000_000: raise ProviderError('oversize_provider_response')
                return json.loads(raw)
        except urllib.error.HTTPError as e:
            # Never log secrets, upstream error bodies, or request prompts.
            raise ProviderError('provider_http_'+str(e.code)) from None
        except (urllib.error.URLError,TimeoutError,ValueError):
            raise ProviderError('provider_connection_failed') from None
    def decision(self,summary):
        data=self.call('/decisions',{'model':'gpt-6-luna','input':json.dumps(summary,separators=(',',':')),'questions':[{
            'type':'choice','name':'pace','instructions':'Choose a gentle gameplay pace for a young child using ONLY these aggregate game metrics. Favor gentle after collisions or few collected stars. Never optimize engagement duration. Bright only after at least 4 stars and zero hits.',
            'choices':[{'value':'gentle','description':'Fewer and slower asteroids; forgiving pacing.'},{'value':'steady','description':'Maintain the current moderate challenge.'},{'value':'bright','description':'A small increase, at most 15 percent, for a successful pilot.'}]}]})
        for answer in data.get('answers',[]):
            if answer.get('name')=='pace' and answer.get('type')=='choice' and answer.get('choice') in ('gentle','steady','bright'):
                return {'choice':answer['choice'],'source':'openai_decisions','provider_id':data.get('id')}
        raise ProviderError('invalid_or_refused_decision')
    def create_session(self,public_url,vault_id):
        host=urllib.parse.urlparse(public_url).hostname
        return self.call('/agents/sessions',{
            'agent':{'model':os.environ.get('LUMI_AGENT_MODEL','gpt-6-astra'),
                'instructions':'You are a mission director for a children\'s vector arcade game. Use shell/Python to GET the approved catalog at the HTTPS URL in your input. Authenticate with Authorization: Bearer and the unchanged LUMI_CATALOG_TOKEN environment placeholder. Do not print the token. Do not access other hosts. Pick one mission from the returned catalog, and validate that its id is garden, comet, or rings. Respond with exactly one JSON object: {"mission_id":"chosen_id"}. No markdown, no other output, no code generation for the client. Do not delegate. If the catalog cannot be retrieved, report failure rather than inventing a mission.'},
            'environment':{'type':'openai_hosted','container_size':'small','network':{'access':'restricted','allowed_domains':[host]}},
            'vault_ids':[vault_id],'metadata':{'app':'lumi-orbit','contract':'1'},
            'input':'Read the approved catalog at '+public_url.rstrip('/')+'/catalog. Choose one joyful mission with a goal of eight stars.','stream':False})
    def fetch_mission(self,session_id):
        path='/agents/sessions/'+urllib.parse.quote(session_id,safe='')
        session=self.call(path)
        if session.get('status') in ('failed','requires_action'):raise ProviderError('agent_session_failed_or_requires_action')
        turns=self.call(path+'/turns?order=desc&limit=20').get('data',[])
        roots=[t for t in turns if t.get('subagent_id') is None]
        if not roots:return None
        turn=roots[0]
        if turn.get('status') in ('failed','cancelled'):raise ProviderError('agent_turn_failed')
        if turn.get('status')!='completed':return None
        after=None
        for _ in range(10):
            suffix='?order=asc&limit=100&turn_id='+urllib.parse.quote(turn['id'],safe='')
            if after:suffix+='&after='+urllib.parse.quote(after,safe='')
            page=self.call(path+'/items'+suffix)
            for item in page.get('data',[]):
                if item.get('type')!='message' or item.get('role')!='assistant':continue
                if item.get('phase') not in (None,'final_answer'):continue
                for content in item.get('content',[]):
                    if content.get('type')!='output_text':continue
                    try:value=json.loads(content.get('text',''))
                    except (TypeError,ValueError):continue
                    if isinstance(value,dict) and set(value)=={'mission_id'} and value['mission_id'] in ('garden','comet','rings'):
                        return value['mission_id']
            if not page.get('has_more'):break
            after=page.get('last_id') or page.get('data',[{}])[-1].get('id')
            if not after:break
        raise ProviderError('invalid_mission_output')
    def delete_session(self,session_id):
        return self.call('/agents/sessions/'+urllib.parse.quote(session_id,safe=''),method='DELETE')
