import sys, zlib, struct, gzip, io, os
from PIL import Image
def rd(f,t):
    if t==1: return struct.unpack('>b',f.read(1))[0]
    if t==2: return struct.unpack('>h',f.read(2))[0]
    if t==3: return struct.unpack('>i',f.read(4))[0]
    if t==4: return struct.unpack('>q',f.read(8))[0]
    if t==5: return struct.unpack('>f',f.read(4))[0]
    if t==6: return struct.unpack('>d',f.read(8))[0]
    if t==7: n=rd(f,3); return f.read(n)
    if t==8: n=struct.unpack('>H',f.read(2))[0]; return f.read(n).decode('utf-8','replace')
    if t==9: et=rd(f,1); n=rd(f,3); return [rd(f,et) for _ in range(n)]
    if t==10:
        d={}
        while True:
            tt=rd(f,1)
            if tt==0: return d
            nm=rd(f,8); d[nm]=rd(f,tt)
    if t==11: n=rd(f,3); return list(struct.unpack('>%di'%n, f.read(4*n)))
    if t==12: n=rd(f,3); return [rd(f,4) for _ in range(n)]
def root(b):
    f=io.BytesIO(b); t=rd(f,1); rd(f,8); return rd(f,10)

def load_world(world):
    lvl=root(gzip.open(os.path.join(world,'level.dat')).read())
    names={}
    for e in lvl['FML']['Registries']['minecraft:blocks']['ids']:
        names[e['V']]=e['K']
    return names
def chunk_at(world,cx,cz):
    rx,rz=cx>>5,cz>>5
    p=os.path.join(world,'region','r.%d.%d.mca'%(rx,rz))
    if not os.path.exists(p): return None
    with open(p,'rb') as f:
        f.seek(4*((cx&31)+(cz&31)*32)); off=struct.unpack('>I',f.read(4))[0]
        if off==0: return None
        f.seek((off>>8)*4096); ln=struct.unpack('>I',f.read(4))[0]; f.read(1); data=f.read(ln-1)
    L=root(zlib.decompress(data))['Level']; secs={}
    for s in L.get('Sections',[]): secs[s['Y']]=(s['Blocks'],s.get('Add'))
    return secs
def block(cache,world,names,x,y,z):
    key=(x>>4,z>>4)
    if key not in cache: cache[key]=chunk_at(world,x>>4,z>>4)
    secs=cache[key]
    if not secs or (y>>4) not in secs: return None
    blocks,add=secs[y>>4]; i=((y&15)<<8)|((z&15)<<4)|(x&15)
    bid=blocks[i]&255
    if add is not None:
        a=add[i>>1]; a=(a>>4)&15 if i&1 else a&15; bid|=a<<8
    return None if bid==0 else names.get(bid,str(bid))
