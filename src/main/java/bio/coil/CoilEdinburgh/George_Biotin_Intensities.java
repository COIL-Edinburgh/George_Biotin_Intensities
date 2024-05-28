/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package bio.coil.CoilEdinburgh;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.ChannelSplitter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * </p>
 * <p>
 * You should replace the parameter fields with your own inputs and outputs,
 * and replace the {@link run} method implementation with your own logic.
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Users Plugins>George Biotin Intensites")
public class George_Biotin_Intensities<T extends RealType<T>> implements Command {
    //
    // Feel free to add more parameters here...
    //
    @Parameter
    private FormatService formatService;

    @Parameter
    private DatasetIOService datasetIOService;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService ops;

    @Parameter
    private ROIService roiService;

    @Parameter(label = "Open Folder: ", style="directory")
    public File filePath;

    RoiManager roiManager;

    String filename;
    @Override
    public void run() {

        File[] files = filePath.listFiles();
        //Set up roiManager
        if (RoiManager.getInstance() != null) {
            roiManager = RoiManager.getInstance();
        } else {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        assert files != null;
        for (File file : files) {
            if (file.toString().contains(".dv") && !file.toString().contains(".nd2 ")) {
                //open file
                ImagePlus imp = IJ.openImage(file.getPath());
                filename = imp.getShortTitle();
                //segment ch1
                Roi[] centromeres = segment(imp);
                //measure in ch1,2,3
                double[][] results = measureIntensities(centromeres, imp);
                //calculate backgrounds
                double[] backgrounds = calculateBackground(imp);

                //update results
                try {
                    MakeResults(results, backgrounds);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                ImagePlus outputImage = outputImage(imp, centromeres);
                outputImage.show();
                IJ.save(WindowManager.getCurrentImage(), Paths.get(filePath.toString(), filename) + "_Overlay.tif");

                IJ.run("Close All", "");
            }
        }
    }

    private ImagePlus outputImage(ImagePlus imp, Roi[] rois) {
        ImagePlus[] split = ChannelSplitter.split(imp.duplicate());
        split[1].show();
        split[2].show();
        split[3].show();
        ImagePlus overlay = IJ.createImage("Overlay", "16-bit", split[1].getWidth(), split[1].getHeight(),
                split[1].getNChannels(), split[1].getNSlices(), split[1].getNFrames());
        overlay.show();
        drawNumbers(overlay, rois);
        IJ.run("Merge Channels...", "c2=[" + split[1].getTitle() + "] c4=[Overlay] c6=[" +
                split[2].getTitle() +"] c7=[" + split[3].getTitle() + "] create");
        return WindowManager.getCurrentImage();
    }

    private void drawNumbers(ImagePlus ProjectedWindow, Roi[] rois) {
        ImageProcessor ip = ProjectedWindow.getProcessor();
        Font font = new Font("SansSerif", Font.BOLD, 20);
        ip.setFont(font);
        ip.setColor(Color.white);
        for (int i = 0; i < rois.length; i++) {
            String cellnumber = String.valueOf(i + 1);
            ip.draw(rois[i]);
            ip.drawString(cellnumber, (int) rois[i].getContourCentroid()[0], (int) rois[i].getContourCentroid()[1]);
            ProjectedWindow.updateAndDraw();
        }
    }

    private double[] calculateBackground(ImagePlus imp){
        ImagePlus[] split = ChannelSplitter.split(imp);
        ImagePlus dup = split[2].duplicate();
        dup.show();
        IJ.run(dup, "Gaussian Blur...", "sigma=25");
        IJ.setAutoThreshold(dup, "Huang dark");
        IJ.run(dup, "Analyze Particles...", "add");
        Roi outline = roiManager.getRoi(0);
        roiManager.reset();
        dup.setRoi(outline);
        IJ.setAutoThreshold(dup, "Triangle dark");
        IJ.run(dup, "Analyze Particles...", "add");
        Roi[] foreground = roiManager.getRoisAsArray();
        roiManager.reset();

        double[] output = new double[3];
        for (int i = 1; i< 4; i++){
            split[i].show();
            split[i].setRoi(outline);
            ImageProcessor ip = split[i].getProcessor();
            double totalIntensity = ip.getStatistics().mean * outline.getStatistics().area;
            double totalArea = outline.getStatistics().area;
            double foregroundarea = 0;
            double foregroundintensity = 0;
            for (Roi points : foreground) {
                ip = split[i].getProcessor();
                ip.setRoi(points);
                foregroundarea += points.getStatistics().area;
                foregroundintensity += ip.getStatistics().mean * points.getStatistics().area;
            }
            output[i-1] = (totalIntensity - foregroundintensity)/ (totalArea - foregroundarea);
            IJ.log((totalIntensity/totalArea)+" "+ foregroundintensity/foregroundarea+" "+ output[i-1]+"");
        }
        return output;
    }

    private double[][] measureIntensities(Roi[] rois, ImagePlus imp){
        ImagePlus[] split = ChannelSplitter.split(imp);
        double[][] output = new double[3][rois.length];
        for( int i = 1; i< 4; i++){
            split[i].show();
            for (int j = 0; j < rois.length; j++) {
                split[i].setRoi(rois[j]);
                ImageProcessor ip = split[i].getProcessor();
                output[i-1][j]= ip.getStatistics().mean;
            }
        }
        return output;
    }

    private Roi[] segment(ImagePlus image){
        ImagePlus[] split = ChannelSplitter.split(image);
        ImagePlus imp = split[1];
        imp.show();
        IJ.run(imp, "Subtract Background...", "rolling=10");
        IJ.run(imp, "Gaussian Blur...", "sigma=2");
        IJ.setAutoThreshold(imp, "Moments dark");
        IJ.run(imp, "Analyze Particles...", "size=5-Infinity pixel exclude add");
        Roi[] ROIs = roiManager.getRoisAsArray();
        roiManager.reset();
        return ROIs;
    }

    public void MakeResults(double[][] results, double[] backgrounds ) throws IOException {
        Date date = new Date(); // This object contains the current date value
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy, hh:mm:ss");
        String CreateName = Paths.get(String.valueOf(filePath),filePath.getName()+"_Results.csv").toString();
        boolean exists = Files.exists(Paths.get(CreateName));
        try {
            FileWriter fileWriter = new FileWriter(CreateName, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            if(!exists){
                bufferedWriter.newLine();
                bufferedWriter.write(formatter.format(date));
                bufferedWriter.newLine();
                bufferedWriter.newLine();
                bufferedWriter.write("File, Number,Ch2 , Ch3, Ch4, Ch2-background, Ch3-background, Ch4-background , Ch3/Ch2 (Background Subtracted), Ch4/Ch2 (Background Subtracted)");//write header 1
                bufferedWriter.newLine();
            }
            for (int i =0; i < results[0].length; i++){//for each slice create and write the output string
                bufferedWriter.newLine();
                bufferedWriter.write(filename+ ","+(i+1)+","+results[0][i]+"," +results[1][i]+","+results[2][i]+","+
                        (results[0][i]-backgrounds[0])+","+(results[1][i]-backgrounds[1])+","+(results[2][i]-backgrounds[2])+
                        ","+(results[1][i]-backgrounds[1])/(results[0][i]-backgrounds[0])+","+(results[2][i]-backgrounds[2])/(results[0][i]-backgrounds[0]));
            }
            bufferedWriter.close();
        } catch (IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
    }


    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(George_Biotin_Intensities.class, true);
    }

}
