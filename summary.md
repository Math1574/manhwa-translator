# LaMa manga avec ONNX Runtime 1.20.0, par tuiles

```
onnxruntime 1.20.0, onnx 1.23.2
--- README ogkalu/lama-manga-onnx-dynamic ---
---
license: apache-2.0
---

An ONNX model for [AnimeMangaInpainting](https://huggingface.co/dreMaz/AnimeMangaInpainting).

It is based on [FourierUnitJIT](https://github.com/Carve-Photos/lama/commit/5a67a02ad5047c33326695acf3bff8f9f44f19ac) but with improvements that allow inference on images with varying input sizes.
--- README dreMaz/AnimeMangaInpainting ---
---
license: mit
---
lama_large_512px.ckpt is finetuned [big lama](https://github.com/advimman/lama) on 300k manga and anime style data.  Significently better than older lama_mpe on manga.
--- README ogkalu/aot-inpainting ---
---
license: mit
---

A jit-traced version of the model trained by [zyddnys](https://github.com/zyddnys/) for  [manga-image-translator](https://github.com/zyddnys/manga-image-translator/)

fichier 206291843 octets, sha256 de31ffa5ba26916b8ea35319f6c12151ff9654d4261bccf0583a69bb095315f9
opset : ai.onnx=18 ; ir 8
opérateurs : Add×325, BatchNormalization×75, Cast×710, Concat×280, Constant×3379, ConstantOfShape×98, Conv×222, ConvTranspose×3, Cos×144, Div×432, Gather×540, MatMul×432, Mul×291, Neg×36, Pad×98, Range×252, Relu×152, Reshape×268, Shape×468, Sigmoid×1, Sin×144, Slice×278, Sqrt×144, Sub×181, Transpose×674, Unsqueeze×612
mémoire avant session : 504 Mo
session 4 fils : 3.3 s, mémoire max 736 Mo
session 2 fils : 3.2 s, mémoire max 976 Mo
tuile 256x256, 4 fils : 479 ms, mémoire max 1068 Mo
tuile 384x384, 4 fils : 1055 ms, mémoire max 1180 Mo
tuile 512x512, 4 fils : 1949 ms, mémoire max 1324 Mo
tuile 256x256, 2 fils : 482 ms, mémoire max 1411 Mo
tuile 384x384, 2 fils : 1037 ms, mémoire max 1524 Mo
tuile 512x512, 2 fils : 1905 ms, mémoire max 1774 Mo

106 régions, 166 tuiles : médiane 946 ms, max 11766 ms (999x515, 6 tuiles), total 155 s ; mémoire max 1883 Mo
```
