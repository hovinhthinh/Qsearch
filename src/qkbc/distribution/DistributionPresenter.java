package qkbc.distribution;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
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
    private JFreeChart chart;

    private static XYSeriesCollection getDistributionSamples(ContinuousDistribution dist, int nSamples) {
        XYSeries series = new XYSeries(dist.toString());
        for (int i = 0; i < nSamples; ++i) {
            double cd = 1.0 / (nSamples * 2) * (i * 2 + 1);
            double x = dist.inverseF(cd);
            series.add(x, dist.density(x));
        }
        return new XYSeriesCollection(series);
    }

    public DistributionPresenter(String title, ContinuousDistribution d, double[] samples, boolean drawHistogram, boolean drawSamples, boolean legend) {
        super("Samples vs. Distribution");
        this.chart = createChart(title, samples, d, drawHistogram, drawSamples, legend);
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
                                          boolean drawHistogram, boolean drawSamples, boolean legend) {
        HistogramDataset histogramData = new HistogramDataset();
        histogramData.addSeries("Histogram", samples, optimalFreedmanDiaconisNBins(samples));

        XYSeries dotsData = new XYSeries("Samples");
        for (double v : samples) {
            dotsData.add(v, 0);
        }

        // Draw samples first
        JFreeChart chart = ChartFactory.createXYLineChart(
                title, "Value", "Distribution density",
                new XYSeriesCollection(drawSamples ? dotsData : new XYSeries("Samples")),
                PlotOrientation.VERTICAL, legend, true, false);
        XYPlot plot = chart.getXYPlot();
        // change BG
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer0 = new XYLineAndShapeRenderer();
        renderer0.setSeriesLinesVisible(0, false);
        renderer0.setSeriesShape(0, new Rectangle2D.Double(-0.5, -8, 1, 8));
        plot.setRenderer(0, renderer0);

        // Draw distribution
        plot.setDataset(1, getDistributionSamples(d, 1000));
        XYLineAndShapeRenderer renderer1 = new XYLineAndShapeRenderer();
        renderer1.setSeriesShapesVisible(0, false);
        plot.setRenderer(1, renderer1);

        // Draw histogram
        if (drawHistogram) {
            plot.setDataset(2, histogramData);
            NumberAxis rangeAxis = new NumberAxis("Sample count");
            rangeAxis.setLabelFont(plot.getRangeAxis().getLabelFont());
            plot.setRangeAxis(1, rangeAxis);
            XYBarRenderer renderer2 = new XYBarRenderer();
            renderer2.setShadowVisible(false);
            plot.setRenderer(2, renderer2);
            plot.mapDatasetToRangeAxis(2, 1);
        }

        // Style
        plot.getRenderer(0).setSeriesPaint(0, Color.DARK_GRAY);
        plot.getRenderer(1).setSeriesPaint(0, Color.BLUE);
        plot.getRenderer(1).setSeriesStroke(0, new BasicStroke(1.5f));

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
