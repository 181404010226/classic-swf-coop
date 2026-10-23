"""Reproduce the tested adapter from a user-supplied SWF and pinned build tools."""
from pathlib import Path
import argparse,hashlib,io,json,shutil,subprocess,sys,tarfile,time,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
GAME_SHA='b971248765587523e63e5264a2e9d00db3e595d9866b2d8c15a403d7dee2aa6b'
DEPS={
 'ffdec_26.4.0.zip':('https://github.com/jindrapetrik/jpexs-decompiler/releases/download/version26.4.0/ffdec_26.4.0.zip','56ce960aeafc7caf034e2052ba49f878a883f7c7ca690da6eeffca9587b5e4b9'),
 'ruffle-0.7.1.tgz':('https://registry.npmjs.org/@ruffle-rs/ruffle/-/ruffle-0.7.1.tgz','dc9e7407549a91accc41a14932b0b9d9a9d51a3742ce6f020888c8a76682b157'),
 'json-20240303.jar':('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar','3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed')
}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def fetch(cache,name):
 url,digest=DEPS[name];target=cache/name
 if target.exists() and sha(target)==digest:return target
 for attempt in range(3):
  try:
   request=urllib.request.Request(url,headers={'User-Agent':'classic-swf-coop/0.2.0'})
   with urllib.request.urlopen(request,timeout=90) as response:data=response.read()
   if hashlib.sha256(data).hexdigest()!=digest:raise ValueError('Dependency hash mismatch: '+name)
   target.write_bytes(data);return target
  except Exception:
   if attempt==2:raise
   time.sleep(1)
def run(*args):subprocess.run([str(a) for a in args],check=True,cwd=ROOT)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--game',type=Path,required=True);parser.add_argument('--java',default='java');args=parser.parse_args()
 if not args.game.is_file() or sha(args.game)!=GAME_SHA:raise SystemExit('This profile requires the exact original rgm141 SWF. See README for source and SHA256.')
 local=ROOT/'local';local.mkdir(exist_ok=True);target=local/'game.swf'
 if args.game.resolve()!=target.resolve():shutil.copy2(args.game,target)
 cache=ROOT/'build/deps';cache.mkdir(parents=True,exist_ok=True)
 archive=fetch(cache,'ffdec_26.4.0.zip');ffdec=cache/'ffdec'
 if not (ffdec/'ffdec.jar').exists():
  ffdec.mkdir(exist_ok=True)
  with zipfile.ZipFile(archive) as z:
   for member in z.infolist():
    destination=(ffdec/member.filename).resolve()
    if not destination.is_relative_to(ffdec.resolve()):raise ValueError('Unsafe ZIP path')
   z.extractall(ffdec)
 runtime=ROOT/'app/assets/ruffle';runtime.mkdir(exist_ok=True)
 with tarfile.open(fetch(cache,'ruffle-0.7.1.tgz')) as tar:
  for member in tar.getmembers():
   name=Path(member.name).name
   if member.isfile() and (name.endswith(('.js','.wasm')) or name.startswith('LICENSE')):
    (runtime/name).write_bytes(tar.extractfile(member).read())
 lib=ROOT/'app/lib';lib.mkdir(exist_ok=True);shutil.copy2(fetch(cache,'json-20240303.jar'),lib/'json.jar')
 output=ROOT/'build/game';output.mkdir(parents=True,exist_ok=True)
 run(args.java,'-Xmx3g','-jar',ffdec/'ffdec.jar','-export','script',output/'export',target)
 if not (output/'export/scripts/Game.as').exists():raise RuntimeError('FFDec did not export the expected scripts')
 run(sys.executable,ROOT/'tools/patch_game.py')
 patched=output/'patched';replacement=[]
 for cls in ['Game','level','deadFire','WaterGirlStairs']:replacement.extend([cls,patched/(cls+'.as')])
 run(args.java,'-Xmx3g','-jar',ffdec/'ffdec.jar','-replace',target,patched/'game-lan.swf',*replacement)
 run(sys.executable,ROOT/'tools/splice_abc.py')
 print('READY: app/assets/game.swf; asset preservation report: build/game/patched/preservation.json')
if __name__=='__main__':main()
