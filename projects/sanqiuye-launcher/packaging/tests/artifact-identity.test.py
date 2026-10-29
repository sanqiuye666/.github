import hashlib,json,subprocess,sys,tempfile,unittest,zipfile
from pathlib import Path
CHECK=Path(__file__).resolve().parents[1]/'check-artifact-identity.py'
PREFIX='cn/dreamingfish/updater/player/launcher/web/ui/'
IDENTITY='a'*64
class PlayerArtifactTests(unittest.TestCase):
    def check(self,missing_info=False,missing_asset=False,changed=False):
        with tempfile.TemporaryDirectory() as tmp:
            jar=Path(tmp)/'player.jar'
            with zipfile.ZipFile(jar,'w') as z:
                z.writestr('META-INF/dreamingfish/build/player-app.json',json.dumps(dict(schemaVersion=1,component='player-app',buildId=IDENTITY)))
                if not missing_info:z.writestr(PREFIX+'build-info.json',json.dumps(dict(buildId=IDENTITY,outputs={'index.html':hashlib.sha256(b'good').hexdigest()})))
                if not missing_asset:z.writestr(PREFIX+'index.html','bad' if changed else 'good')
            return subprocess.run([sys.executable,str(CHECK),'--expected',IDENTITY,'--player-jar',str(jar)],capture_output=True).returncode
    def test_valid_embedded_player(self):self.assertEqual(0,self.check())
    def test_changed_embedded_asset_fails(self):self.assertNotEqual(0,self.check(changed=True))
    def test_missing_embedded_player_fails(self):self.assertNotEqual(0,self.check(missing_info=True))
    def test_missing_embedded_asset_fails(self):self.assertNotEqual(0,self.check(missing_asset=True))
if __name__=='__main__':unittest.main()
