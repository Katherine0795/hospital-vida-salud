import os,secrets
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'.env'
if p.exists():
    raise SystemExit('Ya existe .env; conservarlo para no cambiar credenciales de volumenes existentes')
keys=['POSTGRES_PASSWORD','RABBIT_PASSWORD','PACIENTES_DB_PASSWORD','CITAS_DB_PASSWORD','ATENCIONES_DB_PASSWORD','PAGOS_DB_PASSWORD','NOTIFICACIONES_DB_PASSWORD','AUDITORIA_DB_PASSWORD']
lines=['POSTGRES_USER=hospital_admin','RABBIT_USER=hospital_events','IMAGE_TAG=demo']
lines += [k+'='+secrets.token_hex(24) for k in keys]
p.write_text('\n'.join(lines)+'\n')
os.chmod(p,0o600)
print('Archivo local .env creado. No guardarlo en Git.')
