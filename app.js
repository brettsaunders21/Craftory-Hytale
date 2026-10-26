const TIERS=[
 {n:1,era:"Goblin",name:"Scrapjank",status:"ok",energy:"Fizz",unlock:"Where you start. Buy your first parts from Grizzlecog the goblin trader.",
  fantasy:"Sealed goblin junk machines that clank, smoke and occasionally fizzle.",
  items:["Crank-a-Fizz","Boomshroom Burner","Rattlewheel","Shroom Bed","Fizz Canister","Scorch Oven","Gnasher Pit","Squint Goggles","Wakebell Anchor","Tinker's Wrench"]},
 {n:2,era:"Goblin",name:"Tinkered",status:"ok",energy:"Fizz / Pressurised Fizz",unlock:"Find all 8 Tinkered Notebook pages with your crew, then hammer together a Junk Heart.",
  fantasy:"A ramshackle factory: multi-block builds, pipes you can see, bodge upgrades and goblin raids.",
  items:["Junk Heart","Tinker's Hammer","Tinker's Notebook","Bodge parts","Gutterpipe","Snatcher Claw","Sloshtank","Gutter Boiler","Gasbag","Hurl Tubes","Hurl Cannon"]},
 {n:3,era:"Hybrid",name:"Sparkbound",status:"ok",energy:"Dim Glimmer",unlock:"Run the First Spark experiment: a working Tinkered machine next to Cyan or Void crystals. Vessa arrives. Then catch your first Flicker in a Gob-Jar and seat it in a Well Cradle.",
  fantasy:"Crystals wired into goblin machines, and the hunt for living Glimmerwells begins.",
  items:["Glimmer Lens","Gob-Jar","Codex","Well Cradle","Crackle Coil","Resonator","Storm Vane","Fizz Damper","Tether Stake","Wellhead"]},
 {n:4,era:"Hybrid",name:"Glimmerforged",status:"later",energy:"Bright Glimmer",unlock:"Seat your first Steady Well and build the Heart of the Machine.",fantasy:"The Gobliterator Core power plant, Sparklines and Prism Grinders.",items:[]},
 {n:5,era:"Hybrid",name:"Resonant",status:"later",energy:"Bright Glimmer with Tint",unlock:"Seat three Wells of different Temperaments.",fantasy:"Resonance Forge, Tinkling helper golems and Void Bleed.",items:[]},
 {n:6,era:"Arcane",name:"Arcanum",status:"later",energy:"Radiant Glimmer",unlock:"Coming later.",fantasy:"Floating arcane machines, Infusion Circles and ley streams.",items:[]},
 {n:7,era:"Arcane",name:"Ascendant",status:"later",energy:"Prismatic Glimmer",unlock:"Coming later.",fantasy:"Taming the Void: Veil Engine, Rift Gates and the Hollowmere.",items:[]}
];
const M=[
 {n:"Crank-a-Fizz",i:"Crank_A_Fizz",t:1,c:"power",d:"Turn it by hand for your first Fizz. It only animates while it's actually producing."},
 {n:"Boomshroom Burner",i:"Boomshroom_Burner",t:1,c:"power",d:"Burns fuel for steady Fizz. Boomshrooms burn hot."},
 {n:"Rattlewheel",i:"Rattlewheel",t:1,c:"power",d:"A wind wheel. It makes more the higher it sits, and it doesn't like water nearby."},
 {n:"Shroom Bed",i:"Shroom_Bed",t:1,c:"power",d:"Grows Boomshrooms in real darkness and makes Fizz. Popping shrooms knock you back but never break blocks."},
 {n:"Fizz Canister",i:"Fizz_Canister",t:1,c:"power",d:"Stores Fizz for your network. It has its own page with charge and flow."},
 {n:"Scorch Oven",i:"Scorch_Oven",t:1,c:"machine",d:"A Fizz-powered smelter. It runs at half speed on plain Fizz and full speed on Pressurised Fizz."},
 {n:"Gnasher Pit",i:"Gnasher_Pit",t:1,c:"machine",d:"Chews ore into grit, which the Scorch Oven smelts."},
 {n:"Wakebell Anchor",i:"Wakebell_Anchor",t:1,c:"machine",d:"Fuel it to keep its area running while nobody is nearby. Admins set the maximum charge."},
 {n:"Squint Goggles",i:"Squint_Goggles",t:1,c:"tool",d:"Wear them to read machine status, Jank and a crewmate's jar Stability."},
 {n:"Tinker's Wrench",i:"Tinkers_Wrench",t:1,c:"tool",d:"Take apart a machine you don't know yet to learn its page, or pack up one you do know."},
 {n:"Tinker's Notebook",i:"Tinkers_Notebook",t:2,c:"tool",d:"Your crew's research. It shows which of the 8 Tinkered pages you've found."},
 {n:"Tinker's Hammer",i:"Tinkers_Hammer",t:2,c:"tool",d:"Hammer parts into a ghost frame, then kick-start it. Your timing sets the build quality."},
 {n:"Junk Heart",i:"Junk_Heart_Firebox",t:2,c:"power",d:"A multi-block firebox and chimney. Building it unlocks Tier 2."},
 {n:"Bodge parts",i:"Boom_Valve",t:2,c:"machine",d:"Every machine has two bodge slots. Fit Boom Valves, Mufflers, Squawk Boxes, Gutter Gears or Scrap Plating to trade speed, noise and Jank."},
 {n:"Gutterpipe",i:"Gutterpipe",t:2,c:"logistics",d:"You can see the items moving through it. It has an item lane and a fluid lane, and paint daubs sort by colour."},
 {n:"Snatcher Claw",i:null,t:2,c:"logistics",d:"Pulls items from chests and machines into pipes, with filters."},
 {n:"Sloshtank",i:null,t:2,c:"logistics",d:"Stack them into a taller tank. Fill it with a water bucket or by piping from a water source."},
 {n:"Gutter Boiler",i:null,t:2,c:"power",d:"Boils water into Pressurised Fizz and vents steam when it's full."},
 {n:"Gasbag",i:null,t:2,c:"power",d:"Stores Pressurised Fizz. Fire next to it starts a leak."},
 {n:"Hurl Tubes",i:null,t:2,c:"logistics",d:"Ride between named stations. Each ride costs a little Fizz, and only your crew can use your stations."},
 {n:"Hurl Cannon",i:null,t:2,c:"logistics",d:"Fires you through the air at a Catch Net."},
 {n:"Glimmer Lens",i:null,t:3,c:"tool",d:"Scan Wells with a three-ring resonance dial. A perfect read reveals the Well's Taste."},
 {n:"Gob-Jar",i:null,t:3,c:"tool",d:"Catch a Flicker, or a tethered Well, and carry it home."},
 {n:"Codex",i:null,t:3,c:"tool",d:"Your record of every Well Temperament you've discovered, plus your Insight."},
 {n:"Well Cradle",i:null,t:3,c:"power",d:"Seat a jarred Well at home. It rates its surroundings against its Taste for a Harmony of 1 to 5."},
 {n:"Crackle Coil",i:null,t:3,c:"power",d:"Bolts onto a Burner or Junk Heart and skims a little Dim Glimmer off the Fizz passing through: your first Glimmer."},
 {n:"Resonator",i:null,t:3,c:"power",d:"A crystal-seated converter. Tune it to between 25% and 40% conversion."},
 {n:"Storm Vane",i:"Storm_Vane",t:3,c:"well",d:"Passive. Each Vane within 64 blocks raises the chance that a storm strike births a Steady Well."},
 {n:"Fizz Damper",i:"Fizz_Damper",t:3,c:"well",d:"Three powered Dampers within 4 blocks of a Well calm it, at the cost of one strength band."},
 {n:"Tether Stake",i:"Tether_Stake",t:3,c:"well",d:"Ring a calmed Well with evenly spaced stakes to tether it and claim it for your crew."},
 {n:"Wellhead",i:null,t:3,c:"well",soon:true,d:"Tap a tethered Well where it stands, and route energy through it to upgrade its grade (catalysis)."}
];
const CATS=[["all","All"],["power","Power"],["machine","Machines"],["logistics","Logistics"],["tool","Tools"],["well","Well hunting"]];
const STEPS=[
 ["Find","Wells are born in thunderstorms, in rain and from big fights. In a storm, a strike lands on the highest block near a player, and the Well appears where it hits. Storm Vanes raise the odds that it's a Steady Well rather than a Flicker.","A region can only hold one strong Wild Well at a time, so go exploring."],
 ["Scan","Hold the Glimmer Lens on a Well. Three rings turn: Temperament, Strength and State. Lock each one as it pulses. A sloppy read still works; a perfect read reveals the Well's Taste and gives bonus Insight.","Your first scan of each Temperament is a Codex discovery your whole crew shares."],
 ["Calm","A Wild Steady Well won't sit still. Defeat the Sparkjaw guardian that rises to protect it, or ring it with three powered Fizz Dampers.","Dampers work, but they cost the Well one strength band. Fighting is the better reward."],
 ["Tether","Place three Tether Stakes 3 to 6 blocks out, spaced evenly around the Well. Chain beams lock on and the Well is claimed for your crew. A badly spaced stake leans; straighten it with the Tinker's Hammer.","Only your crew can straighten your stakes or jar your Well."],
 ["Jar","Use a Gob-Jar on the tethered Well and press three times, in rhythm. Miss and you get knocked back, and a stake starts leaning.","Co-op tip: one of you jars while the other guards."],
 ["Escort","The jar has a Stability meter. It drains slowly, drops 10 every time you're hit, and drops faster in weather the Well dislikes. Carrying a jar slows you down, Goblin Poachers ambush you, and you can't take a jarred Well into a Hurl Tube. At zero it bursts out wild where you stand.","Crewmates wearing Squint Goggles can see your jar's Stability."],
 ["Seat or Tap","At home, uncork the jar in a Well Cradle. The Well rates the area against its Taste, and its Harmony (1 to 5) sets how well it grows. Coming next: tap a Well in place with a Wellhead instead.","Build the Cradle where the Well's Taste will be happy before you set out."]
];
const TEMPS=[["Spark","#5fe3e0","Dislikes rain"],["Ember","#ff7a3d","Dislikes rain and storms"],["Tide","#4a90ff","No weather penalty"],["Growth","#6fd16b","No weather penalty"],["Stone","#b0a090","No weather penalty"],["Gale","#cfe8ff","No weather penalty"],["Clank","#e3b04b","No weather penalty"],["Hollow","#9b7bff","No weather penalty"]];
const CMDS=[["/craftory crew create","Start a crew"],["/craftory crew invite <player>","Invite a friend (crew owner only)"],["/craftory crew accept | leave","Join or leave a crew"],["/craftory config anchor-max <minutes>","Admin: maximum Anchor charge (0 disables Anchors)"],["/craftory mob spawn <role>","Admin: spawn a Craftory mob"],["/craftory selftest","Admin: run the mod's built-in self-test"]];
const ROAD=[["Tiers 1–3","ok","Goblin machines, logistics, fluids, co-op crews, the First Spark and Storm Hunt. In testing on live servers."],["Storm Hunt part 2","wip","The Wellhead and catalysis, Riftmoths corrupting Wells, and the Slurp Walker Poacher raid."],["Tier 4: Glimmerforged","later","The Coreheart Engine, Sparklines, Tint and Racket raids."],["Tier 5: Resonant","later","Resonance Forge, Tinklings and Void Bleed."],["Tiers 6–7: Arcane","later","Infusion, ley streams, the Prism Warden and the Veil Engine."],["The Hollowmere","later","Your crew's own realm of planted islands."]];
const BADGE={ok:["in testing","ok"],wip:["in progress","wip"],later:["planned","later"]};
const $=s=>document.querySelector(s),h=(t,a={},...k)=>{const e=document.createElement(t);for(const[x,v]of Object.entries(a))x==="class"?e.className=v:x.startsWith("on")?e.addEventListener(x.slice(2),v):e.setAttribute(x,v);k.flat().forEach(c=>e.append(c));return e};
function badge(s){const[b,c]=BADGE[s];return h("span",{class:"badge "+c},b)}
function showTier(i){const t=TIERS[i];[...$("#tierTabs").children].forEach((b,j)=>b.setAttribute("aria-selected",i===j));
 $("#tierCard").replaceChildren(
  h("div",{},h("h3",{},`Tier ${t.n}: ${t.name}`,badge(t.status)),h("p",{},t.fantasy),
   h("dl",{},h("dt",{},"Era"),h("dd",{},t.era),h("dt",{},"Energy"),h("dd",{},t.energy),h("dt",{},"How to unlock"),h("dd",{},t.unlock))),
  h("div",{},h("p",{class:"note"},t.items.length?"Unlocks":"Details coming with this tier"),h("div",{class:"chips"},t.items.map(x=>h("span",{class:"chip"},x)))))}
TIERS.forEach((t,i)=>$("#tierTabs").append(h("button",{role:"tab",class:t.status==="later"?"later":"",onclick:()=>showTier(i)},h("small",{},`Tier ${t.n} · ${t.era}`),t.name)));showTier(0);
let cat="all";function renderGrid(){$("#machineGrid").replaceChildren(...M.filter(m=>cat==="all"||m.c===cat).map(m=>h("div",{class:"card"},
 h("header",{},m.i?h("img",{src:`img/${m.i}.png`,alt:"",loading:"lazy"}):h("div",{class:"ph",title:"Art in progress"}),h("div",{},h("h4",{},m.n),h("div",{class:"meta"},`Tier ${m.t}`,m.soon?badge("wip"):""))),h("p",{},m.d))));
 [...$("#filters").children].forEach(b=>b.setAttribute("aria-pressed",b.dataset.c===cat))}
CATS.forEach(([c,l])=>{const b=h("button",{onclick:()=>{cat=c;renderGrid()}},l);b.dataset.c=c;$("#filters").append(b)});renderGrid();
function showStep(i){[...$("#wellSteps").children].forEach((li,j)=>li.firstChild.setAttribute("aria-current",i===j?"step":"false"));const[t,d,tip]=STEPS[i];
 $("#wellStepBody").replaceChildren(h("h3",{},`${i+1}. ${t}`),h("p",{},d),h("div",{class:"tip"},tip),h("div",{class:"step-nav"},
  i>0?h("button",{onclick:()=>showStep(i-1)},"← Back"):"",i<STEPS.length-1?h("button",{onclick:()=>showStep(i+1)},"Next →"):""))}
STEPS.forEach(([t],i)=>$("#wellSteps").append(h("li",{},h("button",{onclick:()=>showStep(i)},h("span",{class:"n"},String(i+1)),t))));showStep(0);
TEMPS.forEach(([n,c,d])=>{const e=h("div",{class:"temp"},h("b",{},n),h("span",{},d));e.style.setProperty("--c",c);$("#temperaments").append(e)});
CMDS.forEach(([c,d])=>$("#cmds").append(h("tr",{},h("td",{},h("code",{},c)),h("td",{},d))));
ROAD.forEach(([t,s,d])=>$("#roadmap").append(h("div",{class:"rm"},h("h4",{},t,badge(s)),h("p",{},d))));
