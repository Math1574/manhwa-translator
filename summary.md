# Essai de retouche par IA (modèles manga)

Planches : origine | effacement actuel | aot-inpainting-aot | aot-inpainting-aot_traced | aot-inpainting-aot | aot-inpainting-aot_traced | lama-manga-onn-lama-manga-dynamic | lama-manga-onn-lama-manga | LaMa-ONNX-lama_fp32

- aot-inpainting-aot (AOT, 23.1 Mo, sortie [-1,1]) : médiane 658 ms, max 3044 ms, 5987 ms par mégapixel, sur 212 régions (processeur du serveur de test, 4 fils)
- aot-inpainting-aot_traced (AOT, 23.1 Mo, sortie [-1,1]) : médiane 708 ms, max 3683 ms, 6739 ms par mégapixel, sur 212 régions (processeur du serveur de test, 4 fils)
- aot-inpainting-aot (AOT, 23.1 Mo, sortie [-1,1]) : médiane 658 ms, max 3044 ms, 5987 ms par mégapixel, sur 212 régions (processeur du serveur de test, 4 fils)
- aot-inpainting-aot_traced (AOT, 23.1 Mo, sortie [-1,1]) : médiane 708 ms, max 3683 ms, 6739 ms par mégapixel, sur 212 régions (processeur du serveur de test, 4 fils)
- lama-manga-onn-lama-manga-dynamic (LaMa, 206.3 Mo, sortie [0,1]) : médiane 1484 ms, max 7219 ms, 14191 ms par mégapixel, sur 106 régions (processeur du serveur de test, 4 fils)
- lama-manga-onn-lama-manga (LaMa, 207.5 Mo, sortie [0,1]) : médiane 4311 ms, max 4444 ms, 32407 ms par mégapixel, sur 106 régions (processeur du serveur de test, 4 fils)
- LaMa-ONNX-lama_fp32 (LaMa, 208.0 Mo, sortie [0,255]) : médiane 4298 ms, max 4515 ms, 32359 ms par mégapixel, sur 106 régions (processeur du serveur de test, 4 fils)

## Journal

```
- dépôt ogkalu/aot-inpainting : .gitattributes (0.0 Mo), README.md (0.0 Mo), aot.onnx (23.1 Mo), aot_traced.pt (23.1 Mo), inpainting.ckpt (22.8 Mo)
- dépôt ogkalu/aot-inpainting-jit : .gitattributes (0.0 Mo), README.md (0.0 Mo), aot.onnx (23.1 Mo), aot_traced.pt (23.1 Mo), inpainting.ckpt (22.8 Mo)
- dépôt ogkalu/lama-manga-onnx-dynamic : .gitattributes (0.0 Mo), README.md (0.0 Mo), lama-manga-dynamic.onnx (206.3 Mo)
- dépôt mayocream/lama-manga-onnx : .gitattributes (0.0 Mo), README.md (0.0 Mo), lama-manga.onnx (207.5 Mo)
- dépôt dreMaz/AnimeMangaInpainting : .gitattributes (0.0 Mo), README.md (0.0 Mo), lama_large_512px.ckpt (204.5 Mo)
- dépôt Carve/LaMa-ONNX : .gitattributes (0.0 Mo), README.md (0.0 Mo), image.jpg (0.1 Mo), lama.onnx (207.5 Mo), lama_fp32.onnx (208.0 Mo), mask.png (0.0 Mo), output_onnx.png (0.2 Mo), output_onnx_fp32.png (0.2 Mo), output_orig.png (0.3 Mo)
- modèle aot-inpainting-aot (ogkalu/aot-inpainting/aot.onnx, 23.1 Mo) : image['batch', 3, 'h', 'w']:tensor(float), mask['batch', 1, 'h', 'w']:tensor(float) -> inpainted['batch', 3, 'h', 'w']:tensor(float) ; AOT ; taille libre
- modèle aot-inpainting-aot_traced (ogkalu/aot-inpainting/aot_traced.pt, 23.1 Mo) : torch.jit ; AOT ; taille libre
- modèle ogkalu/aot-inpainting/inpainting.ckpt : échec (PytorchStreamReader failed locating file constants.pkl: file not found. This is an internal miniz error. If you are seeing this error, there is a high likelihood that your checkpoint file is corrupted. This can happen if the checkpoint was not saved properly, was transferred incorrectly, or the file was modified after saving.)
- modèle aot-inpainting-aot (ogkalu/aot-inpainting-jit/aot.onnx, 23.1 Mo) : image['batch', 3, 'h', 'w']:tensor(float), mask['batch', 1, 'h', 'w']:tensor(float) -> inpainted['batch', 3, 'h', 'w']:tensor(float) ; AOT ; taille libre
- modèle aot-inpainting-aot_traced (ogkalu/aot-inpainting-jit/aot_traced.pt, 23.1 Mo) : torch.jit ; AOT ; taille libre
- modèle ogkalu/aot-inpainting-jit/inpainting.ckpt : échec (PytorchStreamReader failed locating file constants.pkl: file not found. This is an internal miniz error. If you are seeing this error, there is a high likelihood that your checkpoint file is corrupted. This can happen if the checkpoint was not saved properly, was transferred incorrectly, or the file was modified after saving.)
- modèle lama-manga-onn-lama-manga-dynamic (ogkalu/lama-manga-onnx-dynamic/lama-manga-dynamic.onnx, 206.3 Mo) : image['batch', 3, 'h', 'w']:tensor(float), mask['batch', 1, 'h', 'w']:tensor(float) -> inpainted['batch', 3, 'h', 'w']:tensor(float) ; LaMa ; taille libre
- modèle lama-manga-onn-lama-manga (mayocream/lama-manga-onnx/lama-manga.onnx, 207.5 Mo) : image['batch', 3, 512, 512]:tensor(float), mask['batch', 1, 512, 512]:tensor(float) -> output['batch', 3, 512, 512]:tensor(float) ; LaMa ; taille fixe 512
- modèle LaMa-ONNX-lama_fp32 (Carve/LaMa-ONNX/lama_fp32.onnx, 208.0 Mo) : image['batch', 3, 512, 512]:tensor(float), mask['batch', 1, 512, 512]:tensor(float) -> output['batch', 3, 512, 512]:tensor(float) ; LaMa ; taille fixe 512

106 paires, 7 modèles

  aot-inpainting-aot : sortie lue comme [-1,1]
  aot-inpainting-aot_traced : sortie lue comme [-1,1]
  aot-inpainting-aot : sortie lue comme [-1,1]
  aot-inpainting-aot_traced : sortie lue comme [-1,1]
  lama-manga-onn-lama-manga-dynamic : sortie lue comme [0,1]
  lama-manga-onn-lama-manga : sortie lue comme [0,1]
  LaMa-ONNX-lama_fp32 : sortie lue comme [0,255]
```
