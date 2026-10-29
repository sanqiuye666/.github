"""核对新加入的运行时许可确实进入成品，而非仅存在于node_modules。"""
from pathlib import Path
import argparse, hashlib, json
parser=argparse.ArgumentParser()
parser.add_argument('--bundle',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
names=['NOTICE.md','skinview3d-LICENSE.txt','skinview-utils-LICENSE.txt','three-LICENSE.txt']
for name in names:
    expected=(root/'docs/third-party/creative-tools'/name).read_bytes()
    actual=(args.bundle/'third-party/creative-tools'/name).read_bytes()
    assert actual==expected,name
print(json.dumps({'passed':True,'files':names,'bundle':str(args.bundle)}))
