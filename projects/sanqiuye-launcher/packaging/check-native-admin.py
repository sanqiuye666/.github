"""核对管理EXE的Windows GUI头和实际编译的源码身份。"""
import argparse,hashlib,json,re,struct
from pathlib import Path

def inspect(path,expected):
    if not re.fullmatch('[a-f0-9]{64}',expected):raise ValueError('需要完整源码身份')
    data=path.read_bytes()
    if len(data)<256 or len(data)>96*1024*1024 or data[:2]!=b'MZ':raise ValueError('管理EXE格式或大小无效')
    offset=struct.unpack_from('<I',data,0x3c)[0]
    if offset+96>len(data) or data[offset:offset+4]!=b'PE\0\0':raise ValueError('管理EXE的PE头无效')
    if struct.unpack_from('<H',data,offset+4)[0]!=0x8664:raise ValueError('需要Windows x64管理EXE')
    if struct.unpack_from('<H',data,offset+24+68)[0]!=2:raise ValueError('管理程序必须为GUI EXE')
    # Rust服务握手使用编译期DFS_BUILD_ID，正式EXE必须保留其完整值。
    if expected.encode('ascii') not in data:raise ValueError('管理EXE源码身份不一致，请重新编译')
    return {'buildId':expected,'windowsGui':True,'architecture':'x64','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--exe',type=Path,required=True);parser.add_argument('--expected',required=True);args=parser.parse_args()
    try:print(json.dumps(inspect(args.exe,args.expected),ensure_ascii=False))
    except (ValueError,OSError,struct.error) as error:raise SystemExit(str(error))
