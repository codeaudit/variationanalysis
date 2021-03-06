package org.campagnelab.dl.somatic.storage;


import com.google.protobuf.TextFormat;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.io.IOException;
import java.util.Set;


/**
 * Currently holds the main method. Jar takes two arguments
 * <p>
 * java -jar var-analysis.jar process /recordsPath/to/genotypes.parquet
 * creates mutated and randomized parquet file, then prints the latter
 * <p>
 * java -jar var-analysis.jar print /recordsPath/to/genotypes.parquet
 * prints the parquet file as is
 * <p>
 * Also, this jar should be a resource for Goby to output variations as a parquet file using its AvroVariationOutputFormat
 * class.
 * Created by rct66 on 5/17/16.
 *
 * @author rct66
 */
public class ProtobufPrinter {

    String path;
    boolean focusPrint = false;
    private int refIndex;
    private int position;
    private boolean customPosOnly = false;
    static int actualCount = 0;

    private int[] customPos = {
            40932570
    };
    private Set<Integer> posSet = new IntOpenHashSet(customPos);

    public static void main(String[] args) throws IOException {
        if (args.length<1) {
            System.err.println("usage: printer <parquet-file> [focus-ref-index focus-position]");
            System.exit(1);
        }
        ProtobufPrinter protobufPrinter = new ProtobufPrinter(args[0]);
        if (args.length >= 3) {
            // will only print the record(s) matching a specific position:
            protobufPrinter.setFocusOnPosition(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            System.out.println("Scanning for ");
        }
        protobufPrinter.print();
        System.out.println("actual count: " + actualCount);

    }

    private void setFocusOnPosition(int refIndex, int position) {
        focusPrint = true;
        this.refIndex = refIndex;
        this.position = position;
    }


    public ProtobufPrinter(String path) {
        this.path = path;
    }

    private void recordPrinter(BaseInformationRecords.BaseInformation base) throws IOException {
        TextFormat.print(base, System.out);
    }

    public void print() {
        try {
            RecordReader reader = new RecordReader(path);
            for (BaseInformationRecords.BaseInformation base : reader) {
                if (!(focusPrint || customPosOnly) ||
                        (base.getReferenceIndex() == refIndex && base.getPosition() == position) || (posSet.contains(base.getPosition()))) {
                    recordPrinter(base);
                    actualCount++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}