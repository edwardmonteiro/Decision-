"""One-time trusted server-side provisioning. Never runs on the child's phone."""
import json
import os
from urllib.parse import urlparse
from provider import OpenAI

def provision():
    url=os.environ['LUMI_PUBLIC_URL'];host=urlparse(url).hostname;token=os.environ['LUMI_CATALOG_TOKEN']
    if not host or urlparse(url).scheme!='https' or urlparse(url).port not in (None,443,8443):raise SystemExit('A public HTTPS origin on 443/8443 is required')
    if len(token)<24 or token==os.environ.get('LUMI_DEVICE_TOKEN'):raise SystemExit('A separate catalog token is required')
    api=OpenAI()
    vault=api.call('/vaults',{'name':'LUMI approved mission catalog','metadata':{'app':'lumi-orbit','scope':'catalog-read-only'}})
    # No automatic POST retries: a timeout requires operator reconciliation to avoid duplicates.
    try:
        credential=api.call('/vaults/'+vault['id']+'/credentials',{'name':'Read approved LUMI catalog','auth':{'type':'environment_variable','secret_name':'LUMI_CATALOG_TOKEN','secret_value':token,'networking':{'type':'limited','allowed_hosts':[host]}}})
    except Exception:
        print(json.dumps({'vault_id':vault['id'],'status':'credential_not_confirmed','action':'Inspect this vault before retrying'}));raise
    print(json.dumps({'vault_id':vault['id'],'credential_id':credential['id'],'status':'created'}))

if __name__=='__main__':provision()
