"""Pages de test « mises en page difficiles » : bulles qui se touchent ou se chevauchent, narration collée à une bulle, bulles sombres, nuages, bulles à pointes, texte libre proche d'un dialogue. Vérité terrain : blocs et lignes."""
import random, json, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import sys, os
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/site/synth'
os.makedirs(OUT, exist_ok=True)
random.seed(21)
FONTS=['/usr/share/fonts/truetype/comic-neue/ComicNeue-Bold.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf']
SENT=["I TOLD YOU TO STAY AWAY FROM HERE","WHAT DO YOU WANT FROM ME","THE MASTER WILL NOT SEE ANYONE TODAY","DON'T UNDERESTIMATE THE OLD MAN","IT HAS BEEN THREE YEARS SINCE THE WAR","HOW DID YOU FIND THIS PLACE","WE MUST LEAVE BEFORE DAWN","SOMEONE IS WATCHING US FROM THE ROOF","I NEVER THOUGHT I WOULD SEE YOU AGAIN","THIS IS THE END OF THE ROAD","LET ME HANDLE THE REST","THEY WILL PAY FOR WHAT THEY DID","YOU CALL THAT A TECHNIQUE","STAY BEHIND ME AND DON'T MOVE","THE GATE WILL OPEN AT SUNSET"]
def art_bg(W, H):
    """Fond « dessin » procédural : dégradé, formes translucides et bruit, sans image protégée."""
    c0 = tuple(random.randint(40, 200) for _ in range(3)); c1 = tuple(random.randint(40, 200) for _ in range(3))
    img = Image.new("RGB", (W, H)); px = img.load()
    for y in range(H):
        t = y / H; col = tuple(int(c0[k] * (1 - t) + c1[k] * t) for k in range(3))
        for x in range(0, W): px[x, y] = col
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
    for _ in range(26):
        cx, cy = random.randint(0, W), random.randint(0, H); r = random.randint(40, 160)
        pts = [(cx + r * random.uniform(0.4, 1) * math.cos(a), cy + r * random.uniform(0.4, 1) * math.sin(a)) for a in [i * math.pi / 3 for i in range(6)]]
        od.polygon(pts, fill=tuple(random.randint(20, 230) for _ in range(3)) + (random.randint(60, 150),))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    return img.filter(ImageFilter.GaussianBlur(1.2))
def wrap(words,font,maxw,d):
    lines=[];cur=''
    for w in words:
        t=(cur+' '+w).strip()
        if d.textlength(t,font=font)<=maxw: cur=t
        else: lines.append(cur); cur=w
    lines.append(cur); return lines
def inter(a,b,pad=0):
    return not (a[2]+pad<=b[0] or b[2]+pad<=a[0] or a[3]+pad<=b[1] or b[3]+pad<=a[1])
def make(idx):
    W,H=800,random.choice([1000,1150,1300])
    bg=random.choice(['art','white','art']); img=art_bg(W,H) if bg=='art' else Image.new('RGB',(W,H),'white')
    d=ImageDraw.Draw(img); blocks=[]; text_rects=[]; shapes=[]
    y=random.randint(30,70); prev=None; bid=0
    while y<H-200 and bid<7:
        kind=random.choices(['bubble','dark','narration','cloud','spiky','free'],[34,10,16,10,10,20])[0]
        font=ImageFont.truetype(random.choice(FONTS),random.choice([26,28,30,32]))
        txt=random.choice(SENT); maxw=random.randint(220,330)
        ls=wrap(txt.split(),font,maxw,d)
        lh=int(font.size*1.22); tw=max(d.textlength(l,font=font) for l in ls); th=lh*len(ls)
        padx,pady=(46,38) if kind in ('bubble','dark','cloud','spiky') else (22,16)
        bw,bh=int(tw+2*padx),int(th+2*pady)
        # position : colle ou chevauche la forme précédente avec une probabilité élevée
        for _try in range(30):
            if prev and random.random()<0.75:
                ov=random.choice([-0.04,0.0,0.05,0.12,0.2])
                x0=int(prev[0]+random.randint(-140,140)); y0=int(prev[3]-bh*ov-random.choice([0,0,4]) ) if random.random()<0.7 else int(prev[1]+random.randint(0,40))
                if random.random()<0.35: x0=prev[2]-int(bw*random.choice([0.1,0.2])) ; y0=prev[1]+random.randint(0,60)
            else:
                x0=random.randint(40,W-bw-40); y0=y
            x0=max(20,min(W-bw-20,x0)); y0=max(20,y0)
            if y0+bh>H-20: continue
            tx0=x0+(bw-tw)/2; ty0=y0+(bh-th)/2; trect=(tx0,ty0,tx0+tw,ty0+th)
            if any(inter(trect,t,14) for t in text_rects): continue
            break
        else:
            y+=60; continue
        box=(x0,y0,x0+bw,y0+bh)
        fg='black'; 
        if kind=='bubble': d.ellipse(box,fill='white',outline='black',width=3)
        elif kind=='dark': d.ellipse(box,fill=(10,10,14),outline=(10,10,14),width=3); fg='white'
        elif kind=='cloud':
            for k in range(9):
                a=2*math.pi*k/9; cx=(box[0]+box[2])/2+math.cos(a)*bw*0.36; cy=(box[1]+box[3])/2+math.sin(a)*bh*0.36
                d.ellipse((cx-bw*0.2,cy-bh*0.2,cx+bw*0.2,cy+bh*0.2),fill='white',outline='black',width=3)
            d.ellipse((box[0]+bw*0.1,box[1]+bh*0.1,box[2]-bw*0.1,box[3]-bh*0.1),fill='white')
        elif kind=='spiky':
            pts=[]; n=18
            for k in range(2*n):
                a=math.pi*k/n; r=1.0 if k%2==0 else 0.82
                pts.append(((box[0]+box[2])/2+math.cos(a)*bw/2*r,(box[1]+box[3])/2+math.sin(a)*bh/2*r))
            d.polygon(pts,fill='white',outline='black')
        elif kind=='narration':
            col=random.choice([(250,236,160),(235,235,235),(255,255,255)])
            d.rectangle(box,fill=col,outline='black',width=3)
        else: # texte libre : blanc contouré sur dessin (ou noir sur fond blanc)
            fg='white' if bg=='art' else 'black'
        # texte
        yy=ty0; lines=[]
        for l in ls:
            w=d.textlength(l,font=font); x=(W/2 if False else tx0+(tw-w)/2)
            if kind=='free' and bg=='art': d.text((x,yy),l,font=font,fill='white',stroke_width=3,stroke_fill='black')
            else: d.text((x,yy),l,font=font,fill=fg)
            bb=d.textbbox((x,yy),l,font=font); lines.append({'text':l,'box':[bb[0],bb[1],bb[2],bb[3]]}); yy+=lh
        blocks.append({'id':bid,'kind':kind,'lines':lines,'shape':list(box)}); text_rects.append(trect); bid+=1
        prev=box; y=max(y,int(box[3]*0.6))
    img.save(f'{OUT}/layouts_{idx}.png'); json.dump(blocks,open(f'{OUT}/layouts_{idx}.json','w'))
    return len(blocks)
n = sum(make(i) for i in range(6)); print('mises en page générées :', n, 'blocs')
