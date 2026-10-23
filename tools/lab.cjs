const http=require('http'),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..');
http.createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 const name=pathname==='/'?'index.html':pathname.slice(1);
 if(!/^[a-zA-Z0-9_./-]+$/.test(name)||name.includes('..'))return res.writeHead(404).end();
 const file=path.join(root,name==='index.html'||name==='lab.js'?'tools/lab-ui':'app/assets',name);
 fs.stat(file,(e,s)=>{if(e||!s.isFile())return res.writeHead(404).end();
  const mime={'.html':'text/html','.js':'application/javascript','.wasm':'application/wasm','.swf':'application/x-shockwave-flash'}[path.extname(file)]||'application/octet-stream';
  res.writeHead(200,{'Content-Type':mime,'Content-Length':s.size,'Cache-Control':'no-store'});fs.createReadStream(file).pipe(res);
 });
}).listen(17694,'127.0.0.1',()=>console.log('Adapter lab: http://127.0.0.1:17694'));
