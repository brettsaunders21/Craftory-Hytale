const WALK=[
["Tier 1: Scrapjank (sealed goblin machines)"],
["Gather Scrap. It's the goblin currency for almost everything in Tier 1."],
["Find Grizzlecog, the goblin scrap peddler, and trade Scrap for his sealed machines. Start with a Squeak-crank (16 Scrap), a Fizz canister (20) and Gutterpipe (8 for 6)."],
["Buy a Tinker's Notebook (5 Scrap). It tracks the eight pages your crew needs for Tier 2."],
["Place the Squeak-crank, run Gutterpipe to a Fizz canister and use the crank to make Fizz. A Boomshroom burner (24 Scrap) makes Fizz from fuel without cranking."],
["Power a Scorch oven (smelting) and a Gnasher pit, which grinds ore into twice the grit."],
["Wear Squint goggles to see what each machine and network is doing: Fizz, grade and Jank."],
["Optional: craft a Wakebell Anchor (6 Iron Bar + 1 Fizz canister) to keep nearby machines running while you're away. Machines pause in areas the server isn't ticking."],
["Tier 2: Tinkered (learn to bodge)"],
["Buy a Cracked Tinker's Wrench (15 Scrap). Secondary-use a machine you haven't studied yet to take it apart: you lose the machine but your crew learns its Notebook page. Once you know the page, the wrench packs the machine back into an item instead."],
["Learn all six machine pages: Burner, Crank, Canister, Gutterpipe, Gnasher and Oven. Each page also lets you craft that machine at a Workbench (Tinkering bench) instead of buying it."],
["Buy Grizzlecog's Receipt (20 Scrap) and use it to record the delivery job page."],
["Survive a Scrap Rush. Goblins try to carry off one of your crew's running machines and stall it. Defeat the raid to earn the Scrap Rush page."],
["Build a Junk Heart: place a Junk Heart firebox, wrap it in Scrap plating, stack a chimney, hit the frame with a Tinker's Hammer and kick it awake. All eight pages plus a Junk Heart unlock Tier 2."],
["Fit bodge parts (sneak-use a part on a machine; two slots each): Boom Valve for speed, Gutter Gear for efficiency, Scrap Plating for less Jank, a Muffler for quiet and a Squawk Box for alerts. Use Scrap on a machine to bring its Jank down."],
["Automate: a Snatcher Claw pulls items into Gutterpipe, and paint daubs mark branches for sorting. Sloshtanks and a Gutter Boiler make Pressurised Fizz, stored in a Gasbag. Hurl Tubes and Stations carry players between named stops."],
["Tier 3: Sparkbound (crystal meets scrap)"],
["Buy a Glimmer Lens, a Gob-Jar and a Codex from Grizzlecog. Glimmerwells are born in thunderstorms, in rain and from mob kills."],
["Scan a Well with the Lens by locking its three resonance rings. Scans earn Insight, shared by your crew and tracked in the Codex."],
["Catch a Flicker Well in a Gob-Jar."],
["Run a Boomshroom burner or a burning Junk Heart within 8 blocks of a Cyan or Void crystal. Your machine whines, the crystal answers (First Spark), and Vessa Lanternwright arrives."],
["Craft a Glimmerwell Cradle, seat your Well above it and surround it with blocks matching its taste to raise Harmony. Add a Crackle Coil on a burning machine and tune a Glimmer Resonator to turn Fizz into Dim Glimmer."],
["Storm Hunt: Storm Vanes improve your odds of a Steady Well. Calm a Wild Well by beating its Sparkjaw guardian or with three powered Fizz Dampers. Tether it with three evenly spaced stakes, jar it with three timed presses and escort it home before its Stability runs out. Poachers may ambush you on the way."]];
const CATS={power:"Power",machine:"Machines",logistics:"Logistics",fluid:"Fluids",tool:"Tools",bodge:"Bodge parts",well:"Wells",material:"Materials",npc_item:"Other"};
const $=s=>document.querySelector(s),el=(t,c,h)=>{const e=document.createElement(t);if(c)e.className=c;if(h!=null)e.innerHTML=h;return e};
const esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c])).replace(/`([^`]+)`/g,"<code>$1</code>");
const ico=i=>i.icon?`<img src="img/${i.icon}.png" alt="">`:`<span class="ph" aria-hidden="true"></span>`;
const NOSRC=/No survival source/i;
fetch("guide.json").then(r=>r.json()).then(d=>{
 const L=d.lore;$("#intro").textContent=L.intro;
 L.beats.forEach(b=>$("#beats").append(el("div","beat",`<small>Tier ${b.tier}</small><h3>${esc(b.title)}</h3><p>${esc(b.text)}</p>`)));
 L.cast.forEach(c=>{const n=(d.npcs||[]).find(x=>x.name.startsWith(c.name.split(" ")[0]));$("#cast").append(el("div","",`<h4>${esc(c.name)}</h4><p>${esc(c.text)}</p>${n?`<p class="sub">Where: ${esc(n.where)}</p>`:""}`))});
 const done=new Set(JSON.parse(localStorage.getItem("craftory-walk")||"[]"));
 WALK.forEach((w,i)=>{if(w.length===1&&/^Tier/.test(w[0])){$("#walkList").append(el("li","tier",esc(w[0])));return}
  const li=el("li",done.has(i)?"done":"",`<input type="checkbox" id="w${i}" ${done.has(i)?"checked":""}><span><label for="w${i}">${esc(w[0])}</label></span>`);
  li.querySelector("input").onchange=e=>{e.target.checked?done.add(i):done.delete(i);li.classList.toggle("done",e.target.checked);localStorage.setItem("craftory-walk",JSON.stringify([...done]))};$("#walkList").append(li)});
 const items=d.items.filter(i=>!/^Craftory_Grit_/.test(i.id)).concat(d.items.filter(i=>/^Craftory_Grit_Iron$/.test(i.id)));
 let cat="all",q="",cur=items[0].id;
 const cats=["all",...new Set(items.map(i=>i.category))];
 cats.forEach(c=>{const b=el("button","chip"+(c==="all"?" on":""),c==="all"?"All":CATS[c]||c);b.onclick=()=>{cat=c;document.querySelectorAll("#wf .chip").forEach(x=>x.classList.toggle("on",x===b));list()};$("#wf").append(b)});
 $("#q").oninput=e=>{q=e.target.value.toLowerCase();list()};
 function detail(i){const nos=(i.obtain||[]).some(o=>NOSRC.test(o));
  $("#wikiDetail").innerHTML=`<header>${ico(i)}<div><h3>${esc(i.name)}</h3><span class="pill">${esc(CATS[i.category]||i.category)}</span></div></header>
  <h4>What it does</h4><p>${esc(i.does)}</p>
  <h4>How to get it</h4><ul>${(i.obtain||[]).map(o=>`<li>${esc(o)}</li>`).join("")}</ul>${nos?`<div class="warn">Not obtainable in survival yet. A recipe is coming in the early-tier polish update.</div>`:""}
  ${i.use?.length?`<h4>How to use it</h4><ol>${i.use.map(o=>`<li>${esc(o)}</li>`).join("")}</ol>`:""}
  ${i.tips?.length?`<h4>Tips</h4><ul>${i.tips.map(o=>`<li>${esc(o)}</li>`).join("")}</ul>`:""}`}
 function list(){const ul=$("#wikiList");ul.innerHTML="";
  const f=items.filter(i=>(cat==="all"||i.category===cat)&&(!q||(i.name+" "+i.does+" "+(i.obtain||[]).join(" ")).toLowerCase().includes(q)));
  f.forEach(i=>{const b=el("button","",`${ico(i)}<span>${esc(i.name)}</span>`);b.setAttribute("aria-current",i.id===cur);b.onclick=()=>{cur=i.id;list();detail(i)};const li=el("li");li.append(b);ul.append(li)});
  if(!f.length)ul.append(el("li","sub","No matches."))}
 list();detail(items[0]);
 (d.systems||[]).filter(s=>s.key!=="commands").forEach(s=>{const x=el("details","",`<summary>${esc(s.title)}</summary><ul>${s.facts.map(f=>`<li>${esc(f)}</li>`).join("")}</ul>`);$("#sys").append(x)});
 $("#wellsIntro").textContent=L.wells;const W=d.well_reference||{};
 const kv=(o)=>`<div class="kv">${o.color?`<i class="sw" style="background:${o.color}"></i>`:""}<b>${esc(o.name)}</b> ${esc(o.description)}</div>`;
 (W.strengths||[]).forEach(s=>$("#str").insertAdjacentHTML("beforeend",kv(s)));(W.states||[]).forEach(s=>$("#states").insertAdjacentHTML("beforeend",kv(s)));(W.temperaments||[]).forEach(s=>$("#temps").insertAdjacentHTML("beforeend",kv(s)));if(W.note)$("#temps").insertAdjacentHTML("beforeend",`<p class="sub">${esc(W.note)}</p>`);
 (d.mobs||[]).concat((d.npcs||[]).map(n=>({name:n.name,where:n.where,behaviour:n.role,drops:(n.dialogue||[]).length?"Says: "+n.dialogue.slice(0,2).join(" / "):""}))).forEach(m=>$("#mobGrid").append(el("article","card",`<h3>${esc(m.name)}</h3><p><b>Where:</b> ${esc(m.where)}</p><p>${esc(m.behaviour)}</p>${m.drops?`<p class="sub"><b>Drops:</b> ${esc(m.drops)}</p>`:""}`)));
 $("#cmdT").innerHTML="<tr><th>Command</th><th>Who</th><th>What it does</th></tr>"+(d.commands||[]).map(c=>`<tr><td><code>${esc(c.cmd)}</code></td><td>${esc(c.who)}</td><td>${esc(c.does)}</td></tr>`).join("");
});
