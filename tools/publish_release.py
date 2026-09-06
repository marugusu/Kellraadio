import os
import sys
import json
import urllib.request
import urllib.error
import subprocess

def get_github_token():
    # 1. Kontrolli keskkonnamuutujat
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("RELEASES_TOKEN")
    if token:
        return token.strip()

    # 2. Loe Windows Credential Managerist
    ps_cmd = """
Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public class CredMgr {
    [DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredRead(string target, int type, int reservedFlag, out IntPtr credentialPtr);
    [DllImport("advapi32.dll", EntryPoint = "CredFree", SetLastError = true)]
    public static extern void CredFree(IntPtr cred);
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public int Flags;
        public int Type;
        public string TargetName;
        public string Comment;
        public long LastWritten;
        public int CredentialBlobSize;
        public IntPtr CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }
    public static string GetPassword(string target) {
        IntPtr credPtr;
        if (CredRead(target, 1, 0, out credPtr)) {
            var cred = (CREDENTIAL)Marshal.PtrToStructure(credPtr, typeof(CREDENTIAL));
            byte[] bytes = new byte[cred.CredentialBlobSize];
            Marshal.Copy(cred.CredentialBlob, bytes, 0, cred.CredentialBlobSize);
            CredFree(credPtr);
            return Encoding.Unicode.GetString(bytes);
        }
        return null;
    }
}
'@
$t = [CredMgr]::GetPassword('git:https://github.com')
if ($t) { Write-Output $t }
"""
    try:
        proc = subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, text=True)
        out = proc.stdout.strip()
        if out:
            return out
    except Exception as e:
        print(f"Viga volituste lugemisel: {e}")

    return None

def publish_release(repo_owner, repo_name, tag_name, release_name, body_text, apk_path):
    token = get_github_token()
    if not token:
        print("[VIGA] GitHubi volitusi ei leitud.")
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "Kellraadio-Publisher"
    }

    # 1. Kontrolli, kas release on juba olemas
    check_url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/releases/tags/{tag_name}"
    existing_release = None
    try:
        req = urllib.request.Request(check_url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            existing_release = json.loads(resp.read().decode("utf-8"))
            print(f"[INFO] Väljalase sildiga {tag_name} on juba olemas (ID: {existing_release.get('id')}).")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            print(f"[HOIATUS] Release kontrolli viga: {e}")

    # 2. Kui puudub, loo uus väljalase
    if not existing_release:
        print(f"[1/3] Loon GitHub Release: {release_name} ({tag_name})...")
        create_url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/releases"
        payload = {
            "tag_name": tag_name,
            "name": release_name,
            "body": body_text,
            "draft": False,
            "prerelease": False
        }
        req = urllib.request.Request(
            create_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={**headers, "Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(req) as resp:
                existing_release = json.loads(resp.read().decode("utf-8"))
                print(f"[OK] Release loodud (ID: {existing_release.get('id')})!")
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8")
            print(f"[VIGA] Release loomine ebaõnnestus ({e.code}): {err_body}")
            raise e

    upload_url_template = existing_release.get("upload_url", "")
    upload_base_url = upload_url_template.split("{")[0]
    release_id = existing_release.get("id")

    # 3. Kontrolli, kas fail on juba lisatud
    asset_name = os.path.basename(apk_path)
    for asset in existing_release.get("assets", []):
        if asset.get("name") == asset_name:
            print(f"[INFO] Fail {asset_name} on juba lisatud! Kustutan vana versiooni...")
            del_url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/releases/assets/{asset.get('id')}"
            del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
            urllib.request.urlopen(del_req).close()

    # 4. Laadi APK üles
    apk_size = os.path.getsize(apk_path)
    print(f"[2/3] Laadin üles faili {asset_name} ({apk_size / (1024*1024):.1f} MB)...")

    upload_url = f"{upload_base_url}?name={urllib.parse.quote(asset_name)}"
    with open(apk_path, "rb") as f:
        apk_data = f.read()

    upload_req = urllib.request.Request(
        upload_url,
        data=apk_data,
        headers={
            **headers,
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(apk_size)
        }
    )

    with urllib.request.urlopen(upload_req) as resp:
        asset_info = json.loads(resp.read().decode("utf-8"))
        download_url = asset_info.get("browser_download_url")
        print(f"[3/3] [OK] APK edukalt üles laaditud!")
        print(f"Allalaadimislink: {download_url}")

if __name__ == "__main__":
    import shutil
    repo_owner = "marugusu"
    repo_name = "Kellraadio"
    tag_name = sys.argv[1] if len(sys.argv) > 1 else "v1.1.1"
    release_name = f"Kellraadio {tag_name.lstrip('v')}"
    body_text = sys.argv[2] if len(sys.argv) > 2 else "- Automaatne paigalduse jätkamine pärast seadetes loa andmist\n- Äpisisene uuendussüsteem\n- Stabiilsuse parandused"
    
    apk_path = f"Kellraadio-{tag_name}.apk"
    if not os.path.exists(apk_path):
        debug_apk = os.path.join("app", "build", "outputs", "apk", "debug", "app-debug.apk")
        if os.path.exists(debug_apk):
            print(f"[INFO] Kopeerin värske APK: {debug_apk} -> {apk_path}")
            shutil.copy2(debug_apk, apk_path)
        else:
            print(f"[VIGA] Faili {apk_path} ega {debug_apk} ei leitud! Käivita esmalt ./gradlew assembleDebug")
            sys.exit(1)

    publish_release(repo_owner, repo_name, tag_name, release_name, body_text, apk_path)
