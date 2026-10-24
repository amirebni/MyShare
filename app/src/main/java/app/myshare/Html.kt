package app.myshare

const val PAGE = """<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>MyShare</title>
<style>
body{font-family:system-ui,sans-serif;margin:0;background:#f5f5f5;color:#222}
header{background:#1565c0;color:#fff;padding:12px;position:sticky;top:0}
#path{direction:ltr;text-align:left;font-size:13px;word-break:break-all;margin-top:4px}
#free{font-size:12px;margin-top:4px;opacity:.9}
.bar{padding:8px;display:flex;gap:8px;flex-wrap:wrap}
button{padding:8px 12px;border:0;border-radius:6px;background:#1565c0;color:#fff;cursor:pointer}
button.r{background:#c62828}
.row{display:flex;align-items:center;padding:10px 12px;border-bottom:1px solid #ddd;background:#fff;gap:8px}
.n{flex:1;word-break:break-all;direction:ltr;text-align:left;cursor:pointer}
.s{color:#777;font-size:12px;white-space:nowrap}
#prog{height:4px;background:#4caf50;width:0}
</style></head><body>
<header><div>MyShare</div><div id="path"></div><div id="free"></div></header>
<div id="prog"></div>
<div class="bar">
<button onclick="document.getElementById('f').click()">آپلود به گوشی</button>
<button onclick="mk()">پوشه جدید</button>
<button onclick="go(parent())">بالا</button>
</div>
<input type="file" id="f" multiple hidden>
<div id="list"></div>
<script>
var T=new URLSearchParams(location.search).get('t')||'';
var cur='';
function u(a,p,x){return '/api/'+a+'?t='+encodeURIComponent(T)+'&path='+encodeURIComponent(p)+(x||'');}
function parent(){return cur.split('/').slice(0,-1).join('/');}
function fmt(n){if(n<1024)return n+' B';if(n<1048576)return (n/1024).toFixed(1)+' KB';if(n<1073741824)return (n/1048576).toFixed(1)+' MB';return (n/1073741824).toFixed(2)+' GB';}
function go(p){
  cur=p;
  fetch(u('list',p)).then(function(r){return r.json();}).then(function(d){
    document.getElementById('path').textContent='/'+p;
    document.getElementById('free').textContent='فضای آزاد گوشی: '+fmt(d.free);
    var L=document.getElementById('list');L.innerHTML='';
    d.items.forEach(function(it){
      var full=p?p+'/'+it.name:it.name;
      var row=document.createElement('div');row.className='row';
      var n=document.createElement('div');n.className='n';
      n.textContent=(it.dir?'[DIR] ':'')+it.name;
      n.onclick=function(){if(it.dir)go(full);else location.href=u('get',full);};
      var s=document.createElement('div');s.className='s';s.textContent=it.dir?'':fmt(it.size);
      var del=document.createElement('button');del.className='r';del.textContent='حذف';
      del.onclick=function(){if(confirm('حذف شود؟\n'+full))fetch(u('delete',full),{method:'POST'}).then(function(){go(cur);});};
      row.appendChild(n);row.appendChild(s);row.appendChild(del);L.appendChild(row);
    });
  });
}
function mk(){
  var n=prompt('نام پوشه:');
  if(n)fetch(u('mkdir',cur,'&name='+encodeURIComponent(n)),{method:'POST'}).then(function(){go(cur);});
}
document.getElementById('f').onchange=function(e){
  var files=Array.from(e.target.files),i=0;
  e.target.value='';
  (function next(){
    var pg=document.getElementById('prog');
    if(i>=files.length){pg.style.width='0';go(cur);return;}
    var f=files[i++],x=new XMLHttpRequest();
    x.open('POST',u('upload',cur,'&name='+encodeURIComponent(f.name)));
    x.upload.onprogress=function(ev){pg.style.width=(ev.loaded/ev.total*100)+'%';};
    x.onload=function(){if(x.status!==200)alert('خطا در آپلود '+f.name+': '+x.responseText);next();};
    x.onerror=function(){alert('اتصال قطع شد');pg.style.width='0';};
    x.send(f);
  })();
};
go('');
</script></body></html>"""
