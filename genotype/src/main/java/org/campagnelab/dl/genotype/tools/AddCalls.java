package org.campagnelab.dl.genotype.tools;


import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.logging.ProgressLogger;
import org.apache.commons.io.FilenameUtils;
import org.campagnelab.dl.framework.tools.arguments.AbstractTool;
import org.campagnelab.dl.somatic.storage.RecordReader;
import org.campagnelab.dl.somatic.storage.RecordWriter;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The addcalls object uses a map to create a new protobuf file with genotype calls.
 * <p>
 * Created by rct66 on 5/18/16.
 *
 * @author rct66
 */
public class AddCalls extends AbstractTool<AddCallsArguments> {


    static private Logger LOG = LoggerFactory.getLogger(AddCalls.class);

    public static void main(String[] args) {

        AddCalls tool = new AddCalls();
        tool.parseArguments(args, "AddCalls", tool.createArguments());
        tool.execute();
    }


    @Override
    //only supports genotypes encoded with a bar (|) delimiter
    public void execute() {
        int sampleIndex = args().sampleIndex;
        try {
            Object2ObjectMap<String,Int2ObjectMap<String>> chMap = (Object2ObjectMap<String,Int2ObjectMap<String>>)BinIO.loadObject(args().genotypeMap);
            RecordReader source = new RecordReader(args().inputFile);
            RecordWriter dest = new RecordWriter(FilenameUtils.removeExtension(args().inputFile)+"_called.sbi");


            ProgressLogger recordLogger = new ProgressLogger(LOG);
            recordLogger.expectedUpdates = source.numRecords();
            System.out.println(source.numRecords() + " records to label");
            int recordsLabeled = 0;
            recordloop:
            for (BaseInformationRecords.BaseInformation rec : source) {
                //first, we skip examples where all reads match the reference
                for (BaseInformationRecords.CountInfo count : rec.getSamples(sampleIndex).getCountsList()){
                    if (!count.getMatchesReference() && (count.getGenotypeCountForwardStrand() + count.getGenotypeCountReverseStrand()) != 0) {
                        continue recordloop;
                    }
                }
                BaseInformationRecords.BaseInformation.Builder buildRec = rec.toBuilder();
                int position = buildRec.getPosition();
                String chrom = buildRec.getReferenceId();
                String[] genotypes = new String[2];
                try {
                    genotypes = chMap.get(chrom).get(position).split("|");
                } catch (NullPointerException e) {
                    genotypes[0] = buildRec.getReferenceBase();
                    genotypes[1] = buildRec.getReferenceBase();
                }
                BaseInformationRecords.SampleInfo.Builder buildSample = buildRec.getSamples(sampleIndex).toBuilder();
                for (int i = 0; i < buildSample.getCountsCount(); i++){
                    BaseInformationRecords.CountInfo.Builder count = buildSample.getCounts(i).toBuilder();
                    boolean isCalled = (count.getToSequence().equals(genotypes[0])||count.getToSequence().equals(genotypes[1]));
                    count.setIsCalled(isCalled);
                    buildSample.setCounts(i,count);
                }
                buildRec.setSamples(sampleIndex,buildSample.build());
                dest.writeRecord(buildRec.build());
                recordLogger.update();
                recordsLabeled++;
                if (recordsLabeled >= 540000){
                    break;
                }
            }
            recordLogger.done();
            dest.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }



    @Override
    public AddCallsArguments createArguments() {
        return new AddCallsArguments();
    }



}