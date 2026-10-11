#!/usr/bin/env python3
"""Flujo hospitalario real por Gateway. Sin paquetes Python externos."""
import argparse,json,os,subprocess,time,uuid
from pathlib import Path
from datetime import datetime,timedelta,timezone
from urllib.request import Request,urlopen
from urllib.error import HTTPError,URLError
parser=argparse.ArgumentParser();parser.add_argument('--docker-checks',action='store_true');parser.add_argument('--base-url',default=os.environ.get('GATEWAY_URL','http://localhost:8080'));args=parser.parse_args()
base=args.base_url.rstrip('/');checks=[];resources={}
def raw(method,path,body=None):
    data=None if body is None else json.dumps(body).encode()
    req=Request(base+path,data=data,method=method,headers={'Content-Type':'application/json'})
    try:
        with urlopen(req,timeout=8) as res: code=res.status;content=res.read()
    except HTTPError as e:code=e.code;content=e.read()
    try:value=json.loads(content)
    except (json.JSONDecodeError,UnicodeDecodeError):value=content.decode(errors='replace')
    return code,value

def call(name,method,path,body=None,expected=200):
    code,value=raw(method,path,body)
    acceptable=expected if isinstance(expected,tuple) else (expected,)
    assert code in acceptable,f'{name}: esperado {acceptable}, recibido {code}: {value}'
    checks.append({'caso':name,'estado':'OK','http':code});print(f'OK {name} HTTP {code}',flush=True)
    return value

def wait_for(fn,timeout=180):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        try:
            value=fn()
            if value:return value
        except (URLError,TimeoutError,ConnectionError,AssertionError,ValueError):pass
        time.sleep(2)
    raise AssertionError('Tiempo agotado esperando disponibilidad o consistencia eventual')

def compose(*argv):
    return subprocess.check_output(['docker','compose',*argv],cwd=Path(__file__).resolve().parents[1],text=True).strip()

def sql(database,query):
    # query se construye unicamente con UUIDs generados/validados en este script.
    return compose('exec','-T','postgres','psql','-U','hospital_admin','-d',database,'-At','-v','ON_ERROR_STOP=1','-c',query)

try:
    unknown=str(uuid.uuid4())
    for path in ['/api/pacientes','/api/citas','/api/notificaciones?citaId='+unknown,'/api/auditoria?recursoId='+unknown,'/api/atenciones/'+unknown,'/api/pagos/'+unknown]:
        wait_for(lambda path=path:raw('GET',path)[0] in (200,404))
    suffix=uuid.uuid4().hex[:12]
    patient={'tipoDocumento':'OTRO','numeroDocumento':'DEMO-'+suffix,'nombres':'Paciente','apellidos':'Demostracion','fechaNacimiento':'1990-01-01','telefono':'000000000','correo':'demo@example.test','direccion':'Direccion ficticia'}
    p=call('Registrar paciente','POST','/api/pacientes',patient,201);pid=str(uuid.UUID(p['id']));resources['pacienteId']=pid
    call('Consultar paciente','GET','/api/pacientes/'+pid)
    call('Rechazar documento duplicado','POST','/api/pacientes',patient,409)
    call('Rechazar campos ausentes','POST','/api/pacientes',{},400)
    call('Paciente inexistente','GET','/api/pacientes/'+unknown,None,404)
    date=(datetime.now(timezone.utc)+timedelta(days=3)).replace(minute=30,second=0,microsecond=0)
    cita={'pacienteId':pid,'especialidad':'DEMO','fechaHora':date.isoformat(),'profesionalCodigo':'DEMO-'+suffix,'motivo':'Gestion administrativa ficticia'}
    c=call('Programar cita','POST','/api/citas',cita,201);cid=str(uuid.UUID(c['id']));resources['citaId']=cid
    call('Consultar cita','GET','/api/citas/'+cid)
    call('Rechazar agenda ocupada','POST','/api/citas',cita,409)
    call('Rechazar agenda fuera de bloque','POST','/api/citas',{**cita,'fechaHora':(date+timedelta(minutes=7)).isoformat()},400)
    call('Rechazar cita para paciente inexistente','POST','/api/citas',{**cita,'pacienteId':unknown,'fechaHora':(date+timedelta(hours=1)).isoformat()},404)
    call('Rechazar atencion sin confirmar','POST','/api/atenciones',{'citaId':cid,'area':'DEMO'},409)
    call('Confirmar cita','PATCH','/api/citas/'+cid+'/estado',{'estado':'CONFIRMADA'})
    call('Rechazar cierre sin referencia de atencion','PATCH','/api/citas/'+cid+'/estado',{'estado':'ATENDIDA'},400)
    for service,param in [('notificaciones','citaId'),('auditoria','recursoId')]:
        def received(service=service,param=param):
            code,events=raw('GET',f'/api/{service}?{param}={cid}')
            return code==200 and isinstance(events,list) and any(e['eventType']=='CitaConfirmada' for e in events)
        wait_for(received)
        checks.append({'caso':'Evento consumido y persistido en '+service,'estado':'OK'});print('OK evento '+service,flush=True)
    a=call('Registrar atencion','POST','/api/atenciones',{'citaId':cid,'area':'ADMISION DEMO'},201);aid=str(uuid.UUID(a['id']));resources['atencionId']=aid
    assert a['estado']=='REGISTRADA'
    call('Consultar atencion','GET','/api/atenciones/'+aid)
    again=call('Atencion idempotente','POST','/api/atenciones',{'citaId':cid,'area':'ADMISION DEMO'},201);assert again['id']==aid
    c=call('Cita queda atendida','GET','/api/citas/'+cid);assert c['estado']=='ATENDIDA' and c['atencionId']==aid
    call('Estado final no reabre','PATCH','/api/citas/'+cid+'/estado',{'estado':'CONFIRMADA'},409)
    call('Atendida no se reprograma','PATCH','/api/citas/'+cid+'/agenda',{'fechaHora':(date+timedelta(days=5)).isoformat(),'profesionalCodigo':cita['profesionalCodigo']},409)
    c2=call('Segunda cita futura','POST','/api/citas',{**cita,'fechaHora':(date+timedelta(days=1)).isoformat()},201);c2id=str(uuid.UUID(c2['id']))
    call('Reprogramar cita','PATCH','/api/citas/'+c2id+'/agenda',{'fechaHora':(date+timedelta(days=6)).isoformat(),'profesionalCodigo':cita['profesionalCodigo']})
    call('Cancelar cita','PATCH','/api/citas/'+c2id+'/estado',{'estado':'CANCELADA'})
    call('Cancelada no se confirma','PATCH','/api/citas/'+c2id+'/estado',{'estado':'CONFIRMADA'},409)
    call('Agenda cancelada se libera','POST','/api/citas',{**cita,'fechaHora':(date+timedelta(days=6)).isoformat()},201)
    inactive=call('Paciente inactivo','POST','/api/pacientes',{**patient,'numeroDocumento':'INACT-'+suffix,'estado':'INACTIVO'},201)
    call('Paciente inactivo no agenda','POST','/api/citas',{**cita,'pacienteId':inactive['id'],'fechaHora':(date+timedelta(days=2)).isoformat()},409)
    pay={'pacienteId':pid,'concepto':'Cargo administrativo ficticio','monto':50,'medio':'EFECTIVO','numeroOperacion':'DEMO-'+suffix}
    call('Pago cero rechazado','POST','/api/pagos',{**pay,'monto':0},400)
    call('Pago negativo rechazado','POST','/api/pagos',{**pay,'monto':-1},400)
    payment=call('Pago simulado valido','POST','/api/pagos',pay,201);resources['pagoId']=payment['id']
    call('Consulta de pago','GET','/api/pagos/'+payment['id'])
    call('Operacion de pago duplicada','POST','/api/pagos',pay,409)
    if args.docker_checks:
        assert sql('pacientes',f"SELECT count(*) FROM paciente WHERE id='{pid}'")== '1'
        assert sql('atenciones',f"SELECT count(*) FROM atencion WHERE cita_id='{cid}'")== '1'
        checks.append({'caso':'Persistencia PostgreSQL real','estado':'OK'})
        # Un mismo eventId se publica otra vez; consumidores no duplican sus filas.
        eventid=str(uuid.UUID(next(e['eventId'] for e in raw('GET','/api/notificaciones?citaId='+cid)[1] if e['eventType']=='CitaConfirmada')))
        sql('citas',f"UPDATE outbox SET sent_at=NULL WHERE event_id='{eventid}'")
        wait_for(lambda:sql('citas',f"SELECT count(*) FROM outbox WHERE event_id='{eventid}' AND sent_at IS NOT NULL")=='1')
        assert sql('notificaciones',f"SELECT count(*) FROM evento WHERE event_id='{eventid}'")=='1'
        assert sql('auditoria',f"SELECT count(*) FROM evento WHERE event_id='{eventid}'")=='1'
        checks.append({'caso':'Republicacion real sin duplicados','estado':'OK'})
        # El productor persiste la outbox aunque el broker no este disponible.
        pending=call('Cita para recuperacion del broker','POST','/api/citas',{**cita,'fechaHora':(date+timedelta(days=3)).isoformat()},201);pendingid=pending['id']
        compose('stop','rabbitmq')
        call('Confirmacion con broker temporalmente caido','PATCH','/api/citas/'+pendingid+'/estado',{'estado':'CONFIRMADA'})
        assert int(sql('citas',f"SELECT count(*) FROM outbox WHERE payload LIKE '%{pendingid}%' AND sent_at IS NULL"))>=1
        compose('start','rabbitmq')
        wait_for(lambda:any(e['eventType']=='CitaConfirmada' for e in raw('GET','/api/notificaciones?citaId='+pendingid)[1]))
        checks.append({'caso':'Outbox recupera evento tras caida RabbitMQ','estado':'OK'})
        # La comprobacion sincronica impide insertar una cita sin validar al paciente.
        compose('stop','pacientes')
        try:
            call('Dependencia caida devuelve error controlado','POST','/api/citas',{**cita,'fechaHora':(date+timedelta(days=4)).isoformat()},(503,504))
        finally:compose('start','pacientes')
        wait_for(lambda:raw('GET','/api/pacientes/'+pid)[0]==200)
    report={'fechaUtc':datetime.now(timezone.utc).isoformat(),'estado':'OK','checks':checks,'recursosFicticios':resources}
    print(f'Flujo completado: {len(checks)} comprobaciones',flush=True)
except Exception as ex:
    report={'fechaUtc':datetime.now(timezone.utc).isoformat(),'estado':'FALLO','error':str(ex),'checks':checks,'recursosFicticios':resources}
    raise
finally:
    directory=Path(__file__).resolve().parents[1]/'evidencias';directory.mkdir(exist_ok=True)
    (directory/'smoke-result.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
