#!/usr/bin/env python3
"""Dependency-free static validation for NOVA module manifests."""
from __future__ import annotations
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_DIR = ROOT / "manifests"
ID_RE = re.compile(r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
VERSION_RE = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?$")
SECRET_KEYS = {"token","password","secret","api_key","private_key","credential","service_role","authorization","bearer","client_secret"}
ROOT_KEYS = {"schema_version","id","name","version","kind","lifecycle","repository","capabilities","permissions","data_access","network_policy","offline_support","dependencies"}
PERMISSION_KEYS = {"resource","action","scope","approval"}
KINDS = {"application","module","model","website","game","library"}
LIFECYCLES = {"proposed","in-development","preview","active","retired"}
ACTIONS = {"read","create","update","delete","publish","export","sync","execute","admin"}
SCOPES = {"self","participant","group","tenant","ecosystem","owner-only"}
DATA_CLASSES = {"public","user-private","sensitive","financial","biometric"}
NETWORK_POLICIES = {"offline-only","local-first","online-required"}
APPROVAL_ACTIONS = {"delete","publish","export","admin"}
FINANCIAL_CLASSES = {"financial"}
BIOMETRIC_CLASSES = {"biometric"}

def add(errors, where, message):
    errors.append(f"{where}: {message}")

def check_secrets(value, where, errors):
    if isinstance(value, dict):
        for key, child in value.items():
            k = str(key).lower().replace("-", "_")
            if k in SECRET_KEYS or any(x in k for x in ("access_token","refresh_token","private_key")):
                add(errors, where, f"credential-like field {key!r} is forbidden")
            check_secrets(child, where, errors)
    elif isinstance(value, list):
        for child in value:
            check_secrets(child, where, errors)

def validate(data, where="manifest"):
    errors = []
    if not isinstance(data, dict):
        return [f"{where}: root must be a JSON object"]
    unknown = set(data) - ROOT_KEYS
    missing = (ROOT_KEYS - {"dependencies"}) - set(data)
    if unknown: add(errors, where, f"unknown root keys: {', '.join(sorted(unknown))}")
    if missing: add(errors, where, f"missing required keys: {', '.join(sorted(missing))}")
    if data.get("schema_version") != "1.0": add(errors, where, "schema_version must be 1.0")
    if not isinstance(data.get("id"), str) or not ID_RE.fullmatch(data.get("id","")): add(errors, where, "invalid NOVA id")
    if not isinstance(data.get("name"), str) or not 2 <= len(data.get("name","")) <= 80: add(errors, where, "name must contain 2..80 characters")
    if not isinstance(data.get("version"), str) or not VERSION_RE.fullmatch(data.get("version","")): add(errors, where, "version must be semantic X.Y.Z")
    if not isinstance(data.get("kind"),str) or data.get("kind") not in KINDS: add(errors, where, "invalid kind")
    if not isinstance(data.get("lifecycle"),str) or data.get("lifecycle") not in LIFECYCLES: add(errors, where, "invalid lifecycle")
    repo = data.get("repository")
    if not isinstance(repo, dict) or set(repo) != {"url","default_branch"}:
        add(errors, where, "repository must contain url and default_branch only")
    else:
        if not isinstance(repo.get("url"), str) or not re.fullmatch(r"https://github\.com/Cumba35/[A-Za-z0-9._-]+", repo["url"]): add(errors, where, "repository.url must point to Cumba35 on GitHub")
        if not isinstance(repo.get("default_branch"), str) or not repo["default_branch"]: add(errors, where, "repository.default_branch is required")
    caps = data.get("capabilities")
    if not isinstance(caps, list) or any(not isinstance(x,str) or not re.fullmatch(r"[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*",x) for x in caps): add(errors, where, "invalid capabilities")
    elif len(set(caps)) != len(caps): add(errors, where, "capabilities must be unique")
    perms = data.get("permissions")
    if not isinstance(perms, list): add(errors, where, "permissions must be an array")
    else:
        for i, p in enumerate(perms):
            loc = f"{where}.permissions[{i}]"
            if not isinstance(p,dict) or set(p) != PERMISSION_KEYS:
                add(errors, loc, "permission must contain resource, action, scope and approval only")
                continue
            if not isinstance(p.get("resource"),str) or not re.fullmatch(r"[a-z][a-z0-9_.-]*",p["resource"]): add(errors,loc,"invalid resource")
            if not isinstance(p.get("action"),str) or p.get("action") not in ACTIONS: add(errors,loc,"invalid action")
            if not isinstance(p.get("scope"),str) or p.get("scope") not in SCOPES: add(errors,loc,"invalid scope")
            if not isinstance(p.get("approval"),bool): add(errors,loc,"approval must be boolean")
            elif isinstance(p.get("action"),str) and p.get("action") in APPROVAL_ACTIONS and p["approval"] is not True: add(errors,loc,"sensitive action requires explicit approval")
    data_access = data.get("data_access")
    if not isinstance(data_access,list) or any(not isinstance(x,str) or x not in DATA_CLASSES for x in data_access): add(errors,where,"invalid data_access classification")
    elif len(set(data_access)) != len(data_access): add(errors,where,"data_access must be unique")
    if not isinstance(data.get("network_policy"),str) or data.get("network_policy") not in NETWORK_POLICIES: add(errors,where,"invalid network_policy")
    if not isinstance(data.get("offline_support"),bool): add(errors,where,"offline_support must be boolean")
    deps = data.get("dependencies",[])
    if not isinstance(deps,list): add(errors,where,"dependencies must be an array")
    else:
        for i,d in enumerate(deps):
            loc=f"{where}.dependencies[{i}]"
            if not isinstance(d,dict) or set(d) != {"id","version_range","optional"}: add(errors,loc,"invalid dependency shape")
            elif not isinstance(d.get("id"),str) or not ID_RE.fullmatch(d.get("id","")) or not isinstance(d.get("version_range"),str) or not isinstance(d.get("optional"),bool): add(errors,loc,"invalid dependency values")
    check_secrets(data,where,errors)
    return errors

def main():
    paths=sorted(MANIFEST_DIR.glob("*.json"))
    if not paths:
        print("ERROR: no manifests found")
        return 1
    errors=[]; ids=set()
    for path in paths:
        try: data=json.loads(path.read_text(encoding="utf-8"))
        except (OSError,json.JSONDecodeError) as exc:
            add(errors,path.name,f"cannot read JSON: {exc}"); continue
        errors.extend(validate(data,path.name))
        if isinstance(data,dict):
            mid=data.get("id")
            if mid in ids: add(errors,path.name,f"duplicate module id: {mid}")
            ids.add(mid)
    if errors:
        print("NOVA manifest validation failed:")
        for error in errors: print(f"- {error}")
        return 1
    print(f"Validated {len(paths)} NOVA module manifest(s).")
    print("Static validation only; this does not grant runtime permissions or prove deployment.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
