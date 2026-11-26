import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mvlib import *
from PIL import Image
COL={'minecraft:grass':(90,150,60),'minecraft:dirt':(120,85,55),'minecraft:water':(50,90,200),'minecraft:planks':(150,110,70),
'minecraft:spruce_stairs':(110,80,50),'minecraft:log2':(70,50,30),'minecraft:cobblestone':(120,120,120),'minecraft:farmland':(100,70,40),
'minecraft:wheat':(200,190,80),'minecraft:carrots':(220,130,40),'minecraft:potatoes':(180,170,90),'minecraft:gravel':(140,130,125),
'shatteredcitadels:sealed_gate':(200,0,200),'shatteredcitadels:trial_spawner':(255,0,0),'minecraft:chest':(255,200,0),'shatteredcitadels:vase':(255,120,0),
'minecraft:bedrock':(30,30,30),'minecraft:iron_bars':(180,180,200),'minecraft:glowstone':(255,240,150),'minecraft:sea_lantern':(200,240,255),'minecraft:ladder':(160,120,60)}
world=sys.argv[1]; a0,a1,b0,b1=map(int,sys.argv[2:6]); out=sys.argv[6]; axis=sys.argv[7]
names=load_world(world); cache={}
W=a1-a0+1; H=256
img=Image.new('RGB',(W,H),(150,190,230)); px=img.load()
for a in range(a0,a1+1):
    for y in range(0,256):
        for b in range(b0,b1+1):
            x,z=(a,b) if axis=='x' else (b,a)
            n=block(cache,world,names,x,y,z)
            if n:
                c=COL.get(n)
                if c is None:
                    h=abs(hash(n))%200; c=(90+h%60,90+(h//3)%60,100+(h//7)%60)
                k=1.0-0.5*(b-b0)/max(1,(b1-b0))
                px[a-a0,255-y]=tuple(int(v*k) for v in c); break
img=img.crop((0,0,W,256)).resize((W*3,256*3),Image.NEAREST); img.save(out); print('saved',out)
