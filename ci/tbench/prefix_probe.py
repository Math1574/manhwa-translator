#!/usr/bin/env python3
"""Sonde : la consigne lue une fois puis recopiée (clone de session) donne-t-elle la même réponse qu'une conversation, et combien plus vite ?
Gemma 4 n'a pas de gabarit simple de tour de parole dans ses métadonnées (seulement un gabarit Jinja, appliqué par les conversations) : la session doit recevoir le texte
mis en forme à la main. Usage : prefix_probe.py <modèle.litertlm>"""
import re, sys, time
import litert_lm as lm

model = sys.argv[1]
data = open(model, "rb").read(64 * 1024 * 1024)
i = data.find(b"<|turn>system")
print("=== gabarit Jinja (extrait) ===")
print(data[max(0, i - 1500): i + 2500].decode("utf-8", "replace") if i >= 0 else "introuvable dans les 64 premiers Mo")
engine = lm.Engine(model, backend=lm.Backend.CPU(), max_num_tokens=4096)
for t in ["<|turn>system\n", "<turn|>\n", "<|turn>user\n", "<|turn>model\n", "<bos>", "<start_of_turn>", "<end_of_turn>"]:
    try:
        print("jetons de", repr(t), "->", engine.tokenize(t))
    except Exception as e:
        print("jetons de", repr(t), "-> erreur", e)
try:
    print("BOS", engine.bos_token_id, "EOS", engine.eos_token_ids)
except Exception as e:
    print("BOS/EOS : erreur", e)

system = ("You are a professional translator. Translate each numbered English line into natural French. "
          "Answer with the same numbers, one line each, in the form 'N| translation', nothing else.") * 3
user = "Lines to translate:\n1| I'LL MAKE SURE TO TEACH HIM A LESSON.\n2| What's the point? Anyway, let's go.\n3| THE MOUNT HUA SECT HAS FALLEN?!"
sampler = lm.SamplerConfig(top_k=1, top_p=1.0, temperature=0.0, seed=7)


def conv():
    c = engine.create_conversation(system_message=system, sampler_config=sampler, max_output_tokens=200)
    try:
        return str(c.send_message(user))
    finally:
        c.close()


t = time.time(); ref = conv(); print(f"\n=== conversation ({time.time() - t:.1f} s) ===\n{ref}")

variants = {
    "gabarit à la main, consigne dans un tour système": ("<|turn>system\n" + system + "<turn|>\n<|turn>user\n", user + "<turn|>\n<|turn>model\n"),
    "gabarit à la main, sans saut de ligne final": ("<|turn>system\n" + system + "<turn|>\n<|turn>user\n", user + "<turn|>\n<|turn>model"),
}
for apply in (True, False):
    for name, (first, second) in variants.items():
        try:
            base = engine.create_session(apply_prompt_template=apply, sampler_config=sampler, max_output_tokens=200)
            t0 = time.time(); base.run_prefill([first]); tp = time.time() - t0
            for k in range(2):
                s = base.clone()
                t = time.time(); s.run_prefill([second]); r = s.run_decode(); dt = time.time() - t
                out = r.texts[0] if r.texts else ""
                print(f"\n=== session apply_prompt_template={apply} / {name} / essai {k + 1} (consigne {tp:.1f} s, demande {dt:.1f} s) identique={out.strip() == ref.strip()} ===\n{out[:600]}")
                s.close()
            base.close()
        except Exception as e:
            print(f"\n=== session apply_prompt_template={apply} / {name} : erreur {e!r}")
