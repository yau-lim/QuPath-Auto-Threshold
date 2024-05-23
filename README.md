QuPath script to apply various auto-threshold methods from ImageJ on annotations.

This is useful for applying auto-thresholding on a large batch of images by scripting. A GUI version developed by @iviecomarti based on this script is available at https://github.com/iviecomarti/GUI_AutoTH_QuPath

This script has been optimised to apply auto-thresholding on the histogram from pixels strictly within the annotation ROI, instead of the bounding box.

Note that there are two different downsample parameters to set.
 * "thresholdDownsample" determines the downsample factor for the image and pixels that are used to calculate the threshold value from the histogram. This is useful to adjust if you have a very large annotation, which you should use a larger downsample factor.
 * "classifierDownsample" determines the downsample factor for the objects that are to be created. This is useful to adjust depending on the size/complexity of the resulting object ROI.

Specify the threshold method to use by setting the "threshold" variable.
 * For fixed threshold, set "threshold" with the desired threshold value.
 * For auto threshold, set "threshold" with the desired threshold method. The following are available: "Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen

You can choose the desired output:
 * "threshold value" for just saving the threshold value to manually check the results without creating objects.
 * "preview" to show the thresholded area as an overlay (experimental feature).
 * "measurement" to save the thresholded area as a measurement in the parent annotation.
 * "annotation" to save the thresholded area as an annotation, making use of the classifier object options.
 * "detection" to save the thresholded area as a detection, making use of the classifier object options.

@author Yau Mun Lim @yau-lim (2024)
