import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle
plt.rcParams['font.family']='DejaVu Sans'

NAVY='#1F3864'; BLUE='#2E74B5'; LBLUE='#DEEAF6'; GREY='#595959'; LGREY='#F2F2F2'
GREEN='#2E7D32'; LGREEN='#E8F5E9'; AMBER='#B26A00'; LAMBER='#FFF3E0'
RED='#A61C1C'; LRED='#FDECEA'; PURPLE='#5B2C8D'; LPURPLE='#EDE7F6'

def canvas(w,h):
    fig,ax=plt.subplots(figsize=(w,h)); ax.set_xlim(0,100); ax.set_ylim(0,100)
    ax.axis('off'); return fig,ax

def box(ax,x,y,w,h,title,lines=None,fc=LBLUE,ec=BLUE,tc=NAVY,fs=9,tfs=10,r=0.02):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle=f"round,pad=0.4,rounding_size={r*100}",
                 fc=fc,ec=ec,lw=1.6))
    ax.text(x+w/2,y+h-(3.0 if lines else h/2-1.2),title,ha='center',
            va='top' if lines else 'center',fontsize=tfs,fontweight='bold',color=tc)
    if lines:
        for i,l in enumerate(lines):
            ax.text(x+w/2,y+h-8.0-i*4.3,l,ha='center',va='top',fontsize=fs,color=GREY)

def arrow(ax,p1,p2,label=None,color=BLUE,style='-|>',ls='-',rad=0.0,lw=1.5,fs=8,off=(0,2.2)):
    ax.add_patch(FancyArrowPatch(p1,p2,arrowstyle=style,mutation_scale=14,
                 color=color,lw=lw,linestyle=ls,connectionstyle=f"arc3,rad={rad}",
                 shrinkA=2,shrinkB=2))
    if label:
        mx,my=(p1[0]+p2[0])/2+off[0],(p1[1]+p2[1])/2+off[1]
        ax.text(mx,my,label,ha='center',va='bottom',fontsize=fs,color=color)

def save(fig,name):
    fig.savefig(f'/tmp/figs/{name}',dpi=200,bbox_inches='tight',facecolor='white')
    plt.close(fig); print('wrote',name)

# ---------- Fig 1: target architecture / system context ----------
fig,ax=canvas(13,8.2)
box(ax,32,88,36,10,'Client applications',['Browser SPA  |  anonymous and authenticated traffic'],LGREY,GREY,NAVY)
svc=[(2,'Catalogue Service','8081',['26 routes  /  7 tables','products, categories,','lists, images, company'],LGREEN,GREEN),
     (35.5,'Identity Service','8082',['23 routes  /  7 tables','people, accounts, roles,','customers, salespeople'],LBLUE,BLUE),
     (69,'Orders Service','8083',['20 routes  /  9 tables','orders, lines, status,','checkout, billing, shipper'],LAMBER,AMBER)]
for x,name,port,lines,fc,ec in svc:
    box(ax,x,48,29,30,name,[f'HTTP :{port}']+lines,fc,ec,NAVY,8.5,11)
    arrow(ax,(x+14.5,88),(x+14.5,78),color=GREY,lw=1.2)
    box(ax,x+3,30,23,11,'MariaDB',['own schema, exclusive'],'#FFFFFF',GREY,GREY,8,9)
    arrow(ax,(x+14.5,48),(x+14.5,41),color=GREY,lw=1.2)
box(ax,20,14,60,10,'trebol-shared-contracts  (plain library jar, no Spring)',
    ['AuthorityCodes - 54 generated authority codes, consumed by all three services'],LPURPLE,PURPLE,PURPLE,8.5,10)
for x,_,_,_,_,_ in svc: arrow(ax,(x+14.5,48),(x+30 if x<40 else x+5,24),color=PURPLE,ls=':',lw=1.1,rad=0.08)
box(ax,70.5,2,27.5,8,'Transbank Webpay Plus',['outbound, payment'],LRED,RED,RED,8,9)
box(ax,40,2,27,8,'Mailgun',['outbound, optional'],LRED,RED,RED,8,9)
arrow(ax,(84,30),(84,10),color=RED,lw=1.2)
arrow(ax,(76,30),(58,10),color=RED,lw=1.2,rad=-0.1)
ax.text(50,99.6,'Figure 1 - Target architecture: three independently deployable services',
        ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig01_architecture.png')

# ---------- Fig 2: before / after ----------
fig,ax=canvas(13,6.4)
ax.text(25,97,'BEFORE',ha='center',fontsize=11,fontweight='bold',color=RED)
ax.text(75,97,'AFTER',ha='center',fontsize=11,fontweight='bold',color=GREEN)
box(ax,4,56,42,34,'trebol-backend-monolith  (one WAR)',
    ['21 controllers  -  67 routes','316 main sources, 132 test sources','all 12 domains in one deployable'],LRED,RED,NAVY,8.5,10.5)
box(ax,9,26,32,18,'Shared schema',['22 tables, one database','cross-domain foreign keys'],'#FFFFFF',RED,RED,8.5,10)
arrow(ax,(25,56),(25,44),color=RED)
for i,(n,fc,ec) in enumerate([('Catalogue',LGREEN,GREEN),('Identity',LBLUE,BLUE),('Orders',LAMBER,AMBER)]):
    y=72-i*18
    box(ax,54,y,20,14,n,None,fc,ec,NAVY,8,10)
    box(ax,78,y,19,14,'own schema',[f'{[7,7,9][i]} tables'],'#FFFFFF',ec,ec,8,9)
    arrow(ax,(74,y+7),(78,y+7),color=ec,lw=1.2)
ax.text(75.5,13,'no cross-service foreign key  -  no shared database',ha='center',fontsize=8.5,color=GREEN)
ax.text(75.5,8,'each service builds, tests and deploys on its own',ha='center',fontsize=8.5,color=GREEN)
ax.add_patch(FancyArrowPatch((47,52),(52,52),arrowstyle='-|>',mutation_scale=22,color=NAVY,lw=2.2))
ax.text(50,99.6,'Figure 2 - Decomposition: from one deployable to three',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig02_before_after.png')

# ---------- Fig 3: data ownership ----------
fig,ax=canvas(13,6.0)
groups=[('Catalogue Service - 7 tables',['products','product_categories','product_lists','product_list_items','product_images','images','app_params'],LGREEN,GREEN,3),
        ('Identity Service - 7 tables',['people','app_users','app_user_roles','app_permissions','app_user_role_permissions','customers','salespeople'],LBLUE,BLUE,35.5),
        ('Orders Service - 9 tables',['orders','order_details','order_statuses','addresses','billing_types','billing_companies','payment_types','shippers','order_mail_outbox'],LAMBER,AMBER,68)]
for title,tables,fc,ec,x in groups:
    box(ax,x,8,29,78,title,None,fc,ec,NAVY,8,10)
    for i,t in enumerate(tables):
        ax.add_patch(Rectangle((x+2.5,78-i*8),24,6,fc='white',ec=ec,lw=1.1))
        ax.text(x+14.5,81-i*8,t,ha='center',va='center',fontsize=8.2,color=GREY,family='DejaVu Sans Mono')
ax.text(50,2.5,'order_mail_outbox is new infrastructure (register item D11). The other 22 are the tables of record.',
        ha='center',fontsize=8,color=GREY,style='italic')
ax.text(50,95,'Figure 3 - Data ownership: 23 tables, each owned by exactly one service',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig03_data_ownership.png')

# ---------- Fig 4: contract seams ----------
fig,ax=canvas(13,6.6)
box(ax,37,40,26,20,'Orders Service',['the only consumer of','cross-service data'],LAMBER,AMBER,NAVY,8.5,11)
box(ax,2,72,30,16,'Catalogue Service',None,LGREEN,GREEN,NAVY,8,10)
box(ax,68,72,30,16,'Identity Service',None,LBLUE,BLUE,NAVY,8,10)
arrow(ax,(44,60),(20,72),'ProductLookupContract\nHTTP  GET /data/products?barcode',color=GREEN,rad=-0.12,fs=7.5,off=(-6,1))
arrow(ax,(56,60),(80,72),'CustomerProvisioningContract\nSalespersonLookupContract\nHTTP  /data/customers, /data/salespeople',color=BLUE,rad=0.12,fs=7.5,off=(7,1))
box(ax,6,12,38,18,'LOCAL seams  (own repositories)',['BillingContract  -  billing types, payment types,','resolve-or-create billing company','ShipperLookupContract  -  shippers'],'#FFFFFF',AMBER,AMBER,7.8,9)
box(ax,56,12,38,18,'REMOTE seams  (RestClient + token relay)',['caller bearer token is relayed, so the hop gets','the same authorisation a direct call would','exchange business keys, never entities'],'#FFFFFF',PURPLE,PURPLE,7.8,9)
arrow(ax,(43,44),(30,30),color=AMBER,lw=1.2,rad=0.1)
arrow(ax,(57,44),(70,30),color=PURPLE,lw=1.2,rad=-0.1)
ax.text(50,4,'Keys crossing the boundary are BUSINESS keys - product barcode, person id number - because neither sibling exposes a surrogate id.',
        ha='center',fontsize=7.8,color=GREY,style='italic')
ax.text(50,97,'Figure 4 - Cross-service contract seams',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig04_contracts.png')

# ---------- Fig 5: authentication ----------
fig,ax=canvas(13,6.2)
box(ax,2,70,28,20,'Client',['POST /public/login','POST /public/guest'],LGREY,GREY,NAVY,8,10)
box(ax,36,70,28,20,'Identity Service',['SOLE token ISSUER','signs with shared key'],LBLUE,BLUE,NAVY,8,10)
arrow(ax,(30,80),(36,80),'credentials',color=GREY,fs=8)
arrow(ax,(50,70),(50,58),'signed JWT',color=BLUE,fs=8,off=(7,0))
box(ax,30,44,40,12,'JWT  -  authorities embedded as claims',None,'#FFFFFF',BLUE,BLUE,8,9.5)
for i,(n,fc,ec) in enumerate([('Catalogue Service',LGREEN,GREEN),('Identity Service',LBLUE,BLUE),('Orders Service',LAMBER,AMBER)]):
    x=2+i*33.5
    box(ax,x,14,31,20,n,['VERIFIES locally','no issuing machinery','no password store'],fc,ec,NAVY,7.8,9.5)
    arrow(ax,(50,44),(x+15.5,34),color=BLUE,ls=':',lw=1.2,rad=0.0)
ax.text(50,7,'ADR-002: every service verifies the signature locally with the shared key. No service calls Identity to validate a token.',
        ha='center',fontsize=8,color=GREY,style='italic')
ax.text(50,3,'Authorisation is method-level @PreAuthorize against the 54-code generated catalogue. No request-matcher rules anywhere.',
        ha='center',fontsize=8,color=GREY,style='italic')
ax.text(50,97,'Figure 5 - Authentication and authorisation (ADR-002)',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig05_auth.png')

# ---------- Fig 6: checkout + callback ----------
fig,ax=canvas(13,7.6)
lanes=[('Client',8),('Orders Service',30),('Identity /\nCatalogue',52),('Transbank',74),('Outbox',92)]
for name,x in lanes:
    ax.text(x,96,name,ha='center',fontsize=8.5,fontweight='bold',color=NAVY)
    ax.plot([x,x],[8,92],color='#CCCCCC',lw=1,ls='--',zorder=0)
steps=[(8,30,'1  POST /public/checkout  (guest token, authority "checkout")',88,GREY),
       (30,52,'2  resolve product by barcode / customer by id number',80,BLUE),
       (30,30,'3  create order, status Pending -> Payment Started, generate NONCE',72,AMBER),
       (30,74,'4  request payment page, callback URL carries the nonce',64,RED),
       (74,8,'5  redirect customer to Webpay payment page',56,RED),
       (74,30,'6  GET/POST /public/checkout/validate?nonce=...  (anonymous)',48,RED),
       (30,30,'7  verify nonce; if already resolved, no-op (idempotent)',40,AMBER),
       (30,30,'8  status -> Paid Unconfirmed  (inside one transaction)',32,AMBER),
       (30,92,'9  enqueue mail in the SAME transaction',24,PURPLE),
       (92,92,'10  scheduled dispatcher sends later, bounded retry',16,PURPLE)]
for a,b,label,y,c in steps:
    if a==b:
        ax.add_patch(FancyBboxPatch((a-9,y-2.6),18,5.2,boxstyle="round,pad=0.3,rounding_size=1",fc='white',ec=c,lw=1.3))
        ax.text(a,y,'self',ha='center',va='center',fontsize=7,color=c)
        ax.text(a+11,y,label,ha='left',va='center',fontsize=8,color=c)
    else:
        arrow(ax,(a,y),(b,y),color=c,lw=1.4)
        ax.text((a+b)/2,y+1.6,label,ha='center',va='bottom',fontsize=8,color=c)
ax.text(50,10,'A payment-infrastructure failure now raises an error and leaves the order at Payment Started (D3),',ha='center',fontsize=7.8,color=GREY,style='italic')
ax.text(50,6.5,'so the later legitimate callback is still accepted. The nonce authenticates the callback and makes replays safe (D4).',ha='center',fontsize=7.8,color=GREY,style='italic')
ax.text(50,99.6,'Figure 6 - Checkout and payment callback flow',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig06_checkout.png')

# ---------- Fig 7: order status machine ----------
fig,ax=canvas(13,6.4)
pos={'Pending':(8,74),'Payment Started':(32,74),'Payment Cancelled':(60,90),
     'Payment Failed':(60,74),'Paid Unconfirmed':(60,56),'Paid Confirmed':(84,66),
     'Rejected':(84,44),'Completed':(84,86)}
term={'Payment Cancelled','Payment Failed','Rejected','Completed'}
for n,(x,y) in pos.items():
    fc,ec=(LRED,RED) if n in term else (LGREEN,GREEN)
    ax.add_patch(FancyBboxPatch((x-7.5,y-4),15,8,boxstyle="round,pad=0.3,rounding_size=1.5",fc=fc,ec=ec,lw=1.5))
    ax.text(x,y,n.replace(' ','\n',1),ha='center',va='center',fontsize=7.4,color=NAVY,fontweight='bold')
edges=[('Pending','Payment Started'),('Payment Started','Payment Cancelled'),('Payment Started','Payment Failed'),
       ('Payment Started','Paid Unconfirmed'),('Paid Unconfirmed','Paid Confirmed'),('Paid Unconfirmed','Rejected'),
       ('Paid Confirmed','Completed')]
for a,b in edges:
    arrow(ax,pos[a],pos[b],color=GREEN,lw=1.4,rad=0.05)
box(ax,4,10,44,22,'UNREACHABLE - register item D10',
    ['Returned   |   Delivery Failed','Delivery Cancelled   |   Delivery On Route',
     'Seeded in the database but reached by no code.','Now rejected as transition targets.'],LGREY,GREY,GREY,7.6,9)
box(ax,54,10,44,22,'Enforcement',
    ['One transition table is the single source of truth.','All 7 markAs* preconditions consult it (D5).',
     'Status is NOT patchable: the mapping is','updatable = false, so a patch is a silent no-op.'],'#FFFFFF',AMBER,AMBER,7.6,9)
ax.text(50,97,'Figure 7 - Order status machine: 8 reachable statuses, 7 legal transitions, 4 terminal',ha='center',fontsize=11,fontweight='bold',color=NAVY)
save(fig,'fig07_status_machine.png')

# ---------- Fig 8: outbox ----------
fig,ax=canvas(13,5.0)
box(ax,2,58,30,30,'OrdersProcessServiceImpl',['@Transactional','status transition'],LAMBER,AMBER,NAVY,8,9.5)
box(ax,37,58,26,30,'order_mail_outbox',['entry written in the','SAME transaction'],LPURPLE,PURPLE,NAVY,8,9.5)
box(ax,68,58,30,30,'MailOutboxDispatcher',['@Scheduled','bounded retry','per-entry isolation'],LPURPLE,PURPLE,NAVY,8,9.5)
arrow(ax,(32,73),(37,73),'enqueue\nMANDATORY',color=PURPLE,fs=7.5,off=(0,1))
arrow(ax,(63,73),(68,73),'poll pending',color=PURPLE,fs=7.5)
box(ax,68,18,30,26,'Mailgun adapter',['optional - absent bean','means no mail, no error'],LRED,RED,RED,7.8,9.5)
arrow(ax,(83,58),(83,44),color=RED,lw=1.2)
box(ax,2,18,60,26,'Why MANDATORY propagation',
    ['The enqueue must happen inside the transition transaction so the entry and the','status change commit or roll back together. Putting it in the controller - which is',
     'not transactional - would reproduce the original defect one layer out. Propagation','MANDATORY makes a caller outside a transaction fail fast instead of silently opening one.'],LGREY,GREY,GREY,7.6,9)
ax.text(50,96,'Figure 8 - Transactional mail outbox (register item D11)',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig08_outbox.png')

# ---------- Fig 9: build structure ----------
fig,ax=canvas(13,5.4)
box(ax,32,80,36,14,'trebol-platform  (pom)',['aggregator - reactor root'],LGREY,GREY,NAVY,8,10)
box(ax,32,56,36,16,'trebol-shared-contracts',['plain library jar, no Spring','builds FIRST'],LPURPLE,PURPLE,NAVY,8,10)
arrow(ax,(50,80),(50,72),color=GREY,lw=1.3)
for i,(n,fc,ec) in enumerate([('catalogue-service',LGREEN,GREEN),('identity-service',LBLUE,BLUE),('orders-service',LAMBER,AMBER)]):
    x=2+i*33.5
    box(ax,x,24,31,22,n,['Spring Boot executable jar'],fc,ec,NAVY,7.8,9.5)
    arrow(ax,(50,56),(x+15.5,46),color=PURPLE,ls=':',lw=1.2)
ax.text(50,16,'. ~/opt/env.sh && mvn -B -fae clean verify     from the repository root',
        ha='center',fontsize=9,color=NAVY,family='DejaVu Sans Mono')
ax.text(50,9,'495 tests  -  0 failures  -  0 errors  -  BUILD SUCCESS  -  approx 10 minutes',ha='center',fontsize=8.5,color=GREEN,fontweight='bold')
ax.text(50,4,'Do not add -o: the build resolves plugins from the network. Java 21, Maven 3.9.',ha='center',fontsize=7.8,color=GREY,style='italic')
ax.text(50,97,'Figure 9 - Build and module structure',ha='center',fontsize=11.5,fontweight='bold',color=NAVY)
save(fig,'fig09_build.png')
