import matplotlib, json
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle
plt.rcParams['font.family']='DejaVu Sans'
NAVY='#1F3864'; BLUE='#2E74B5'; LBLUE='#DEEAF6'; GREY='#595959'; LGREY='#F2F2F2'
GREEN='#2E7D32'; LGREEN='#E8F5E9'; AMBER='#B26A00'; LAMBER='#FFF3E0'
RED='#A61C1C'; LRED='#FDECEA'; PURPLE='#5B2C8D'; LPURPLE='#EDE7F6'

def canvas(w,h):
    fig,ax=plt.subplots(figsize=(w,h)); ax.set_xlim(0,100); ax.set_ylim(0,100); ax.axis('off'); return fig,ax
def box(ax,x,y,w,h,title,lines=None,fc=LBLUE,ec=BLUE,tc=NAVY,fs=8.5,tfs=10):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle="round,pad=0.35,rounding_size=1.6",fc=fc,ec=ec,lw=1.5))
    ax.text(x+w/2,y+h-(2.6 if lines else h/2-1.0),title,ha='center',va='top' if lines else 'center',
            fontsize=tfs,fontweight='bold',color=tc)
    if lines:
        for i,l in enumerate(lines):
            ax.text(x+w/2,y+h-7.2-i*4.0,l,ha='center',va='top',fontsize=fs,color=GREY)
def arrow(ax,p1,p2,label=None,color=BLUE,ls='-',rad=0.0,lw=1.4,fs=7.5,off=(0,1.6),style='-|>'):
    ax.add_patch(FancyArrowPatch(p1,p2,arrowstyle=style,mutation_scale=13,color=color,lw=lw,
                 linestyle=ls,connectionstyle=f"arc3,rad={rad}",shrinkA=2,shrinkB=2))
    if label: ax.text((p1[0]+p2[0])/2+off[0],(p1[1]+p2[1])/2+off[1],label,ha='center',va='bottom',fontsize=fs,color=color)
def save(fig,n):
    fig.savefig('/tmp/figs2/'+n,dpi=200,bbox_inches='tight',facecolor='white'); plt.close(fig); print('wrote',n)

import os; os.makedirs('/tmp/figs2',exist_ok=True)

# ===== Fig 1: monolith internal architecture =====
fig,ax=canvas(12,7.4)
box(ax,4,4,92,92,'',None,'#FFFFFF','#BBBBBB')
ax.text(50,92,'trebol-backend-monolith   -   ONE deployable WAR',ha='center',fontsize=12,fontweight='bold',color=RED)
ax.text(50,88,'all 12 business domains compiled, tested and deployed together',ha='center',fontsize=8.5,color=GREY,style='italic')
layers=[('api/controllers   -   21 controllers, 67 routes',78,LRED,RED),
        ('api/services + impl   -   16 application services',66,LAMBER,AMBER),
        ('jpa/services   -   conversion 32, crud 29, patch 28, predicates 30, sortspecs 14',54,LBLUE,BLUE),
        ('jpa/repositories   -   23 repositories, ONE flat package',42,LGREEN,GREEN),
        ('jpa/entities   -   23 entities with cross-domain associations',30,LPURPLE,PURPLE)]
for t,y,fc,ec in layers:
    box(ax,8,y,84,9,t,None,fc,ec,NAVY,8,9.5)
    if y>30: arrow(ax,(50,y),(50,y-3),color=GREY,lw=1.1)
box(ax,8,13,84,11,'ONE SHARED SCHEMA   -   22 tables, cross-domain foreign keys',
    ['any service-level change could reach any table; nothing prevented it'],'#FFFFFF',RED,RED,8,10)
arrow(ax,(50,30),(50,24),color=RED,lw=1.3)
ax.text(50,98.8,'Figure 1 - SOURCE: monolith internal structure',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'cmp1_monolith.png')

# ===== Fig 2: target internal architecture =====
fig,ax=canvas(12,7.4)
ax.text(50,98.5,'Figure 2 - TARGET: three services, each with the same internal layering',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
cols=[('Catalogue',2,LGREEN,GREEN,['7 controllers','105 sources'],'7 tables'),
      ('Identity',34.5,LBLUE,BLUE,['9 controllers','132 sources'],'7 tables'),
      ('Orders',67,LAMBER,AMBER,['7 controllers','159 sources'],'9 tables')]
for name,x,fc,ec,meta,tbl in cols:
    box(ax,x,30,31,62,name+' Service',None,fc,ec,NAVY,8,11)
    for i,(lab,h) in enumerate([('api/controllers',78),('api/services',69),('jpa/services',60),('jpa/repositories',51),('jpa/entities',42)]):
        ax.add_patch(Rectangle((x+2.5,h),26,6.5,fc='white',ec=ec,lw=1.1))
        ax.text(x+15.5,h+3.2,lab,ha='center',va='center',fontsize=7.6,color=GREY)
    ax.text(x+15.5,35,' | '.join(meta),ha='center',fontsize=7.4,color=GREY,style='italic')
    box(ax,x+3,16,25,9,'own schema',[tbl],'#FFFFFF',ec,ec,7.6,9)
    arrow(ax,(x+15.5,30),(x+15.5,25),color=ec,lw=1.2)
box(ax,20,4,60,8,'trebol-shared-contracts   -   generated authority catalogue only',None,LPURPLE,PURPLE,PURPLE,7.6,9)
arrow(ax,(65,60),(72,60),'contracts over HTTP',color=PURPLE,rad=0.0,fs=7.2)
arrow(ax,(33,55),(26,55),'contracts over HTTP',color=PURPLE,rad=0.0,fs=7.2,style='<|-')
ax.text(50,27,'The layering is unchanged. What changed is that each stack now terminates in a schema only it can reach.',
        ha='center',fontsize=8,color=NAVY,style='italic')
save(fig,'cmp2_target.png')

# ===== Fig 3: domain to service mapping =====
fig,ax=canvas(12,6.6)
ax.text(50,97,'Figure 3 - Domain to service mapping: 12 domains to 3 services',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
doms=['product','product-category','product-list','image','company-profile',
      'person','user','customer','salesperson',
      'order','billing','shipper']
owner={'product':0,'product-category':0,'product-list':0,'image':0,'company-profile':0,
       'person':1,'user':1,'customer':1,'salesperson':1,'order':2,'billing':2,'shipper':2}
cols=[(14,'Catalogue',LGREEN,GREEN),(50,'Identity',LBLUE,BLUE),(86,'Orders',LAMBER,AMBER)]
for x,n,fc,ec in cols:
    box(ax,x-13,8,26,12,n+' Service',None,fc,ec,NAVY,8,10)
for i,d in enumerate(doms):
    y=88-i*6.4
    ax.add_patch(Rectangle((2,y-2.2),22,4.6,fc=LGREY,ec=GREY,lw=1))
    ax.text(13,y,d,ha='center',va='center',fontsize=7.8,color=NAVY)
    tx,_,_,ec=cols[owner[d]]
    arrow(ax,(24,y),(tx,20),color=ec,lw=1.0,rad=0.12)
ax.text(62,80,'company-profile was folded into Catalogue',ha='left',fontsize=7.8,color=GREEN)
ax.text(62,75,'because it is a single read of a seeded',ha='left',fontsize=7.8,color=GREEN)
ax.text(62,70,'parameter table, not a domain of its own.',ha='left',fontsize=7.8,color=GREEN)
ax.text(62,60,'billing and shipper travel WITH order:',ha='left',fontsize=7.8,color=AMBER)
ax.text(62,55,'they are written only by checkout and',ha='left',fontsize=7.8,color=AMBER)
ax.text(62,50,'have no independent consumer.',ha='left',fontsize=7.8,color=AMBER)
ax.text(62,40,'product, category, list and image had to',ha='left',fontsize=7.8,color=BLUE)
ax.text(62,35,'move as ONE service - they reference each',ha='left',fontsize=7.8,color=BLUE)
ax.text(62,30,'other through non-nullable keys and joins.',ha='left',fontsize=7.8,color=BLUE)
save(fig,'cmp3_domains.png')

# ===== Fig 4: file structure trees =====
fig,ax=canvas(13,8.0)
ax.text(50,99,'Figure 4 - Repository file structure: source and target',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
src="""Base_repo_JDK_17/
└── trebol-backend-monolith/        <- ONE module
    ├── pom.xml
    ├── README.md  LICENSE
    ├── CHANGELOG.md  CONTRIBUTING.md
    ├── .editorconfig  .gitignore
    ├── .github/
    │   ├── workflows/build.yml
    │   ├── ISSUE_TEMPLATE/  (3)
    │   └── PULL_REQUEST_TEMPLATE.md
    ├── schema.png
    ├── business-rules-extraction/     (154)
    ├── detailed-business-specification/ (86)
    ├── microservices-conversion/       (16)
    └── src/
        ├── main/java/org/trebol/
        │   ├── api/  controllers 21, models 24
        │   ├── jpa/  entities 23, repositories 23
        │   ├── config/  security/  common/
        │   ├── mailing/  payment/
        │   └── (43 packages, 316 sources)
        ├── main/resources/
        │   ├── schema-mariadb.sql   22 tables
        │   ├── data.sql
        │   └── application.properties
        └── test/java/   132 test sources"""
tgt="""Base_repo_JDK_17/
├── pom.xml                         <- aggregator
├── .gitignore
├── business-rules-extraction/        (154)
├── detailed-business-specification/  (86)
├── microservices-conversion/         (16)
├── documentation/                    <- new
└── trebol-services/
    ├── shared-contracts/
    │   └── src/main/java/.../AuthorityCodes.java
    ├── catalogue-service/
    │   ├── pom.xml
    │   └── src/main/{java 105, resources}
    │       └── schema-mariadb.sql   7 tables
    ├── identity-service/
    │   ├── pom.xml
    │   └── src/main/{java 132, resources}
    │       └── schema-mariadb.sql   7 tables
    └── orders-service/
        ├── pom.xml
        └── src/main/{java 159, resources}
            └── schema-mariadb.sql   9 tables

NOT carried across:
  README  LICENSE  CHANGELOG  CONTRIBUTING
  .editorconfig  .github/  schema.png"""
for x,title,txt,ec,fc in ((2,'SOURCE  -  before',src,RED,LRED),(51,'TARGET  -  after',tgt,GREEN,LGREEN)):
    ax.add_patch(FancyBboxPatch((x,4),47,90,boxstyle="round,pad=0.4,rounding_size=1.5",fc=fc,ec=ec,lw=1.6))
    ax.text(x+23.5,91,title,ha='center',fontsize=10.5,fontweight='bold',color=ec)
    ax.text(x+1.6,87,txt,ha='left',va='top',fontsize=6.9,color='#20252B',family='DejaVu Sans Mono',linespacing=1.42)
save(fig,'cmp4_filetree.png')

# ===== Fig 5: source distribution by layer =====
rows=json.load(open('/tmp/pkgmap.json'))
def g(pref):
    return sum(r['mono'] for r in rows if r['pkg'].startswith(pref)), sum(r['tot'] for r in rows if r['pkg'].startswith(pref))
groups=[('api/controllers','api/controllers'),('api/models','api/models'),('api/services','api/services'),
        ('jpa/entities','jpa/entities'),('jpa/repositories','jpa/repositories'),('jpa/services','jpa/services'),
        ('jpa/sortspecs','jpa/sortspecs'),('config','config'),('security','security'),
        ('mailing','mailing'),('payment','payment'),('common','common')]
labels=[]; mono=[]; tgt=[]
for lab,pref in groups:
    m,t=g(pref); labels.append(lab); mono.append(m); tgt.append(t)
fig,axb=plt.subplots(figsize=(11,5.4))
y=range(len(labels)); h=0.38
axb.barh([i+h/2 for i in y],mono,height=h,color=RED,label='Monolith (316 sources)')
axb.barh([i-h/2 for i in y],tgt,height=h,color=GREEN,label='Three services combined (397 sources)')
axb.set_yticks(list(y)); axb.set_yticklabels(labels,fontsize=9)
axb.invert_yaxis(); axb.legend(fontsize=9,loc='lower right'); axb.set_xlabel('Java source files',fontsize=9)
axb.set_title('Figure 5 - Source files by layer: monolith versus the three services combined',
              fontsize=11.5,fontweight='bold',color=NAVY,pad=12)
for s in ('top','right'): axb.spines[s].set_visible(False)
axb.grid(axis='x',ls=':',alpha=.5)
for i,(m,t) in enumerate(zip(mono,tgt)):
    axb.text(m+1.2,i+h/2,str(m),va='center',fontsize=7.6,color=RED)
    axb.text(t+1.2,i-h/2,str(t),va='center',fontsize=7.6,color=GREEN)
fig.text(0.5,-0.02,'The combined total is HIGHER than the monolith because the shared kernel - config, exception types, generic CRUD bases - is '
         'duplicated per service.\nThat duplication is the deliberate price of independent deployability. Note common/ falls: the two dead '
         'phone-number validator classes were not carried across.',
         ha='center',fontsize=7.8,color=GREY,style='italic')
fig.savefig('/tmp/figs2/cmp5_distribution.png',dpi=200,bbox_inches='tight',facecolor='white'); plt.close(fig); print('wrote cmp5')
