package com.kylinolap.job.cube;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.kylinolap.common.KylinConfig;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.cube.CubeSegment;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.cube.model.DimensionDesc;
import com.kylinolap.dict.DictionaryInfo;
import com.kylinolap.dict.DictionaryManager;
import com.kylinolap.job.dao.JobPO;
import com.kylinolap.job.exception.ExecuteException;
import com.kylinolap.job.execution.ExecutableContext;
import com.kylinolap.job.execution.ExecuteResult;
import com.kylinolap.job.impl.threadpool.AbstractExecutable;
import com.kylinolap.metadata.model.TblColRef;

public class MergeDictionaryStep extends AbstractExecutable {

    private static final String CUBE_NAME = "cubeName";
    private static final String SEGMENT_ID = "segmentId";
    private static final String MERGING_SEGMENT_IDS = "mergingSegmentIds";

    public MergeDictionaryStep() {
    }

    public MergeDictionaryStep(JobPO job) {
        super(job);
    }

    @Override
    protected ExecuteResult doWork(ExecutableContext context) throws ExecuteException {
        KylinConfig conf = context.getConfig();
        final CubeManager mgr = CubeManager.getInstance(conf);
        final CubeInstance cube = mgr.getCube(getCubeName());
        final CubeSegment newSegment = cube.getSegmentById(getSegmentId());
        final List<CubeSegment> mergingSegments = getMergingSegments(cube);
        
        try {
            makeDictForNewSegment(conf, cube, newSegment, mergingSegments);
            makeSnapshotForNewSegment(cube, newSegment, mergingSegments);

            mgr.updateCube(cube);
            return new ExecuteResult(ExecuteResult.State.SUCCEED, "succeed");
        } catch (IOException e) {
            logger.error("fail to merge dictionary or lookup snapshots", e);
            return new ExecuteResult(ExecuteResult.State.ERROR, e.getLocalizedMessage());
        }
    }
    
    private List<CubeSegment> getMergingSegments(CubeInstance cube) {
        List<String> mergingSegmentIds = getMergingSegmentIds();
        List<CubeSegment> result = Lists.newArrayListWithCapacity(mergingSegmentIds.size());
        for (String id : mergingSegmentIds) {
            result.add(cube.getSegmentById(id));
        }
        return result;
    }

    /**
     * For the new segment, we need to create dictionaries for it, too. For
     * those dictionaries on fact table, create it by merging underlying
     * dictionaries For those dictionaries on lookup table, just copy it from
     * any one of the merging segments, it's guaranteed to be consistent(checked
     * in CubeSegmentValidator)
     *
     * @param cube
     * @param newSeg
     * @throws IOException
     */
    private void makeDictForNewSegment(KylinConfig conf, CubeInstance cube, CubeSegment newSeg, List<CubeSegment> mergingSegments) throws IOException {
        HashSet<TblColRef> colsNeedMeringDict = new HashSet<TblColRef>();
        HashSet<TblColRef> colsNeedCopyDict = new HashSet<TblColRef>();
        DictionaryManager dictMgr = DictionaryManager.getInstance(conf);

        CubeDesc cubeDesc = cube.getDescriptor();
        for (DimensionDesc dim : cubeDesc.getDimensions()) {
            for (TblColRef col : dim.getColumnRefs()) {
                if (newSeg.getCubeDesc().getRowkey().isUseDictionary(col)) {
                    String dictTable = (String) dictMgr.decideSourceData(cubeDesc.getModel(), cubeDesc.getRowkey().getDictionary(col), col, null)[0];
                    if (cubeDesc.getFactTable().equalsIgnoreCase(dictTable)) {
                        colsNeedMeringDict.add(col);
                    } else {
                        colsNeedCopyDict.add(col);
                    }
                }
            }
        }

        for (TblColRef col : colsNeedMeringDict) {
            logger.info("Merging fact table dictionary on : " + col);
            List<DictionaryInfo> dictInfos = new ArrayList<DictionaryInfo>();
            for (CubeSegment segment : mergingSegments) {
                logger.info("Including fact table dictionary of segment : " + segment);
                DictionaryInfo dictInfo = dictMgr.getDictionaryInfo(segment.getDictResPath(col));
                dictInfos.add(dictInfo);
            }
            mergeDictionaries(dictMgr, newSeg, dictInfos, col);
        }

        for (TblColRef col : colsNeedCopyDict) {
            String path = mergingSegments.get(0).getDictResPath(col);
            newSeg.putDictResPath(col, path);
        }
    }

    private DictionaryInfo mergeDictionaries(DictionaryManager dictMgr, CubeSegment cubeSeg, List<DictionaryInfo> dicts, TblColRef col) throws IOException {
        DictionaryInfo dictInfo = dictMgr.mergeDictionary(dicts);
        cubeSeg.putDictResPath(col, dictInfo.getResourcePath());

        return dictInfo;
    }

    /**
     * make snapshots for the new segment by copying from one of the underlying
     * merging segments. it's guaranteed to be consistent(checked in
     * CubeSegmentValidator)
     *
     * @param cube
     * @param newSeg
     */
    private void makeSnapshotForNewSegment(CubeInstance cube, CubeSegment newSeg, List<CubeSegment> mergingSegments) {
        for (Map.Entry<String, String> entry : mergingSegments.get(0).getSnapshots().entrySet()) {
            newSeg.putSnapshotResPath(entry.getKey(), entry.getValue());
        }
    }

    public void setCubeName(String cubeName) {
        this.setParam(CUBE_NAME, cubeName);
    }

    private String getCubeName() {
        return getParam(CUBE_NAME);
    }

    public void setSegmentId(String segmentId) {
        this.setParam(SEGMENT_ID, segmentId);
    }

    private String getSegmentId() {
        return getParam(SEGMENT_ID);
    }

    public void setMergingSegmentIds(List<String> ids) {
        setParam(MERGING_SEGMENT_IDS, StringUtils.join(ids, ","));
    }

    private List<String> getMergingSegmentIds() {
        final String ids = getParam(MERGING_SEGMENT_IDS);
        if (ids != null) {
            final String[] splitted = StringUtils.split(ids, ",");
            ArrayList<String> result = Lists.newArrayListWithExpectedSize(splitted.length);
            for (String id: splitted) {
                result.add(id);
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

}
