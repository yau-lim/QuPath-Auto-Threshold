QuPath script to apply various auto-threshold methods from ImageJ on annotations.

This script has been optimised to apply auto-thresholding on the histogram from pixels strictly within the annotation ROI, instead of the bounding box.

Note that there are two different downsample parameters to set.
 * "thresholdDownsample" determines the downsample factor for the image and pixels that are used to calculate the threshold value from the histogram. This is useful to adjust if you have a very large annotation, which you should use a larger downsample factor.
 * "classifierDownsample" determines the downsample factor for the objects that are to be created. This is useful to adjust depending on the size/complexity of the resulting object ROI.

You can choose the desired output:
 * "threshold value" for just saving the threshold value to manually check the results without creating objects.
 * "preview" to show the thresholded area as an overlay (experimental feature).
 * "measurement" to save the thresholded area as a measurement in the parent annotation.
 * "annotation" to save the thresholded area as an annotation, making use of the classifier object options.
 * "detection" to save the thresholded area as a detection, making use of the classifier object options.

@author @yau-lim Yau Mun Lim (2024)
