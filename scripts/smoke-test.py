#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅用于全新可丢弃MySQL实例的完整会员、课时、扫码、资金及权限验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import json,os,secrets,urllib.request,urllib.error,http.cookiejar,datetime,decimal,time,concurrent.futures,threading
from zoneinfo import ZoneInfo
from pathlib import Path
root=Path(__file__).resolve().parents[1]
env=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
base=os.environ.get('TEST_URL','http://127.0.0.1:'+env['WEB_PORT']);count=0;guard=threading.Lock()
def check(value,message):
 """只打印安全断言名，不打印密码、会话或签到码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
 global count
 with guard:count+=1
 if not value:raise AssertionError(message)
class Client:
 """每个岗位独立Cookie与CSRF，不共享登录会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
 def __init__(self):self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
 def call(self,path,method='GET',body=None,expected=200,raw=False):
  if method!='GET' and self.csrf is None:self.csrf=self.call('/api/auth/csrf')
  headers={'Content-Type':'application/json'}
  if method!='GET':headers[self.csrf['header']]=self.csrf['token']
  request=urllib.request.Request(base+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
  try:
   with self.opener.open(request,timeout=30) as result:status=result.status;data=result.read()
  except urllib.error.HTTPError as error:status=error.code;data=error.read()
  check(status==expected,f'{method} {path}: expected {expected}, got {status}')
  return data.decode('utf-8-sig') if raw else json.loads(data)
 def login(self,name,password):self.call('/api/auth/login','POST',{'username':name,'password':password});return self
D=lambda value:decimal.Decimal(str(value))
now=datetime.datetime.now(datetime.timezone.utc);local=now.astimezone(ZoneInfo('Asia/Shanghai'));today=local.date();password='Aa9'+secrets.token_hex(18)
anon=Client();check(anon.call('/actuator/health')['status']=='UP','health');anon.call('/api/workspace',expected=401)
a=Client().login(env.get('ADMIN_USERNAME','admin'),env['ADMIN_PASSWORD']);w=a.call('/api/workspace')
check(all(len(w[key])==0 for key in ['members','coaches','rooms','courses','plans','passes','sessions','reservations','visits']),'refuse nonempty test database')
roles=a.call('/api/admin/roles');check(len(roles)==6,'six bootstrap roles');check(len(a.call('/api/admin/permissions'))==12,'twelve permissions');check(len(w['categories'])==4,'four course categories')
role=lambda name:next(r['id'] for r in roles if name in r['name'])
def member(code,branch=1,name=None):return a.call('/api/master/members','POST',{'code':code,'name':name or 'TEST '+code,'departmentId':branch,'contactNote':'TEST contact only; no real personal data','enabled':True})
m1=member('TEST-M001',name='TEST 会员一 / Member One');m2=member('TEST-M002',name='TEST 会员二 / Member Two');branch=a.call('/api/admin/departments','POST',{'name':'TEST London branch','zone':'Europe/London','enabled':True});m3=member('TEST-M003',branch['id'])
def user(name,role_name,dep,member_id=None,coach_id=None):return a.call('/api/admin/users','POST',{'username':name,'displayName':'TEST '+name,'password':password,'roleId':role(role_name),'departmentId':dep,'memberId':member_id,'coachId':coach_id,'enabled':True})
coach=a.call('/api/master/coaches','POST',{'code':'TEST-COACH-01','name':'TEST 教练 / Coach','departmentId':1,'specialty':'TEST group classes','enabled':True});room=a.call('/api/master/rooms','POST',{'name':'TEST 一教室 / Studio 1','departmentId':1,'capacity':8,'enabled':True})
course=a.call('/api/master/courses','POST',{'name':'TEST 体验课程','nameEn':'TEST Intro class','departmentId':1,'categoryId':w['categories'][0]['id'],'durationMinutes':5,'capacity':4,'enabled':True})
plan=a.call('/api/master/plans','POST',{'name':'TEST 五次课程卡','nameEn':'TEST Five-class pack','departmentId':1,'mode':'CREDITS','credits':5,'validDays':30,'price':'199.95','enabled':True})
period=a.call('/api/master/plans','POST',{'name':'TEST 三十天期限卡','nameEn':'TEST Thirty-day membership','departmentId':1,'mode':'PERIOD','credits':0,'validDays':30,'price':'89.99','enabled':True})
user('test-member','Member',1,m1['id']);user('test-other','Member',1,m2['id']);user('test-coach','Coach',1,None,coach['id']);user('test-reception','Reception',1);user('test-outside','Branch operations',branch['id'])
b=Client().login('test-member',password);other=Client().login('test-other',password);trainer=Client().login('test-coach',password);reception=Client().login('test-reception',password);outside=Client().login('test-outside',password)
print('PASS: fresh schema, accounts and catalogue initialized with TEST data only',flush=True)
def issue(mid,p=plan,date=today):return a.call('/api/passes','POST',{'memberId':mid,'planId':p['id'],'startsOn':date.isoformat(),'note':'TEST external agreement'})
def detail(pid):return a.call('/api/passes/'+str(pid))
def card(pid):return detail(pid)['summary']['pass']
def cash(pid,kind,amount,origin=None,expected=200,ref=None,client=a):return client.call(f'/api/passes/{pid}/cash','POST',{'kind':kind,'reference':ref or 'TEST-'+secrets.token_hex(8),'amount':amount,'originalId':origin,'note':'TEST verified external fact','revision':card(pid)['revision']},expected)
p1=issue(m1['id']);p2=issue(m2['id']);cash(p1['id'],'RECEIPT','70.00');check(card(p1['id'])['state']=='DRAFT','partial payment stays draft');cash(p1['id'],'RECEIPT','129.95',client=reception);cash(p2['id'],'RECEIPT','199.95');check(card(p1['id'])['state']=='ACTIVE','full payment activates');check(D(detail(p1['id'])['summary']['netPaid'])==D('199.95'),'exact received amount')
# Session is in the future; attendance window permits reception verification immediately.
start=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(minutes=1)
def session(at,capacity=4):return a.call('/api/sessions','POST',{'departmentId':1,'courseId':course['id'],'coachId':coach['id'],'roomId':room['id'],'localStart':at.astimezone(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None,microsecond=0).isoformat(timespec='seconds'),'capacity':capacity,'cancelHours':0,'note':'TEST on-site class'})
def sd(sid):return a.call('/api/sessions/'+str(sid))
def sa(sid,action,expected=200):return a.call(f'/api/sessions/{sid}/{action}','POST',{'revision':sd(sid)['summary']['session']['revision'],'note':'TEST scheduling verified'},expected)
s1=session(start,1);sa(s1['id'],'publish');s2=session(start+datetime.timedelta(days=1),4);sa(s2['id'],'publish');s3=session(start+datetime.timedelta(days=2),4);sa(s3['id'],'publish')
# Real database concurrency, distinct members and independently held sessions.
def payload(mid,pid,sid):return {'memberId':mid,'passId':pid,'sessionId':sid,'passRevision':card(pid)['revision'],'sessionRevision':sd(sid)['summary']['session']['revision']}
v1=payload(m1['id'],p1['id'],s1['id']);v2=payload(m2['id'],p2['id'],s1['id'])
def compete(client,data):
 try:return client.call('/api/portal/reservations','POST',data)
 except AssertionError:
  # A competing request must be a controlled capacity rejection; do not replay it.
  raise
# Obtain raw statuses without asserting which racer wins.
def race(client,data):
 headers={'Content-Type':'application/json',client.csrf['header']:client.csrf['token']};req=urllib.request.Request(base+'/api/portal/reservations',method='POST',headers=headers,data=json.dumps(data).encode())
 try:
  with client.opener.open(req,timeout=25) as res:return res.status,json.loads(res.read())
 except urllib.error.HTTPError as error:return error.code,json.loads(error.read())
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 futures=[pool.submit(race,b,v1),pool.submit(race,other,v2)];outcomes=[f.result() for f in futures]
check(sorted(code for code,_ in outcomes)==[200,409],'last seat only one winner');check(next(body['code'] for code,body in outcomes if code==409)=='CLASS_FULL','loser has explicit full-class response')
winning=next(body for code,body in outcomes if code==200);a.call(f"/api/reservations/{winning['id']}/cancel",'POST',{'revision':winning['revision'],'note':'TEST race cleanup'})
booking=b.call('/api/portal/reservations','POST',payload(m1['id'],p1['id'],s1['id']));check(card(p1['id'])['held']==1 and card(p1['id'])['used']==0,'credit reserved but not consumed')
qr=b.call('/api/portal/check-token','POST',{});outside.call('/api/checkin','POST',{'token':qr['token'],'sessionId':s1['id']},403);reception.call('/api/checkin','POST',{'token':qr['token'],'sessionId':s1['id']});reception.call('/api/checkin','POST',{'token':qr['token'],'sessionId':s1['id']},409);check(card(p1['id'])['held']==0 and card(p1['id'])['used']==1,'single consumption after QR')
check(any(e['action']=='BOOKING_CANCELLED' for e in a.call('/api/reservations/'+str(winning['id']))['events']),'cancelled race history retained even when the unique booking row is reused');check(len(trainer.call('/api/coaching')['sessions'])==3,'coach only assigned classes');check('contactNote' not in json.dumps(trainer.call('/api/sessions/'+str(s1['id']))),'coach roster excludes contact notes')
print('PASS: actual MySQL seat race, reservation hold and single-use QR attendance',flush=True)
# Cancellation and freeze keep immutable ledgers and precise versions.
booking2=b.call('/api/portal/reservations','POST',payload(m1['id'],p1['id'],s2['id']));b.call(f"/api/portal/reservations/{booking2['id']}/cancel",'POST',{'revision':booking2['revision'],'note':'TEST member cancellation'});check(card(p1['id'])['held']==0,'cancellation releases hold')
def pa(pid,action,days=None,expected=200):return a.call(f'/api/passes/{pid}/{action}','POST',{'revision':card(pid)['revision'],'days':days,'note':'TEST recorded agreement'},expected)
old_end=card(p2['id'])['endsOn'];pa(p2['id'],'freeze',7);check(card(p2['id'])['endsOn']==(datetime.date.fromisoformat(old_end)+datetime.timedelta(days=7)).isoformat(),'freeze extends by seven days');other.call('/api/portal/reservations','POST',payload(m2['id'],p2['id'],s2['id']),409);pa(p2['id'],'unfreeze');check(card(p2['id'])['endsOn']==old_end,'early unfreeze removes unused extension')
origin=detail(p2['id'])['cash'][0]['entry']['id'];cash(p2['id'],'REFUND','1.00',origin,409);pa(p2['id'],'close');refund=cash(p2['id'],'REFUND','20.00',origin);cash(p2['id'],'REVERSAL','199.95',origin,409);cash(p2['id'],'REVERSAL','20.00',refund['id']);cash(p2['id'],'REVERSAL','20.00',refund['id'],409);cash(p2['id'],'REVERSAL','199.95',origin);check(D(detail(p2['id'])['summary']['netPaid'])==0,'reversed net receipt zero');check(len(detail(p2['id'])['cash'])==4,'all original financial facts retained')
# Period cards allow venue entry while class packs do not.
period_card=issue(m1['id'],period);cash(period_card['id'],'RECEIPT','89.99');reception.call('/api/visits','POST',{'memberId':m1['id'],'passId':p1['id']},409)
time.sleep(5.1)
qr2=b.call('/api/portal/check-token','POST',{});reception.call('/api/checkin','POST',{'token':qr2['token']});check(len(b.call('/api/portal')['visits'])==1,'period QR actual entry');reception.call('/api/visits','POST',{'memberId':m1['id'],'passId':period_card['id']},409);check(detail(period_card['id'])['summary']['available'] is None,'period balance is not fake finite credits')
# Editing plan terms does not rewrite sold cards. Renewal is an independent agreement.
plan=a.call('/api/master/plans/'+str(plan['id']),'PUT',{**plan,'name':'TEST 八次续开卡','nameEn':'TEST Eight-class renewal','credits':8,'price':'225.00'})
check(card(p1['id'])['credits']==5 and D(card(p1['id'])['price'])==D('199.95'),'sold card snapshot immutable');renewal=issue(m1['id'],plan,today+datetime.timedelta(days=30));check(renewal['credits']==8 and renewal['id']!=p1['id'],'renewal card independent')
partial=issue(m1['id'],plan);r=cash(partial['id'],'RECEIPT','14.99');pa(partial['id'],'close');cash(partial['id'],'REFUND','15.00',r['id'],409);cash(partial['id'],'REFUND','14.99',r['id']);check(D(detail(partial['id'])['summary']['netPaid'])==0,'partial draft refund exact')
# Stale versions and invalid decimals never change balance.
a.call('/api/passes/'+str(renewal['id'])+'/cash','POST',{'kind':'RECEIPT','reference':'TEST-BAD-DECIMAL','amount':'1.001','note':'TEST bad precision','revision':renewal['revision']},400)
a.call('/api/passes/'+str(renewal['id'])+'/freeze','POST',{'revision':0,'days':7,'note':'TEST stale'},409)
a.call('/api/master/rooms','POST',{'name':'TEST bad fractional capacity','departmentId':1,'capacity':1.5,'enabled':True},400)
a.call('/api/sessions/'+str(s2['id'])+'/finish','POST',{'revision':sd(s2['id'])['summary']['session']['revision'],'note':'TEST too early'},409)
for path in ['/api/workspace','/api/admin/users','/api/audit','/api/sessions/'+str(s1['id']),'/api/passes/'+str(p2['id']),'/api/reports?from='+today.isoformat()+'&to='+today.isoformat()]:b.call(path,expected=403)
check(len(outside.call('/api/workspace')['members'])==1,'branch member isolation');outside.call('/api/passes/'+str(p1['id']),expected=403);trainer.call('/api/passes/'+str(p1['id']),expected=403)
check(all('passwordHash' not in row for row in a.call('/api/admin/users')),'hashes not returned')
# Complete import succeeds; a bad batch rolls back member and audit writes together.
rows=[{'code':f'TEST-IMP-{i:02d}','name':f'TEST 导入会员 {i:02d}','departmentId':1,'contactNote':'TEST import only','enabled':True} for i in range(1,13)]
a.call('/api/members/import','POST',rows);before=len(a.call('/api/workspace')['members']);before_audit=len(a.call('/api/audit'))
bad=[{'code':'TEST-ATOMIC','name':'TEST row one','departmentId':1,'enabled':True},{'code':'TEST-ATOMIC','name':'TEST row two','departmentId':1,'enabled':True}];error=a.call('/api/members/import','POST',bad,400);check(error['row']==2 and error['code']=='IMPORT_INVALID','import exact safe error record');check(len(a.call('/api/workspace')['members'])==before,'bad import creates no members');check(len(a.call('/api/audit'))==before_audit,'bad import creates no audit')
# Bound accounts cannot become employees; current credentials and roles are rechecked.
me=b.call('/api/auth/me');a.call('/api/admin/users/'+str(me['id']),'PUT',{'username':'test-member','displayName':'TEST attempted conversion','roleId':role('Administrator'),'departmentId':1,'enabled':True},409)
custom=a.call('/api/admin/roles','POST',{'name':'TEST read-only role','scope':'DEPARTMENT','permissions':['read']});reader=a.call('/api/admin/users','POST',{'username':'test-reader','displayName':'TEST reader','password':password,'roleId':custom['id'],'departmentId':1,'enabled':True});reader_client=Client().login('test-reader',password);reader_client.call('/api/workspace');a.call('/api/admin/roles/'+str(custom['id']),'PUT',{**custom,'permissions':[]});reader_client.call('/api/workspace',expected=403)
a.call('/api/admin/users/'+str(reader['id']),'PUT',{'username':'test-reader','displayName':'TEST reader','password':'Bb8'+secrets.token_hex(18),'roleId':custom['id'],'departmentId':1,'enabled':True});reader_client.call('/api/auth/me',expected=401)
# Reports use a single branch-local date definition; branch totals reconcile.
report=a.call('/api/reports?from='+today.isoformat()+'&to='+today.isoformat());check(sum(D(row['netReceived']) for row in report['branches'])==D(report['total']['netReceived']),'company equals branch net receipts');check(D(report['total']['netReceived'])==D('289.94'),'manual cash exact report');check(report['total']['attended']==1 and report['total']['visits']==1,'actual class and venue attendance report')
for typ in ['members','passes','attendance','cash']:
 csv=a.call('/api/exports/'+typ+'.csv',raw=True);check(',' in csv and '\r\n' in csv,'CSV opens as standard rows: '+typ)
ledger=detail(p1['id'])['credits'];check(sum(x['totalDelta'] for x in ledger)==5 and sum(x['heldDelta'] for x in ledger)==0 and sum(x['usedDelta'] for x in ledger)==1,'ledger totals reconcile independently of race winner')
print('PASS: freeze, refund/reversal, exact reports, import rollback and live permissions',flush=True)
state={'base':base,'adminUsername':env.get('ADMIN_USERNAME','admin'),'adminPassword':env['ADMIN_PASSWORD'],'memberUsername':'test-member','memberPassword':password,'otherUsername':'test-other','otherPassword':password,'coachUsername':'test-coach','coachPassword':password,'receptionUsername':'test-reception','receptionPassword':password,'outsideUsername':'test-outside','outsidePassword':password,'memberId':m1['id'],'otherId':m2['id'],'coachId':coach['id'],'roomId':room['id'],'courseId':course['id'],'planId':plan['id'],'periodPlanId':period['id'],'cardId':p1['id'],'periodCardId':period_card['id'],'renewalId':renewal['id'],'classId':s1['id'],'futureClassId':s2['id'],'secondFutureClassId':s3['id'],'attendanceBookingId':booking['id'],'checks':count}
path=root/'output'/os.environ.get('QUALITY_STATE_FILE','clubdesk-quality-state.json');path.parent.mkdir(exist_ok=True);fd=os.open(path,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
with os.fdopen(fd,'w')as f:json.dump(state,f,ensure_ascii=False)
print(f'PASS: {count} actual MySQL checks; private test state saved locally only. Class completion follows after its real end time.',flush=True)
