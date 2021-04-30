package qkbc;


import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.UIUtils;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import umontreal.ssj.gof.GofStat;
import umontreal.ssj.probdist.*;
import util.Pair;
import util.Vectors;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class DistributionPresenter extends ApplicationFrame {

    private static XYSeriesCollection getDistributionSamples(ContinuousDistribution dist, int nSamples) {
        XYSeries series = new XYSeries(dist.toString());
        for (int i = 0; i < nSamples; ++i) {
            double cd = 1.0 / (nSamples * 2) * (i * 2 + 1);
            double x = dist.inverseF(cd);
            series.add(x, dist.density(x));
        }
        return new XYSeriesCollection(series);
    }

    public DistributionPresenter(String title, ContinuousDistribution d, double[] samples) {
        super("Samples vs. Distribution");
        this.setContentPane(new ChartPanel(createChart(title, samples, d)));
        this.pack();
        UIUtils.centerFrameOnScreen(this);
        this.setVisible(true);
    }

    public DistributionPresenter(String title, ContinuousDistribution d, ArrayList<Double> samples) {
        this(title, d, samples.stream().mapToDouble(Double::doubleValue).toArray());
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

    private static JFreeChart createChart(String title, double[] samples, ContinuousDistribution d) {
        HistogramDataset samplesData = new HistogramDataset();
        samplesData.addSeries("Samples", samples, optimalFreedmanDiaconisNBins(samples));

        // Draw distribution first
        JFreeChart chart = ChartFactory.createXYLineChart(
                title, "Value", "Distribution density",
                getDistributionSamples(d, 1000),
                PlotOrientation.VERTICAL, true, true, false);

        // Draw samples
        XYPlot plot = chart.getXYPlot();
        plot.setDataset(1, samplesData);
        NumberAxis rangeAxis = new NumberAxis("Sample count");
        rangeAxis.setLabelFont(plot.getRangeAxis().getLabelFont());
        plot.setRangeAxis(1, rangeAxis);
        XYBarRenderer renderer = new XYBarRenderer();
        renderer.setShadowVisible(false);
        plot.setRenderer(1, renderer);
        plot.mapDatasetToRangeAxis(1, 1);

        // Style
        plot.getRenderer().setSeriesPaint(0, Color.BLUE);

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

    public void join() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }
}


public class DistributionFitter {
    public static double getPValueFromSamples(ContinuousDistribution d, double[] samples) {
        /*
        double[] pValues = new double[3];
        GofStat.kolmogorovSmirnov(samples, d, new double[3], pValues);
        return pValues[2];
        */
        return GofStat.andersonDarling(samples, d)[1];
    }

    public static double getPValueFromSample(ContinuousDistribution d, double sample) {
        return getPValueFromSamples(d, new double[]{sample});
    }

    // return Pair<dist, pValue>
    public static Pair<ContinuousDistribution, Double> fitContinuous(double[] values, Class<? extends ContinuousDistribution> distType) {
        ContinuousDistribution bestDist = null;
        double bestPValue = -1;

        ArrayList<ContinuousDistribution> distTypes = new ArrayList<>() {{
            try {
                if (distType == null || distType.equals(NormalDist.class)) {
                    add(NormalDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
            try {
                if (distType == null || distType.equals(ExponentialDist.class)) {
                    add(ExponentialDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
            try {
                if (distType == null || distType.equals(GammaDist.class)) {
                    add(GammaDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
            try {
                if (distType == null || distType.equals(WeibullDist.class)) {
                    add(WeibullDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
        }};

        for (ContinuousDistribution d : distTypes) {
            try {
                double pValue = getPValueFromSamples(d, values);
                if (bestDist == null || pValue > bestPValue) {
                    bestPValue = pValue;
                    bestDist = d;
                }
            } catch (Exception e) {
            }
        }

        return new Pair<>(bestDist, bestPValue);
    }

    // return Pair<dist, pValue>
    public static Pair<ContinuousDistribution, Double> fitContinuous(List<Double> values, Class<? extends ContinuousDistribution> distType) {
        return fitContinuous(values.stream().mapToDouble(Double::doubleValue).toArray(), distType);
    }

    public static Pair<ContinuousDistribution, Double> fitContinuous(double[] values) {
        return fitContinuous(values, null);
    }

    public static Pair<ContinuousDistribution, Double> fitContinuous(List<Double> values) {
        return fitContinuous(values, null);
    }

    public static void drawDistributionVsSamples(String title, ContinuousDistribution dist, double[] samples, boolean waitUntilClose) {
        DistributionPresenter p = new DistributionPresenter(title, dist, samples);
        if (waitUntilClose) {
            try {
                p.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Random r = new Random();
        Distribution d = new NormalDist(0, 1);

        ArrayList<Double> samples = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            samples.add(d.inverseF(r.nextDouble()));
        }

        Pair<ContinuousDistribution, Double> dist = fitContinuous(samples);

        System.out.println(dist);

        new DistributionPresenter(null, dist.first, samples);
    }
}
