package org.campagnelab.dl.somatic.intermediaries;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.util.XorShift1024StarRandom;
import org.campagnelab.dl.somatic.tools.Mutate;
import org.campagnelab.dl.somatic.utils.ProtoPredictor;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.predictions.ProtoHelper;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Introduce a strategy to introduce mutations.
 * Created by fac2003 on 7/19/16.
 */
public class FirstSimulationStrategy implements SimulationStrategy {
    long seed;

    public FirstSimulationStrategy(double deltaSmall, double deltaBig, double zygHeuristic, long seed) {
        setup(deltaSmall, deltaBig, zygHeuristic, seed, 0);
    }

    @Override
    public void setup(double deltaSmall, double deltaBig, double zygHeuristic, long seed, double canonThreshold) {
        this.seed = seed;
        rand = new XorShift1024StarRandom(seed);

        this.deltaSmall = deltaSmall;
        this.deltaBig = deltaBig;
        this.zygHeuristic = zygHeuristic;
    }

    public FirstSimulationStrategy() {

    }


    //delta will be halved in homozygous cases (to account for twice the reads at a base)
    //min fraction of bases mutated at a record (ceilinged fraction)
    double deltaSmall;
    //max fraction of bases mutated at a record (floored fraction)
    double deltaBig;
    //minimum proportion of counts to presume allele
    double zygHeuristic;
    final String[] STRING = new String[]{"A", "T", "C", "G"};
    Random rand;


    class MutationDirection {
        int oldBase;
        int newBase;
        double delta;
        /**
         * The frequency of mutation for the source allele. Can be different from somaticFrequency when two alleles or
         * more.
         */
        double somaticFrequency;

        @Override
        public String toString() {
            return String.format("%d>%d delta=%f somaticFrequency=%f", oldBase, newBase, delta, somaticFrequency);
        }

        /**
         * creates a mutationDirection object which defines where reads will be moved from,to, and how many
         *
         * @param oldBase   index of source base
         * @param newBase   index of destination base
         * @param delta     proportion source base reads to move (0 - 1 in heterozygous case, 0 - 0.5 in homozygous case)
         * @param frequency proportion of source allele reads to move (always 0 - 1, either delta or delta/2 in heterozygous case)
         */
        MutationDirection(int oldBase, int newBase, double delta, double frequency) {
            this.oldBase = oldBase;
            this.newBase = newBase;
            this.delta = delta;
            this.somaticFrequency = frequency;
        }
    }

    /**
     * @param counts arrays of counts, in genotype order.
     * @return
     */
    MutationDirection dirFromCounts(int referenceBase, int[] counts) {
        int numGenos = counts.length;
        int maxCount = 0;
        int secondMostCount = 0;
        int maxCountIdx = -1;
        int secondMostCountIdx = -1;
        int totalNumCounts = 0;
        //find highest count idx, second highest count idx, and record number of counts
        for (int i = 0; i < numGenos; i++) {
            totalNumCounts += counts[i];
            if (counts[i] > maxCount) {
                secondMostCountIdx = maxCountIdx;
                secondMostCount = maxCount;
                maxCountIdx = i;
                maxCount = counts[i];
            } else if (counts[i] > secondMostCount) {
                secondMostCountIdx = i;
                secondMostCount = counts[i];
            }
        }
        //no reads whatsoever
        if (maxCountIdx == -1) {
            return null;
        }
        boolean monozygotic;
        //all reads same base, monozygotic
        if (secondMostCountIdx == -1) {
            monozygotic = true;
        } else {
            //see if base with second most reads exceeds heuristic
            monozygotic = (zygHeuristic * totalNumCounts > counts[secondMostCountIdx]);
        }
        //make rand generator and generate proportion mutating bases
        //generate mutation rate for the source allele.
        double delta = deltaSmall + ((deltaBig - deltaSmall) * rand.nextDouble());
        double originalDelta = delta;
        int newBase = -1;
        int oldBase;
        int otherAlleleBase = -1;
        boolean allowed = false;
        if (monozygotic) {
            oldBase = maxCountIdx;
            //generate from non-max bases uniformly

            // homozygote site: somatic mutation likely to mutate only one of the two alleles, so we halve delta
            // to simulate this:
            if (rand.nextDouble() > 0.001) {
                // Most of the time, we follow the rule. Sometimes we don't because two mutations in the same site can occur,
                // albeit rarely (we don't know the exact frequency).
                // If we did not allow this at all, the model could never see any examples of the rare case, likely causing odd
                // behavior when a rare case shows up.
                delta = delta / 2;
            }
            while (!allowed) {
                newBase = rand.nextInt(numGenos);
                allowed = true;
                if (newBase == oldBase || newBase == 4 || newBase == referenceBase) {
                    //replace self case, N case
                    allowed = false;
                }
            }
        } else { //handle heterozygous case
            boolean mutatingAllele = rand.nextBoolean();
            oldBase = mutatingAllele ? maxCountIdx : secondMostCountIdx;
            otherAlleleBase = !mutatingAllele ? maxCountIdx : secondMostCountIdx;
            while (!allowed) {
                newBase = rand.nextInt(numGenos);
                allowed = true;
                if (newBase == oldBase || newBase == 4 || newBase == otherAlleleBase || newBase == referenceBase) {
                    //replace self case, other allele case, N case
                    allowed = false;
                }
            }

        }

        double somaticFrequency = originalDelta;

        return new MutationDirection(oldBase, newBase, delta, somaticFrequency);
    }

    @Override
    public int numberOfSamplesSupported() {
        return 2;
    }

    @Override
    public BaseInformationRecords.BaseInformation mutate(boolean makeSomatic,
                                                         BaseInformationRecords.BaseInformation record,
                                                         BaseInformationRecords.SampleInfo germlineSample,
                                                         BaseInformationRecords.SampleInfo otherSample,
                                                         SimulationCharacteristics sim) {

        BaseInformationRecords.BaseInformation.Builder baseBuild = record.toBuilder();
        baseBuild.setMutated(makeSomatic);
        int numSamples = record.getSamplesCount();
        for (int i = 0; i < numSamples - 1; i++) {
            baseBuild.setSamples(i, record.getSamples(i).toBuilder().setIsTumor(false));
        }
        baseBuild.setSamples(numSamples - 1, record.getSamples(numSamples - 1).toBuilder().setIsTumor(true));
        if (!makeSomatic) {
            // don't change counts if we are not to make the second sample somatic.
            return baseBuild.build();
        }
        BaseInformationRecords.SampleInfo somatic = baseBuild.getSamples(numSamples - 1);
        int numGenos = somatic.getCountsList().size();
        int[] forward = new int[numGenos];
        int[] backward = new int[numGenos];
        int[] sums = new int[numGenos];
        //fill declared arrays
        int i = 0;
        int referenceBase = -1;
        for (BaseInformationRecords.CountInfo count : somatic.getCountsList()) {
            forward[i] = count.getGenotypeCountForwardStrand();
            backward[i] = count.getGenotypeCountReverseStrand();
            sums[i] = forward[i] + backward[i];
            if (count.getMatchesReference() == true) {
                referenceBase = i;
            }
            i++;
        }
        assert referenceBase != -1 : "A count must match the reference.";
        MutationDirection dir = dirFromCounts(referenceBase, sums);

        if (dir == null) {
            return baseBuild.build();
        }

        final int oldBase = dir.oldBase;
        final int newBase = dir.newBase;
        final double delta = dir.delta;
        double frequency = dir.somaticFrequency;
        int changedCount = 0;
        int germlineCount = 0;
        int sumCount = 0;

        int fMutCount = 0;
        int oldCount = forward[oldBase];
        sumCount += oldCount;
        for (i = 0; i < oldCount; i++) {
            if (rand.nextDouble() < delta) {
                forward[oldBase]--;
                forward[newBase]++;
                fMutCount++;
                changedCount++;
            } else {
                germlineCount++;
            }
        }
        int bMutCount = 0;
        oldCount = backward[oldBase];
        sumCount += oldCount;
        for (i = 0; i < oldCount; i++) {
            if (rand.nextDouble() < delta) {
                backward[oldBase]--;
                backward[newBase]++;
                bMutCount++;
                changedCount++;
            } else {
                germlineCount++;
            }
        }
        //write to respective builders and return rebuild
        BaseInformationRecords.SampleInfo.Builder somaticBuild = somatic.toBuilder();


        //generate mutated numVariationsLists score lists (some boilerplate here...)
        //will add 1 to mutated variant counts using mutateIntegerListsVarAdd
        List<Integer> fromVC = new ObjectArrayList<Integer>();
        List<Integer> toVC = new ObjectArrayList<Integer>();
        if (somaticBuild.getCounts(oldBase).getNumVariationsInReadsCount() > 0) {

            //forward strand
            fromVC.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(oldBase).getNumVariationsInReadsList()));
            toVC.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(newBase).getNumVariationsInReadsList()));
            mutateIntegerListsVarAdd(fMutCount, fromVC, toVC, somaticBuild.getCounts(oldBase).getMatchesReference(), somaticBuild.getCounts(newBase).getMatchesReference());
        }

        //generate mutated insert sizes lists (some boilerplate here...)
        List<Integer> fromIS = new ObjectArrayList<Integer>();
        List<Integer> toIS = new ObjectArrayList<Integer>();
        if (somaticBuild.getCounts(oldBase).getNumVariationsInReadsCount() > 0) {

            //forward strand
            fromIS.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(oldBase).getInsertSizesList()));
            toIS.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(newBase).getInsertSizesList()));
            mutateIntegerLists(fMutCount, fromIS, toIS);
        }


        //generate mutated quality score lists (some boilerplate here...)
        //get old list of from scores
        List<Integer> fromForward = new ObjectArrayList<Integer>();
        List<Integer> fromBackward = new ObjectArrayList<Integer>();
        List<Integer> toForward = new ObjectArrayList<Integer>();
        List<Integer> toBackward = new ObjectArrayList<Integer>();
        if (somaticBuild.getCounts(oldBase).getQualityScoresForwardStrandCount() > 0 && somaticBuild.getCounts(oldBase).getQualityScoresReverseStrandCount() > 0) {

            //forward strand
            fromForward.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(oldBase).getQualityScoresForwardStrandList()));
            toForward.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(newBase).getQualityScoresForwardStrandList()));
            mutateIntegerLists(fMutCount, fromForward, toForward);

            //reverse strand
            fromBackward.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(oldBase).getQualityScoresReverseStrandList()));
            toBackward.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(newBase).getQualityScoresReverseStrandList()));
            mutateIntegerLists(bMutCount, fromBackward, toBackward);

        }

        //generate mutated readIndex lists
        List<Integer> fromForwardR = new ObjectArrayList<Integer>();
        List<Integer> fromBackwardR = new ObjectArrayList<Integer>();
        List<Integer> toForwardR = new ObjectArrayList<Integer>();
        List<Integer> toBackwardR = new ObjectArrayList<Integer>();
        if (somaticBuild.getCounts(oldBase).getReadIndicesForwardStrandCount() > 0 && somaticBuild.getCounts(oldBase).getReadIndicesReverseStrandCount() > 0) {

            //forward strand
            fromForwardR.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(oldBase).getReadIndicesForwardStrandList()));
            toForwardR.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(newBase).getReadIndicesForwardStrandList()));
            mutateIntegerLists(fMutCount, fromForwardR, toForwardR);

            //reverse strand
            fromBackwardR.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(oldBase).getReadIndicesReverseStrandList()));
            toBackwardR.addAll(ProtoPredictor.expandFreq(somaticBuild.getCounts(newBase).getReadIndicesReverseStrandList()));
            mutateIntegerLists(bMutCount, fromBackwardR, toBackwardR);


        }
        String mutatedAllele = "?";


        i = 0;
        for (BaseInformationRecords.CountInfo count : somaticBuild.getCountsList()) {
            BaseInformationRecords.CountInfo.Builder countBuild = count.toBuilder();
            countBuild.setGenotypeCountForwardStrand(forward[i]);
            countBuild.setGenotypeCountReverseStrand(backward[i]);
            if (i == oldBase) {
                //replace quality scores
                countBuild.clearQualityScoresForwardStrand();
                countBuild.clearQualityScoresReverseStrand();
                countBuild.addAllQualityScoresForwardStrand(ProtoHelper.compressFreq(fromForward));
                countBuild.addAllQualityScoresReverseStrand(ProtoHelper.compressFreq(fromBackward));

                //replace readIndices
                countBuild.clearReadIndicesForwardStrand();
                countBuild.clearReadIndicesReverseStrand();
                countBuild.addAllReadIndicesForwardStrand(ProtoHelper.compressFreq(fromForwardR));
                countBuild.addAllReadIndicesReverseStrand(ProtoHelper.compressFreq(fromBackwardR));

                //replace numVars
                countBuild.clearNumVariationsInReads();
                countBuild.addAllNumVariationsInReads(ProtoHelper.compressFreq(fromVC));

                //replace insert sizes
                countBuild.clearInsertSizes();
                countBuild.addAllInsertSizes(ProtoHelper.compressFreq(fromIS));


            } else if (i == newBase) {
                mutatedAllele = countBuild.getToSequence();
                //replace quality scores
                countBuild.clearQualityScoresForwardStrand();
                countBuild.clearQualityScoresReverseStrand();
                countBuild.addAllQualityScoresForwardStrand(ProtoHelper.compressFreq(toForward));
                countBuild.addAllQualityScoresReverseStrand(ProtoHelper.compressFreq(toBackward));

                //replace readIndices
                countBuild.clearReadIndicesForwardStrand();
                countBuild.clearReadIndicesReverseStrand();
                countBuild.addAllReadIndicesForwardStrand(ProtoHelper.compressFreq(toForwardR));
                countBuild.addAllReadIndicesReverseStrand(ProtoHelper.compressFreq(toBackwardR));
                baseBuild.setMutatedBase(count.getToSequence());

                //replace numVars
                countBuild.clearNumVariationsInReads();
                countBuild.addAllNumVariationsInReads(ProtoHelper.compressFreq(toVC));

                //replace insert sizes
                countBuild.clearInsertSizes();
                countBuild.addAllInsertSizes(ProtoHelper.compressFreq(toIS));


            }
            somaticBuild.setCounts(i, countBuild);
            i++;
        }
        somaticBuild.setFormattedCounts(Mutate.regenerateFormattedCounts(somaticBuild, mutatedAllele));
        baseBuild.setSamples(numSamples - 1, somaticBuild);
        baseBuild.setFrequencyOfMutation((float) frequency);
        //   System.out.printf("delta=%f somaticFreq=%f oldCount=%d%n", delta, frequency, sumCount);
        // String newBaseString = newBase<STRING.length? STRING[newBase]:"N";
        //baseBuild.setMutatedBase(newBaseString);
        baseBuild.setIndexOfMutatedBase(newBase);
        return baseBuild.build();
    }

    @Override
    public void setSeed(long seed) {
        rand = new XorShift1024StarRandom(seed);
    }

    private void mutateIntegerLists(int fMutCount, List<Integer> source, List<Integer> dest) {
        Collections.shuffle(source, rand);
        dest.addAll(source.subList(0, fMutCount));
        List<Integer> tmp = new IntArrayList(source.subList(fMutCount, source.size()));
        source.clear();
        source.addAll(tmp);

    }


    private void mutateIntegerListsVarAdd(int fMutCount, List<Integer> source, List<Integer> dest, boolean sourceIsRef, boolean destIsRef) {
        //compute increment (-1,0,or 1)
        int increment = 0;
        if (sourceIsRef) increment++;
        if (destIsRef) increment--;

        int newDestIndex = dest.size();
        Collections.shuffle(source, rand);
        dest.addAll(source.subList(0, fMutCount));
        for (int i = newDestIndex; i < dest.size(); i++) {
            dest.set(i, dest.get(i) + increment);
        }
        List<Integer> tmp = new IntArrayList(source.subList(fMutCount, source.size()));
        source.clear();
        source.addAll(tmp);

    }

}
