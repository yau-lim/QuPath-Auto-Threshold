/* PARAMETERS */
String channel = "Average" // "HTX", "DAB", "Residual" for BF ; use channel name for FL ; "Average":Mean of all channels for BF/FL
def threshold = "Huang" // Input threshold value for fixed threshold. Use the following for auto threshold: "Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"
double thresholdDownsample = 64 // 1:Full, 2:Very high, 4:High, 8:Moderate, 16:Low, 32:Very low, 64:Extremely low
boolean darkBackground = false // Adapt threshold method for dark backgrounds
def thresholdFloor = 225 // Set a threshold floor value in case auto threshold is too low. Set null to disable
String output = "annotation" // "annotation", "detection", "measurement", "preview", "threshold value"
// Reset preview overlay with "getQuPath().getViewer().resetCustomPixelLayerOverlay()"

double classifierDownsample = 64 // 1:Full, 2:Very high, 4:High, 8:Moderate, 16:Low, 32:Very low, 64:Extremely low
double classifierGaussianSigma = 1.0 // Strength of gaussian blurring for pixel classifier (not used in calculation of threshold)
String classBelow = "Tissue" // null or "Class Name"; use this for positive "Average" channel on brightfield
String classAbove = null // null or "Class Name"; use this for positive deconvoluted or fluorescence channels

/* Background subtraction parameters */
boolean useGaussianWeightedLocalMeanSubtraction = false
double backgroundSigmaMean = 25.0 // um; used by GaussianWeightedLocalMean
double backgroundSigmaVariance = 25.0 // um; used by GaussianWeightedLocalMean (set 0 to disable variance normalization)

boolean useMorphologicalTopHatSubtraction = false
double backgroundOpeningRadiusUm = 12.0 // um; used by MorphologicalTopHat

/* Create object parameters */
double minArea = 100000 // Minimum area for annotations to be created
double minHoleArea = 1000 // Minimum area for holes in annotations to be created
String classifierObjectOptions = "SPLIT,DELETE_EXISTING" // "SPLIT,DELETE_EXISTING,INCLUDE_IGNORED,SELECT_NEW"


def annotations = getSelectedObjects().findAll{it.getPathClass() != getPathClass("Ignore*")}

if (annotations) {
    autoThreshold(annotations, channel, threshold, thresholdDownsample, darkBackground, thresholdFloor, output, classifierDownsample, classifierGaussianSigma, classBelow, classAbove, useGaussianWeightedLocalMeanSubtraction, backgroundSigmaMean, backgroundSigmaVariance, useMorphologicalTopHatSubtraction, backgroundOpeningRadiusUm, minArea, minHoleArea, classifierObjectOptions)
} else {
    logger.warn("No annotations selected.")
}

/* IMPORTS */
import qupath.lib.images.servers.TransformedServerBuilder
import qupath.lib.roi.interfaces.ROI
import qupath.imagej.tools.IJTools
import qupath.lib.images.PathImage
import qupath.lib.regions.RegionRequest
import ij.ImagePlus
import ij.process.ImageProcessor
import qupath.opencv.ml.pixel.PixelClassifiers
import qupath.lib.gui.viewer.OverlayOptions
import qupath.lib.gui.viewer.RegionFilter
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay
import qupath.lib.images.servers.ColorTransforms.ColorTransform
import qupath.opencv.ops.ImageOp
import qupath.opencv.ops.ImageOps
import qupath.lib.objects.classes.PathClass

/* FUNCTIONS */
def autoThreshold(annotations, channel, threshold, thresholdDownsample, darkBackground, thresholdFloor, output, classifierDownsample, classifierGaussianSigma, classBelow, classAbove, useGaussianWeightedLocalMeanSubtraction, backgroundSigmaMean, backgroundSigmaVariance, useMorphologicalTopHatSubtraction, backgroundOpeningRadiusUm, minArea, minHoleArea, classifierObjectOptions) {
    if (!(annotations instanceof Collection) || annotations.isEmpty()) {
        logger.warn("No annotations provided.")
        return
    }

    def context = prepareContext(channel, threshold, thresholdDownsample, darkBackground, thresholdFloor, classifierDownsample, classifierGaussianSigma, classBelow, classAbove, useGaussianWeightedLocalMeanSubtraction, backgroundSigmaMean, backgroundSigmaVariance, useMorphologicalTopHatSubtraction, backgroundOpeningRadiusUm, classifierObjectOptions)
    if (context == null) {
        return
    }

    annotations.each { annotation ->
        applyAutoThreshold(annotation, thresholdDownsample, output, minArea, minHoleArea, context)
    }
}

def prepareContext(channel, threshold, thresholdDownsample, darkBackground, thresholdFloor, classifierDownsample, classifierGaussianSigma, classBelow, classAbove, useGaussianWeightedLocalMeanSubtraction, backgroundSigmaMean, backgroundSigmaVariance, useMorphologicalTopHatSubtraction, backgroundOpeningRadiusUm, classifierObjectOptions) {
    def qupath = getQuPath()
    def imageData = getCurrentImageData()
    def imageType = imageData.getImageType()
    def server = imageData.getServer()
    def cal = server.getPixelCalibration()
    def thresholdResolution = cal.createScaledInstance(thresholdDownsample, thresholdDownsample)
    def classifierResolution = cal.createScaledInstance(classifierDownsample, classifierDownsample)
    def classifierChannel

    if (imageType.toString().contains("Brightfield")) {
        def stains = imageData.getColorDeconvolutionStains()

        if (channel == "HTX") {
            server = new TransformedServerBuilder(server).deconvolveStains(stains, 1).build()
            classifierChannel = ColorTransforms.createColorDeconvolvedChannel(stains, 1)
        } else if (channel == "DAB") {
            server = new TransformedServerBuilder(server).deconvolveStains(stains, 2).build()
            classifierChannel = ColorTransforms.createColorDeconvolvedChannel(stains, 2)
        } else if (channel == "Residual") {
            server = new TransformedServerBuilder(server).deconvolveStains(stains, 3).build()
            classifierChannel = ColorTransforms.createColorDeconvolvedChannel(stains, 3)
        } else if (channel == "Average") {
            server = new TransformedServerBuilder(server).averageChannelProject().build()
            classifierChannel = ColorTransforms.createMeanChannelTransform()
        } else if (channel == "Normalised") {
            classifierChannel = [ColorTransforms.createColorDeconvolvedChannel(stains, 1), ColorTransforms.createColorDeconvolvedChannel(stains, 2)]
            def imageDataOp = ImageOps.buildImageDataOp(classifierChannel)
                .appendOps(
                    ImageOps.Normalize.minMax(),
                    ImageOps.Channels.mean()
                )
            server = ImageOps.buildServer(imageData, imageDataOp, thresholdResolution)
        }
    } else if (imageType.toString() == "Fluorescence") {
        if (channel == "Average") {
            server = new TransformedServerBuilder(server).averageChannelProject().build()
            classifierChannel = ColorTransforms.createMeanChannelTransform()
        } else {
            server = new TransformedServerBuilder(server).extractChannels(channel).build()
            classifierChannel = ColorTransforms.createChannelExtractor(channel)
        }
    } else {
        logger.error("Current image type not compatible with auto threshold.")
        return
    }

    // Optional background subtraction methods.
    List<ImageOp> backgroundOps = new ArrayList<>()
    def thresholdServer = server

    if (useGaussianWeightedLocalMeanSubtraction || useMorphologicalTopHatSubtraction) {
        logger.info("Background subtraction enabled: GaussianWeightedLocalMean=${useGaussianWeightedLocalMeanSubtraction}, MorphologicalTopHat=${useMorphologicalTopHatSubtraction}")
    }

    if (useGaussianWeightedLocalMeanSubtraction) {
        logger.info("Applying background subtraction: GaussianWeightedLocalMean (sigmaMean=${backgroundSigmaMean} um, sigmaVariance=${backgroundSigmaVariance} um)")
        backgroundOps.add(ImageOps.Normalize.localNormalization(backgroundSigmaMean, backgroundSigmaVariance))
    }

    if (useMorphologicalTopHatSubtraction) {
        double pixelSizeMicrons = cal.getAveragedPixelSizeMicrons()
        if (!Double.isFinite(pixelSizeMicrons) || pixelSizeMicrons <= 0) {
            logger.warn("Pixel size unavailable or invalid; using 1.0 um/px for MorphologicalTopHat radius conversion.")
            pixelSizeMicrons = 1.0
        }

        int openingRadiusPx = Math.max(1, Math.round(backgroundOpeningRadiusUm / pixelSizeMicrons) as int)
        String pixelSizeMicronsFormatted = String.format(java.util.Locale.US, "%.3f", pixelSizeMicrons)
        logger.info("Applying background subtraction: MorphologicalTopHat (openingRadius=${backgroundOpeningRadiusUm} um, pixelSize=${pixelSizeMicronsFormatted} um/px, openingRadiusPx=${openingRadiusPx})")
        backgroundOps.add(
            ImageOps.Core.splitSubtract(
                ImageOps.Core.identity(),
                ImageOps.Filters.opening(openingRadiusPx)
            )
        )
    }

    // Check if threshold is Double (for fixed threshold) or String (for auto threshold)
    String thresholdMethod
    if (threshold instanceof String) {
        thresholdMethod = threshold
    } else {
        thresholdMethod = "Fixed"
    }

    // Apply the selected algorithm
    def validThresholds = ["Fixed", "Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"]

    if (!(thresholdMethod in validThresholds)) {
        logger.error("Invalid auto-threshold method")
        return
    }

    boolean thresholdServerPreDownsampled = false
    if (thresholdMethod != "Fixed" && !backgroundOps.isEmpty()) {
        def backgroundOp = ImageOps.buildImageDataOp(classifierChannel)
            .appendOps(*backgroundOps)
        thresholdServer = ImageOps.buildServer(imageData, backgroundOp, thresholdResolution)
        thresholdServerPreDownsampled = true
    }

    def classificationBelow
    if (classBelow instanceof PathClass) {
        classificationBelow = classBelow
    } else if (classBelow instanceof String) {
        classificationBelow = getPathClass(classBelow)
    } else if (classBelow == null) {
        classificationBelow = classBelow
    }
    
    def classificationAbove
    if (classAbove instanceof PathClass) {
        classificationAbove = classAbove
    } else if (classAbove instanceof String) {
        classificationAbove = getPathClass(classAbove)
    } else if (classAbove == null) {
        classificationAbove = classAbove
    }

    Map<Integer, PathClass> classifications = new LinkedHashMap<>()
    classifications.put(0, classificationBelow)
    classifications.put(1, classificationAbove)

    def parsedClassifierObjectOptions = null
    if (classifierObjectOptions) {
        parsedClassifierObjectOptions = classifierObjectOptions.split(',')
        def allowedOptions = ["SPLIT", "DELETE_EXISTING", "INCLUDE_IGNORED", "SELECT_NEW"]
        boolean checkValid = parsedClassifierObjectOptions.every{allowedOptions.contains(it)}

        if (!checkValid) {
            logger.warn("Invalid create object options")
            return
        }
    }

    return [
        qupath: qupath,
        imageData: imageData,
        classifierChannel: classifierChannel,
        thresholdServer: thresholdServer,
        thresholdServerPreDownsampled: thresholdServerPreDownsampled,
        classifierResolution: classifierResolution,
        backgroundOps: backgroundOps,
        thresholdMethod: thresholdMethod,
        threshold: threshold,
        thresholdFloor: thresholdFloor,
        darkBackground: darkBackground,
        classifierGaussianSigma: classifierGaussianSigma,
        classificationBelow: classificationBelow,
        classificationAbove: classificationAbove,
        classifications: classifications,
        classifierObjectOptions: parsedClassifierObjectOptions
    ]
}

def applyAutoThreshold(annotation, thresholdDownsample, output, minArea, minHoleArea, context) {
    double thresholdValue
    if (context.thresholdMethod == "Fixed") {
        thresholdValue = context.threshold
    } else {
        logger.info("Running auto-threshold histogram for ${annotation}")

        // Determine threshold value by auto threshold method
        ROI pathROI = annotation.getROI() // Get QuPath ROI
        // If thresholdServer was already built at thresholdDownsample resolution, request it at 1x.
        double histogramRequestDownsample = context.thresholdServerPreDownsampled ? 1.0 : thresholdDownsample
        PathImage pathImage = IJTools.convertToImagePlus(context.thresholdServer, RegionRequest.createInstance(context.thresholdServer.getPath(), histogramRequestDownsample, pathROI)) // Get PathImage within bounding box of annotation
        def ijRoi = IJTools.convertToIJRoi(pathROI, pathImage) // Convert QuPath ROI into ImageJ ROI
        ImagePlus imagePlus = pathImage.getImage() // Convert PathImage into ImagePlus
        ImageProcessor ip = imagePlus.getProcessor() // Get ImageProcessor from ImagePlus
        ip.setRoi(ijRoi) // Add ImageJ ROI to the ImageProcessor to limit the histogram to within the ROI only

        if (context.darkBackground) {
            ip.setAutoThreshold("${context.thresholdMethod} dark")
        } else {
            ip.setAutoThreshold("${context.thresholdMethod}")
        }

        thresholdValue = ip.getMaxThreshold()
        if (thresholdValue != null && context.thresholdFloor != null && thresholdValue < context.thresholdFloor) {
            thresholdValue = context.thresholdFloor
        }
    }

    // If specified output is "threshold value, return threshold value in annotation measurements
    if (output == "threshold value") {
        logger.info("${context.thresholdMethod} threshold value: ${thresholdValue}")
        annotation.measurements.put("${context.thresholdMethod} threshold value" as String, thresholdValue)
        return thresholdValue
    }

    // Define parameters for pixel classifier
    List<ImageOp> ops = new ArrayList<>()

    if (!context.backgroundOps.isEmpty()) {
        ops.addAll(context.backgroundOps)
    }

    if (context.classifierGaussianSigma > 0) {
        ops.add(ImageOps.Filters.gaussianBlur(context.classifierGaussianSigma))
    }

    ops.add(ImageOps.Threshold.threshold(thresholdValue))

    // Create pixel classifier
    def transformer = ImageOps.buildImageDataOp(context.classifierChannel).appendOps(*ops)
    def classifier = PixelClassifiers.createClassifier(
        transformer,
        context.classifierResolution,
        context.classifications
    )

    // Apply classifier
    selectObjects(annotation)
    if (output == "annotation") {
        logger.info("Creating annotations in ${annotation} from ${context.thresholdMethod}: ${thresholdValue}")
        
        if (context.classifierObjectOptions) {
            createAnnotationsFromPixelClassifier(classifier, minArea, minHoleArea, context.classifierObjectOptions)
        } else {
            createAnnotationsFromPixelClassifier(classifier, minArea, minHoleArea)
        }
    }
    if (output == "detection") {
        logger.info("Creating detections in ${annotation} from ${context.thresholdMethod}: ${thresholdValue}")

        if (context.classifierObjectOptions) {
            createDetectionsFromPixelClassifier(classifier, minArea, minHoleArea, context.classifierObjectOptions)
        } else {
            createDetectionsFromPixelClassifier(classifier, minArea, minHoleArea)
        }
    }
    if (output == "measurement") {
        logger.info("Measuring thresholded area in ${annotation} from ${context.thresholdMethod}: ${thresholdValue}")
        def measurementID = "${context.thresholdMethod} threshold"
        addPixelClassifierMeasurements(classifier, measurementID)
    }
    if (output == "preview") {
        logger.info("Showing preview of ${annotation} with ${context.thresholdMethod}: ${thresholdValue}")
        OverlayOptions overlayOption = context.qupath.getOverlayOptions()
        overlayOption.setPixelClassificationRegionFilter(RegionFilter.StandardRegionFilters.ANY_ANNOTATIONS) // RegionFilter.StandardRegionFilters.ANY_ANNOTATIONS
        PixelClassificationOverlay previewOverlay = PixelClassificationOverlay.create(overlayOption, classifier)
        previewOverlay.setLivePrediction(true)
        context.qupath.getViewer().setCustomPixelLayerOverlay(previewOverlay)
    }
    
    if (context.classificationBelow == null) {
        annotation.measurements.put("${context.thresholdMethod}: ${context.classificationAbove.toString()} threshold value" as String, thresholdValue)
    }
    if (context.classificationAbove == null) {
        annotation.measurements.put("${context.thresholdMethod}: ${context.classificationBelow.toString()} threshold value" as String, thresholdValue)
    }
    if (context.classificationBelow != null && context.classificationAbove != null) {
        annotation.measurements.put("${context.thresholdMethod} threshold value" as String, thresholdValue)
    }
}
