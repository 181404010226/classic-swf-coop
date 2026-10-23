"""Preserve every original non-ABC tag byte; FFDec must not reserialize artwork."""
import pathlib,struct,zlib,hashlib,json
root=pathlib.Path(__file__).resolve().parents[1]
def parse(path):
 b=path.read_bytes()
 if b[:3]==b'CWS':b=b'FWS'+b[3:8]+zlib.decompress(b[8:])
 assert b[:3]==b'FWS'
 p=8+(5+4*(b[8]>>3)+7)//8+4;head=b[:p];tags=[]
 while p<len(b):
  start=p;h=struct.unpack_from('<H',b,p)[0];p+=2;t=h>>6;n=h&63
  if n==63:n=struct.unpack_from('<I',b,p)[0];p+=4
  body=b[p:p+n];p+=n;tags.append((t,b[start:p],body))
  if t==0:break
 return head,tags
original=root/'local'/'game.swf';compiled=root/'build'/'game'/'patched'/'game-lan.swf'
head,a=parse(original);_,b=parse(compiled)
changes=[{'index':i,'type':x[0],'old':len(x[1]),'new':len(y[1])}for i,(x,y)in enumerate(zip(a,b))if x[1]!=y[1]]
print('TAG CHANGES',changes[:25], 'counts',len(a),len(b))
abc=[t[1]for t in b if t[0]==82];assert len(abc)==sum(t[0]==82 for t in a)==1
out=head+b''.join(abc.pop(0) if t[0]==82 else t[1] for t in a)
out=out[:4]+struct.pack('<I',len(out))+out[8:]
(root/'build'/'game'/'patched'/'game-lan-preserved.swf').write_bytes(out);(root/'app'/'assets'/'game.swf').write_bytes(out)
newhead,c=parse(root/'app'/'assets'/'game.swf')
assert [t[1]for t in a if t[0]!=82]==[t[1]for t in c if t[0]!=82]
(root/'build'/'game'/'patched'/'preservation.json').write_text(json.dumps({'originalSha256':hashlib.sha256(original.read_bytes()).hexdigest(),'patchedSha256':hashlib.sha256(out).hexdigest(),'originalTagCount':len(a),'preservedNonAbcTags':sum(t[0]!=82 for t in a),'allNonAbcTagBytesIdentical':True,'ffdecReserializedChanges':changes},indent=2))
print('PRESERVED',len(out),'bytes')
