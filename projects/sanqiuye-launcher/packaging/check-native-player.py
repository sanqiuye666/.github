"""核对原生玩家EXE的Windows GUI头、架构和源码构建标识。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

def inspect_native(path:Path,expected:str):
    if not re.fullmatch('[0-9a-f]{64}',expected):
        raise ValueError('需要完整源码构建身份')
    data=path.read_bytes()
    if len(data)<256 or len(data)>64*1024*1024 or data[:2]!=b'MZ':
        raise ValueError('原生EXE格式或大小无效')
    pe=struct.unpack_from('<I',data,0x3c)[0]
    if pe+96>len(data) or data[pe:pe+4]!=b'PE\0\0':
        raise ValueError('原生EXE的PE头无效')
    if struct.unpack_from('<H',data,pe+4)[0]!=0x8664:
        raise ValueError('需要Windows x64 EXE')
    if struct.unpack_from('<H',data,pe+24+68)[0]!=2:
        raise ValueError('需要Windows GUI程序，不能交付控制台EXE')
    if ('SQY_NATIVE_BUILD:'+expected).encode('ascii') not in data:
        raise ValueError('原生EXE与当前源码构建身份不一致，请重新编译')
    return {'buildId':expected,'windowsGui':True,'architecture':'x64','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--exe',type=Path,required=True);parser.add_argument('--expected',required=True)
    args=parser.parse_args()
    try: print(json.dumps(inspect_native(args.exe,args.expected),ensure_ascii=False))
    except (ValueError,OSError,struct.error) as error: raise SystemExit(str(error))
