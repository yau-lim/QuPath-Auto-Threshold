# QuPath Auto Threshold (autoThreshold.groovy)

Applies ImageJ auto-threshold algorithms (or a fixed threshold) to one or more selected annotations, and uses the result to drive a QuPath pixel classifier for creating annotations, detections, measurements, or a live preview.

This is useful for applying auto-thresholding on a large batch of images by scripting. A GUI version developed by @iviecomarti based on this script is available at https://github.com/iviecomarti/GUI_AutoTH_QuPath

### Key feature: ROI-constrained histogram

Unlike a naive implementation that computes the threshold histogram from an annotation's **bounding box**, this script converts the QuPath ROI into an ImageJ ROI and restricts the `ImageProcessor` histogram to pixels **strictly inside the annotation shape**. This avoids the threshold being skewed by background or neighbouring tissue that falls inside the bounding box but outside the actual ROI, which is particularly important for irregular or concave annotations.

---

## Parameters

### Modalities & channels

The available `channel` options depend on the image type:

| Image type | `channel` options | Notes |
|---|---|---|
| Brightfield | `"HTX"` | Haematoxylin deconvolved channel |
| Brightfield | `"DAB"` | DAB deconvolved channel |
| Brightfield | `"Residual"` | Residual (third) deconvolved channel |
| Brightfield | `"Average"` | Mean of all RGB channels |
| Brightfield | `"Normalised"` | Min-max normalisation of HTX and DAB deconvolved stains, followed by channel mean |
| Fluorescence | `<channel name>` | Extracts the named channel (e.g. `"DAPI"`) |
| Fluorescence | `"Average"` | Mean of all channels |

For brightfield images, `"Average"` is typically used for positive staining detection on the mean RGB signal, while `"HTX"`/`"DAB"`/`"Residual"`/`"Normalised"` isolate a specific deconvolved stain.

### Threshold methods

Set `threshold` to either:
- A **numeric value** for a fixed threshold, or
- A **string** naming one of the 16 supported ImageJ auto-threshold algorithms:

  `"Default"`, `"Huang"`, `"Intermodes"`, `"IsoData"`, `"IJ_IsoData"`, `"Li"`, `"MaxEntropy"`, `"Mean"`, `"MinError"`, `"Minimum"`, `"Moments"`, `"Otsu"`, `"Percentile"`, `"RenyiEntropy"`, `"Shanbhag"`, `"Triangle"`, `"Yen"`

Additional options:
- `darkBackground` (`boolean`) : set `true` when the signal of interest is brighter than the background (e.g. fluorescence), so the auto-threshold algorithm is adapted accordingly.
- `thresholdFloor` : a minimum floor value applied if the calculated auto-threshold comes out too low (e.g. from mostly-empty ROIs). Set to `null` to disable.

### Downsampling strategy

Two independent downsample factors control different stages of the pipeline:

| Parameter | Affects | Guidance |
|---|---|---|
| `thresholdDownsample` | Resolution of the pixels used to build the **histogram** for calculating the threshold value | Increase for very large annotations to speed up histogram calculation; does not affect the resolution of created objects |
| `classifierDownsample` | Resolution at which the **pixel classifier** runs to produce output objects/measurements | Increase for coarser/faster object creation, decrease for finer object boundaries |

Both accept the standard QuPath downsample scale: `1` (Full), `2` (Very high), `4` (High), `8` (Moderate), `16` (Low), `32` (Very low), `64` (Extremely low).

### Background subtraction

Optional background correction can be applied prior to both the threshold histogram calculation and the pixel classifier. This is useful for correcting uneven illumination or diffuse background staining. Both methods can be enabled simultaneously.

- **Gaussian-weighted local mean subtraction** (`useGaussianWeightedLocalMeanSubtraction`) : local normalization using a Gaussian-weighted local mean and variance.
  - `backgroundSigmaMean` (µm) : sigma for the local mean.
  - `backgroundSigmaVariance` (µm) : sigma for local variance normalization; set to `0` to disable variance normalization (mean subtraction only).
- **Morphological top-hat subtraction** (`useMorphologicalTopHatSubtraction`) : subtracts a morphological opening from the original image to remove broad background structures.
  - `backgroundOpeningRadiusUm` (µm) : opening radius, automatically converted to pixels using the image's pixel calibration (falls back to 1.0 µm/px with a warning if pixel size is unavailable).

### Pixel classifier smoothing

- `classifierGaussianSigma` : applies Gaussian blur smoothing to the pixel classifier used for **object creation only**. It has no effect on the threshold value calculated from the histogram.

### Class assignments

- `classBelow` : class name (or `null`) assigned to pixels **below** the threshold.
- `classAbove` : class name (or `null`) assigned to pixels **above** the threshold.

Typically only one of the two is set (the other left `null`):
- Use `classBelow` for a positive **"Average"** channel signal on brightfield (e.g. tissue detection, where signal is darker than background).
- Use `classAbove` for positive deconvolved stain (e.g. DAB) or fluorescence channels (where signal is brighter than background).

### Output modes

Set `output` to one of:

| Value | Behaviour |
|---|---|
| `"annotation"` | Creates annotation objects from the pixel classifier |
| `"detection"` | Creates detection objects from the pixel classifier |
| `"measurement"` | Adds the thresholded area as a measurement on the parent annotation |
| `"preview"` | Shows a live overlay preview in the viewer (experimental). Reset with `getQuPath().getViewer().resetCustomPixelLayerOverlay()` |
| `"threshold value"` | Only calculates and stores the threshold value as an annotation measurement, without creating any objects |

The resulting threshold value is always saved as an annotation measurement (named according to the threshold method and assigned class(es)).

### Object creation filtering & options

- `minArea` : minimum area (in calibrated units) for created annotations/detections.
- `minHoleArea` : minimum area for holes within created objects.
- `classifierObjectOptions` : comma-separated list of options passed to `createAnnotationsFromPixelClassifier` / `createDetectionsFromPixelClassifier`:
  - `SPLIT` : split disconnected regions into separate objects.
  - `DELETE_EXISTING` : delete existing child objects before creating new ones.
  - `INCLUDE_IGNORED` : include regions marked as "Ignore*" classes.
  - `SELECT_NEW` : select the newly created objects.

---

## Requirements

- **QuPath** version supporting the scripting API used (`qupath.opencv.ops.ImageOps`, `qupath.opencv.ml.pixel.PixelClassifiers`, `TransformedServerBuilder`, `RegionFilter`) : QuPath v0.4+.
- **ImageJ integration** (bundled with QuPath) : used for `ij.ImagePlus` / `ij.process.ImageProcessor` and the `setAutoThreshold` auto-threshold algorithms.
- **OpenCV ops** (`qupath.opencv.ops.ImageOps`) : used for channel transforms, normalization, filtering (Gaussian blur, morphological opening), and threshold-based pixel classification.
- Compatible image types: **Brightfield** (with colour deconvolution stains set) and **Fluorescence**. Other image types will log an error and abort.
- At least one annotation must be selected (annotations with an `"Ignore*"` class are automatically excluded from the selection).

---

## Usage

1. Open an image and set colour deconvolution stains if using a brightfield channel (`HTX`/`DAB`/`Residual`/`Normalised`).
2. Select one or more annotations to threshold.
3. Open the Script Editor and paste `autoThreshold.groovy`.
4. Edit the parameters block at the top of the script, for example:

   ```groovy
   String channel = "DAB"
   def threshold = "Otsu"
   double thresholdDownsample = 4
   boolean darkBackground = false
   def thresholdFloor = null
   String output = "annotation"

   double classifierDownsample = 1
   double classifierGaussianSigma = 1.0
   String classBelow = null
   String classAbove = "DAB positive"

   boolean useGaussianWeightedLocalMeanSubtraction = false
   double backgroundSigmaMean = 25.0
   double backgroundSigmaVariance = 25.0

   boolean useMorphologicalTopHatSubtraction = true
   double backgroundOpeningRadiusUm = 12.0

   double minArea = 100000
   double minHoleArea = 1000
   String classifierObjectOptions = "SPLIT,DELETE_EXISTING"
   ```

5. Run the script. With `output = "annotation"`, new annotations classified as `classAbove`/`classBelow` are created inside each selected annotation, and the calculated threshold value is stored as a measurement.
6. To first check threshold values without creating objects, set `output = "threshold value"` (or `"preview"` for a visual check), review the measurements/overlay, then re-run with `output = "annotation"` or `"detection"` once satisfied.

---

*Author: Yau Mun Lim ([@yau-lim](https://github.com/yau-lim)), 2026*
