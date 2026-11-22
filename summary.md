# Essai de retouche par IA

Planches : origine | effacement actuel | andran-migan | andran-migan_pipeline_v2 | lxfate-migan | FreeHu-migan-512-generator | Carve-lama_fp32

- andran-migan (pipeline, 29.5 Mo) : médiane 324 ms, max 442 ms sur 106 régions (processeur du serveur de test)
- andran-migan_pipeline_v2 (pipeline, 28.1 Mo) : médiane 361 ms, max 488 ms sur 106 régions (processeur du serveur de test)
- lxfate-migan (migan, 29.5 Mo) : médiane 327 ms, max 438 ms sur 106 régions (processeur du serveur de test)
- FreeHu-migan-512-generator (migan, 28.1 Mo) : médiane 340 ms, max 504 ms sur 106 régions (processeur du serveur de test)
- Carve-lama_fp32 (lama, 208.0 Mo) : médiane 1826 ms, max 1971 ms sur 106 régions (processeur du serveur de test)

## Journal

```
- dépôt andraniksargsyan/migan : ['.gitattributes', 'LICENSE', 'README.md', 'migan.onnx', 'migan_pipeline_v2.onnx']
- dépôt lxfater/inpaint-web : ['.gitattributes', 'README.md', 'migan.onnx', 'realesrgan-x4.onnx']
- dépôt FreeHugsForRobots/ps-inpaint-migan : ['.gitattributes', 'README.md', 'migan-512-generator.onnx']
- dépôt Carve/LaMa-ONNX : ['.gitattributes', 'README.md', 'image.jpg', 'lama.onnx', 'lama_fp32.onnx', 'mask.png', 'output_onnx.png', 'output_onnx_fp32.png', 'output_orig.png']
- modèle andran-migan (andraniksargsyan/migan/migan.onnx, 29.5 Mo) : image['batch_size', 3, 'height', 'width']:tensor(uint8), mask['batch_size', 1, 'height', 'width']:tensor(uint8) -> result['Castresult_dim_0', 3, 'Castresult_dim_2', 'Castresult_dim_3']:tensor(uint8)
  -> lu comme « pipeline »
- modèle andran-migan_pipeline_v2 (andraniksargsyan/migan/migan_pipeline_v2.onnx, 28.1 Mo) : image['batch_size', 3, 'height', 'width']:tensor(uint8), mask['batch_size', 1, 'height', 'width']:tensor(uint8) -> result['ScatterNDresult_dim_0', 3, 'ScatterNDresult_dim_2', 'ScatterNDresult_dim_3']:tensor(uint8)
  -> lu comme « pipeline »
- modèle lxfate-migan (lxfater/inpaint-web/migan.onnx, 29.5 Mo) : input[1, 4, 512, 512]:tensor(float) -> output[1, 3, 512, 512]:tensor(float)
  -> lu comme « migan »
- modèle FreeHu-migan-512-generator (FreeHugsForRobots/ps-inpaint-migan/migan-512-generator.onnx, 28.1 Mo) : gen_input[1, 4, 512, 512]:tensor(float) -> gen_output[1, 3, 512, 512]:tensor(float)
  -> lu comme « migan »
- modèle Carve/LaMa-ONNX/lama.onnx : échec ([ONNXRuntimeError] : 1 : FAIL : Node (_inlfunc_saicinpainting_training_modules_ffc_FourierUnit_getattr_L__self___model_generator_model___5___conv1_ffc_convg2g_fu_1_DFT_20) Op (DFT) [ShapeInferenceError] one-sided DFT requires real input (last dimension must be 1))
- modèle Carve-lama_fp32 (Carve/LaMa-ONNX/lama_fp32.onnx, 208.0 Mo) : image['batch', 3, 512, 512]:tensor(float), mask['batch', 1, 512, 512]:tensor(float) -> output['batch', 3, 512, 512]:tensor(float)
  -> lu comme « lama »

106 paires, 5 modèles

```
