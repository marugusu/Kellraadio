import os
import sys
import json
import urllib.request
import urllib.error
import subprocess

REPO_OWNER = "marugusu"
REPO_NAME = "Kellraadio"

def get_github_token():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("RELEASES_TOKEN")
    if token:
        return token.strip()

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
        print(f"[WARN] Credential Manager viga: {e}")

    return None

def make_request(url, token, method="GET", data=None):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "Kellraadio-Delete-Tool"
    }
    req = urllib.request.Request(url, headers=headers, method=method, data=data)
    try:
        with urllib.request.urlopen(req) as response:
            if method == "DELETE":
                return True
            return json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='ignore')
        print(f"[VIGA] HTTP {e.code} päringul {url}: {body}")
        return None

def main():
    token = get_github_token()
    if not token:
        print("[VIGA] GitHubi tokenit ei leitud!")
        sys.exit(1)

    keep_tag = "v1.1.9"
    print(f"Laadin GitHubi release'ide nimekirja repositooriumist {REPO_OWNER}/{REPO_NAME}...")
    releases = make_request(f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/releases", token)
    if not releases:
        print("Release'e ei leitud.")
        return

    print(f"Kokku leiti {len(releases)} release'i. Jätan alles versiooni: {keep_tag}")

    for r in releases:
        tag = r['tag_name']
        rel_id = r['id']
        name = r.get('name') or tag

        if tag == keep_tag:
            print(f" -> Säilitan: {tag} (ID: {rel_id}, {name})")
            continue

        print(f" -> Kustutan release'i: {tag} (ID: {rel_id}, {name})...")
        del_url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/releases/{rel_id}"
        success = make_request(del_url, token, method="DELETE")
        if success:
            print(f"    [OK] Release {tag} kustutatud.")
        else:
            print(f"    [VIGA] Release {tag} kustutamine ebaõnnestus.")

        # Kustutame ka giti tagi viite
        tag_url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/git/refs/tags/{tag}"
        tag_del = make_request(tag_url, token, method="DELETE")
        if tag_del:
            print(f"    [OK] Tag {tag} kustutatud GitHubist.")
        else:
            print(f"    [INFO] Tag {tag} ei vajanud kustutamist või puudus.")

    print("\nValmis! Kontrollin järelejäänud release'e:")
    rem_releases = make_request(f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/releases", token)
    if rem_releases:
        for r in rem_releases:
            print(f"Alles: {r['tag_name']} (ID: {r['id']}, Name: {r.get('name')})")
    else:
        print("Release'e ei leitud.")

if __name__ == "__main__":
    main()
