# George Biotin Intensities

This plugin takes a folder of 3 channel '.dv' files. The plugin use thresholding in channel 1 to segment the ROIs and then
measures the mean intensity for each ROI in all three channels. Background signal is calculated using thresholding in channel
2 to exclude areas where there are cells. A results file and overlay image showing the ROIs analysed are created and saved in 
the original input folder. 

#### The plugin outputs:
- A filename_Results.csv file with the image intensities data for each cell in each file, with and without background subtraction.
- A filename_Overlay.tif output image with the numbered cells and thresholded regions used for intensity measurements.



This repository is based off an example Maven project implementing an ImageJ2 command.
Visit [this link](https://github.com/imagej/example-imagej2-command/generate)
