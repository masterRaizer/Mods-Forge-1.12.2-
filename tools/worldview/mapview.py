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
world=sys.argv[1]; x0,z0,x1,z1=map(int,sys.argv[2:6]); out=sys.argv[6]; mode=sys.argv[7] if len(sys.argv)>7 else 'top'
lvl=root(gzip.open(os.path.join(world,'level.dat')).read())
names={}
try:
    for e in lvl['FML']['Registries']['minecraft:blocks']['ids']:
        names[e['V']]=e['K']
except Exception as ex: print('no fml ids',ex)
def chunk(cx,cz):
    rx,rz=cx>>5,cz>>5
    p=os.path.join(world,'region','r.%d.%d.mca'%(rx,rz))
    if not os.path.exists(p): return None
    with open(p,'rb') as f:
        f.seek(4*((cx&31)+(cz&31)*32)); off=struct.unpack('>I',f.read(4))[0]
        if off==0: return None
        f.seek((off>>8)*4096); ln=struct.unpack('>I',f.read(4))[0]; comp=f.read(1)[0]; data=f.read(ln-1)
    return root(zlib.decompress(data))['Level']
COL={'minecraft:grass':(90,150,60),'minecraft:dirt':(120,85,55),'minecraft:water':(50,90,200),'minecraft:planks':(150,110,70),
'minecraft:spruce_stairs':(110,80,50),'minecraft:log2':(70,50,30),'minecraft:cobblestone':(120,120,120),'minecraft:farmland':(100,70,40),
'minecraft:wheat':(200,190,80),'minecraft:carrots':(220,130,40),'minecraft:potatoes':(180,170,90),'minecraft:gravel':(140,130,125),
'shatteredcitadels:sealed_gate':(200,0,200),'shatteredcitadels:trial_spawner':(255,0,0),'minecraft:chest':(255,200,0),'shatteredcitadels:vase':(255,120,0),
'minecraft:bedrock':(30,30,30),'minecraft:iron_bars':(180,180,200),'minecraft:glowstone':(255,240,150),'minecraft:sea_lantern':(200,240,255),'minecraft:ladder':(160,120,60)}
def colour(name,y):
    c=COL.get(name)
    if c is None:
        h=abs(hash(name))%200; c=(90+h%60,90+(h//3)%60,100+(h//7)%60)
    k=0.55+0.45*min(1,max(0,(y-4)/60.0))
    return tuple(int(v*k) for v in c)
W=x1-x0+1; H=z1-z0+1
img=Image.new('RGB',(W,H),(0,0,0)); px=img.load()
sliceY = int(sys.argv[8]) if len(sys.argv)>8 else None
counts={}
for cx in range(x0>>4,(x1>>4)+1):
    for cz in range(z0>>4,(z1>>4)+1):
        L=chunk(cx,cz)
        if L is None: continue
        secs={}
        for s in L.get('Sections',[]):
            blocks=s['Blocks']; add=s.get('Add'); data=s['Data']
            secs[s['Y']]=(blocks,add)
        for lx in range(16):
            for lz in range(16):
                X=cx*16+lx; Z=cz*16+lz
                if not(x0<=X<=x1 and z0<=Z<=z1): continue
                ys = [sliceY] if sliceY is not None else range(255,-1,-1)
                for y in ys:
                    sec=secs.get(y>>4)
                    if not sec: continue
                    blocks,add=sec; i=((y&15)<<8)|(lz<<4)|lx
                    bid=blocks[i]&255
                    if add is not None:
                        a=add[i>>1]; a=(a>>4)&15 if i&1 else a&15; bid|=a<<8
                    if bid==0: continue
                    n=names.get(bid,str(bid)); counts[n]=counts.get(n,0)+1
                    px[X-x0,Z-z0]=colour(n,y); break
img=img.resize((W*4,H*4),Image.NEAREST); img.save(out)
print('saved',out, 'top kinds', sorted(counts.items(), key=lambda kv:-kv[1])[:15])
