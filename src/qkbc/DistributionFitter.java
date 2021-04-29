package qkbc;


import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.UIUtils;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.xy.XYDataset;
import umontreal.ssj.gof.GofStat;
import umontreal.ssj.probdist.*;
import util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class DistributionPresenter extends ApplicationFrame {

    public static class DistributionXYDataset extends AbstractXYDataset implements XYDataset {
        private int nSamples;
        private ContinuousDistribution dist;

        public DistributionXYDataset(ContinuousDistribution dist, int nSamples) {
            this.dist = dist;
            this.nSamples = nSamples;
        }

        @Override
        public Number getX(int series, int item) {
            double cd = 1.0 / (nSamples * 2) * (item * 2 + 1);
            return dist.inverseF(cd);
        }

        @Override
        public Number getY(int series, int item) {
            double cd = 1.0 / (nSamples * 2) * (item * 2 + 1);
            return dist.density(dist.inverseF(cd));
        }

        @Override
        public int getSeriesCount() {
            return 1;
        }

        @Override
        public Comparable getSeriesKey(int series) {
            return dist.toString();
        }

        @Override
        public int getItemCount(int series) {
            return nSamples;
        }
    }

    public DistributionPresenter(double[] samples, ContinuousDistribution d) {
        super(DistributionPresenter.class.getName());
        this.setContentPane(new ChartPanel(createChart(samples, d)));
        this.pack();
        UIUtils.centerFrameOnScreen(this);
        this.setVisible(true);
    }

    public DistributionPresenter(ArrayList<Double> samples, ContinuousDistribution d) {
        this(samples.stream().mapToDouble(Double::doubleValue).toArray(), d);
    }

    private static JFreeChart createChart(double[] samples, ContinuousDistribution d) {
        HistogramDataset samplesData = new HistogramDataset();
        samplesData.addSeries("Samples", samples, 100);

        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(new NumberAxis("Value"));

        XYBarRenderer renderer = new XYBarRenderer();
        renderer.setShadowVisible(false);
        XYPlot samplesPlot = new XYPlot(samplesData,
                null, new NumberAxis("Sample count"),
                renderer);

        XYPlot distPlot = new XYPlot(new DistributionXYDataset(d, 1000),
                null, new NumberAxis("Distribution density"),
                new StandardXYItemRenderer());

        plot.add(samplesPlot, 1);
        plot.add(distPlot, 1);
        plot.setOrientation(PlotOrientation.VERTICAL);


        JFreeChart chart = new JFreeChart("Samples vs. Distribution", JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        return chart;
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

    public static void main(String[] args) {
        Random r = new Random();
        Distribution d = new NormalDist(0, 1);

        ArrayList<Double> samples = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            samples.add(d.inverseF(r.nextDouble()));
        }

        Pair<ContinuousDistribution, Double> dist = fitContinuous(samples);

        System.out.println(dist);

        new DistributionPresenter(samples, dist.first);
    }
}
