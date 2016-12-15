package org.campagnelab.dl.genotype.mappers;

import org.campagnelab.dl.framework.mappers.AbstractFeatureMapper1D;
import org.campagnelab.dl.framework.mappers.FeatureMapper;
import org.campagnelab.dl.framework.mappers.FeatureNameMapper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by fac2003 on 12/15/16.
 */
public class CountReorderingMapper extends AbstractFeatureMapper1D<BaseInformationRecords.BaseInformationOrBuilder> {
    FeatureNameMapper<BaseInformationRecords.BaseInformationOrBuilder> delegate;

    public CountReorderingMapper(FeatureNameMapper<BaseInformationRecords.BaseInformationOrBuilder> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getFeatureName(int featureIndex) {
        return delegate.getFeatureName(featureIndex);
    }

    int[] indices = new int[]{0};

    @Override
    public void mapFeatures(BaseInformationRecords.BaseInformationOrBuilder record, INDArray inputs, int indexOfRecord) {
        indices[0] = indexOfRecord;
        inputs.putScalar(indices, produceFeature(sortedCountRecord, 0));
    }


    @Override
    public int numberOfFeatures() {
        return delegate.numberOfFeatures();
    }

    private BaseInformationRecords.BaseInformationOrBuilder sortedCountRecord;
    private RecordCountSortHelper sortHelper = new RecordCountSortHelper();

    @Override
    public void prepareToNormalize(BaseInformationRecords.BaseInformationOrBuilder record, int indexOfRecord) {

        sortedCountRecord = sortHelper.sort(record);
        delegate.prepareToNormalize(sortedCountRecord, indexOfRecord);
    }

    @Override
    public float produceFeature(BaseInformationRecords.BaseInformationOrBuilder record, int featureIndex) {
        return delegate.produceFeature(sortedCountRecord, featureIndex);
    }
}
