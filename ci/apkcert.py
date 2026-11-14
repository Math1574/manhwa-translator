#!/usr/bin/env python3
"""Empreinte SHA-256 du ou des certificats de signature d'un APK (bloc de signature v2/v3), sans outil Android.
Usage : apkcert.py app.apk   — affiche « v2 sha256=EMPREINTE taille_cert=N » ; code de sortie non nul si l'APK n'a pas de bloc de signature lisible.
Sert à vérifier, à chaque compilation, que l'APK publié est signé avec la clé fixe du projet (sinon les mises à jour ne s'installeraient pas par-dessus)."""
import sys, struct, hashlib

def u32(b, o): return struct.unpack_from('<I', b, o)[0]
def u64(b, o): return struct.unpack_from('<Q', b, o)[0]

def lp(b, o):
    """length-prefixed (uint32) -> (bytes, next_offset)"""
    n = u32(b, o)
    return b[o+4:o+4+n], o+4+n

def main(path):
    with open(path, 'rb') as f:
        f.seek(0, 2); size = f.tell()
        tail = min(size, 70000)
        f.seek(size - tail); t = f.read(tail)
        i = t.rfind(b'PK\x05\x06')
        if i < 0: print("EOCD introuvable"); return 1
        eocd = size - tail + i
        f.seek(eocd + 16); cd_off = struct.unpack('<I', f.read(4))[0]
        f.seek(cd_off - 24); hdr = f.read(24)
        if hdr[8:] != b'APK Sig Block 42':
            print("pas de bloc de signature APK (v2/v3) avant le répertoire central"); return 2
        blk_size = u64(hdr, 0)
        f.seek(cd_off - blk_size - 8); blk = f.read(blk_size + 8)
    pos = 8
    end = len(blk) - 24
    found = False
    while pos < end:
        ln = u64(blk, pos); bid = u32(blk, pos + 8)
        val = blk[pos+12:pos+8+ln]
        pos += 8 + ln
        name = {0x7109871a: 'v2', 0xf05368c0: 'v3', 0x1b93ad61: 'v3.1'}.get(bid)
        if not name: continue
        signers, _ = lp(val, 0)
        so = 0
        while so < len(signers):
            signer, so = lp(signers, so)
            sd, p = lp(signer, 0)
            digests, q = lp(sd, 0)
            certs, q = lp(sd, q)
            co = 0
            while co < len(certs):
                cert, co = lp(certs, co)
                print(f"{name} sha256={hashlib.sha256(cert).hexdigest().upper()} taille_cert={len(cert)}")
                found = True
    if not found:
        print("aucun certificat lisible"); return 3
    return 0

sys.exit(main(sys.argv[1]))
