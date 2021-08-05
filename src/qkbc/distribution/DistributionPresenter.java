package qkbc.distribution;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.UIUtils;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.jfree.graphics2d.svg.SVGUtils;
import umontreal.ssj.probdist.ContinuousDistribution;
import util.Vectors;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;

public class DistributionPresenter extends ApplicationFrame {
    public static boolean LOG_SCALED_HISTOGRAM = false;

    private JFreeChart chart;

    private static XYSeriesCollection getDistributionSamples(ContinuousDistribution dist, int nSamples) {
        XYSeries series = new XYSeries(dist.toString());
        double start = dist.inverseF(IntegralDistributionApproximator.INVERSE_F_IGNORE_THRESHOLD);
        double step = (dist.inverseF(1 - IntegralDistributionApproximator.INVERSE_F_IGNORE_THRESHOLD) - start) / (nSamples - 1);
        for (int i = 0; i < nSamples; ++i) {
            series.add(start, dist.density(start));
            start += step;
        }
        return new XYSeriesCollection(series);
    }

    public DistributionPresenter(String title, ContinuousDistribution d, double[] samples,
                                 boolean drawDistribution, boolean drawHistogram, boolean drawSamples, boolean legend) {
        super("Samples vs. Distribution");
        this.chart = createChart(title, samples, d, drawDistribution, drawHistogram, drawSamples, legend);
    }

    public void present(boolean waitUntilClose) {
        this.setContentPane(new ChartPanel(chart));
        this.pack();
        UIUtils.centerFrameOnScreen(this);
        this.setVisible(true);
        if (waitUntilClose) {
            try {
                synchronized (lock) {
                    lock.wait();
                }
            } catch (InterruptedException e) {
            }
        }
    }

    public XYPlot getPlot() {
        return chart.getXYPlot();
    }

    public void printToFile(String file, int width, int height) {
        try {
            SVGGraphics2D g2 = new SVGGraphics2D(width, height);
            Rectangle r = new Rectangle(0, 0, width, height);
            chart.draw(g2, r);
            File f = new File(file);
            SVGUtils.writeToSVG(f, g2.getSVGElement());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int optimalFreedmanDiaconisNBins(double[] samples) {
        if (samples.length <= 1) {
            return samples.length;
        }
        DescriptiveStatistics ds = new DescriptiveStatistics(samples);
        double iqr = ds.getPercentile(75) - ds.getPercentile(25);
        int nBins = (int) Math.round((Vectors.max(samples) - Vectors.min(samples)) / (2 * iqr / Math.pow(samples.length, 1.0 / 3)));
        return Math.max(nBins, 1);
    }

    private static JFreeChart createChart(String title, double[] samples, ContinuousDistribution d,
                                          boolean drawDistribution, boolean drawHistogram, boolean drawSamples, boolean legend) {
        HistogramDataset histogramData = new HistogramDataset();
        histogramData.addSeries("Histogram", samples, optimalFreedmanDiaconisNBins(samples));

        XYSeries dotsData = new XYSeries("Samples");
        for (double v : samples) {
            dotsData.add(v, 0);
        }

        // Draw samples first
        JFreeChart chart = ChartFactory.createXYLineChart(title, "Value", null, new XYSeriesCollection(),
                PlotOrientation.VERTICAL, legend, true, false);
        XYPlot plot = chart.getXYPlot();
        // change BG
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        int rangeAxisIndex = 0;
        // Draw distribution
        if (drawDistribution) {
            plot.setDataset(0, getDistributionSamples(d, 1000));
            NumberAxis rangeAxis = new NumberAxis("Distribution density");
            rangeAxis.setLabelFont(plot.getRangeAxis().getLabelFont());
            plot.setRangeAxis(rangeAxisIndex, rangeAxis);
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            renderer.setSeriesShapesVisible(0, false);
            renderer.setSeriesPaint(0, Color.BLUE);
            renderer.setSeriesStroke(0, new BasicStroke(1.5f));
            plot.setRenderer(0, renderer);
            plot.mapDatasetToRangeAxis(0, rangeAxisIndex);
            rangeAxisIndex++;
        }

        if (drawSamples) {
            plot.setDataset(1, new XYSeriesCollection(dotsData));
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            renderer.setSeriesLinesVisible(0, false);
            renderer.setSeriesShape(0, new Rectangle2D.Double(-0.25, -4, 0.5, 4));
            renderer.setSeriesPaint(0, Color.DARK_GRAY);
            plot.setRenderer(1, renderer);
        }

        // Draw histogram
        if (drawHistogram) {
            if (LOG_SCALED_HISTOGRAM) {
                XYSeries scaled = new XYSeries("Scaled_histogram");
                for (int i = 0; i < histogramData.getItemCount(0); ++i) {
                    scaled.add((double) histogramData.getX(0, i), Math.log((double) histogramData.getY(0, i) + 1));
                }
                XYSeriesCollection ds = new XYSeriesCollection(scaled);
                ds.setAutoWidth(true);
                plot.setDataset(2, ds);
            } else {
                plot.setDataset(2, histogramData);
            }
            NumberAxis rangeAxis = new NumberAxis("Sample count");
            rangeAxis.setLabelFont(plot.getRangeAxis().getLabelFont());
            plot.setRangeAxis(rangeAxisIndex, rangeAxis);
            XYBarRenderer renderer = new XYBarRenderer();
            renderer.setShadowVisible(false);
            renderer.setBarPainter(new StandardXYBarPainter()); // don't use gradient
            plot.setRenderer(2, renderer);
            plot.mapDatasetToRangeAxis(2, rangeAxisIndex);
            rangeAxisIndex++;
        }

        return chart;
    }

    @Override
    public void windowClosing(WindowEvent event) {
        this.dispose();
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private Object lock = new Object();
}
