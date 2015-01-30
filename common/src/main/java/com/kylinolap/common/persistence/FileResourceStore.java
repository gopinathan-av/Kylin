/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kylinolap.common.persistence;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;

import com.kylinolap.common.KylinConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileResourceStore extends ResourceStore {

    File root;

    private static Logger logger = LoggerFactory.getLogger(FileResourceStore.class);

    public FileResourceStore(KylinConfig kylinConfig) {
        super(kylinConfig);
        root = new File(kylinConfig.getMetadataUrl()).getAbsoluteFile();
        if (root.exists() == false)
            throw new IllegalArgumentException("File not exist by '" + kylinConfig.getMetadataUrl() + "': " + root.getAbsolutePath());
    }

    @Override
    protected ArrayList<String> listResourcesImpl(String resPath) throws IOException {
        String[] names = file(resPath).list();
        if (names == null) // not a directory
            return null;

        ArrayList<String> r = new ArrayList<String>(names.length);
        String prefix = resPath.endsWith("/") ? resPath : resPath + "/";
        for (String n : names) {
            r.add(prefix + n);
        }
        return r;
    }

    @Override
    protected boolean existsImpl(String resPath) throws IOException {
        File f = file(resPath);
        return f.exists() && f.isFile(); // directory is not considered a
                                         // resource
    }

    @Override
    protected InputStream getResourceImpl(String resPath) throws IOException {
        File f = file(resPath);
        if (f.exists() && f.isFile())
            return new FileInputStream(file(resPath));
        else {
            logger.info("path:" + f.toPath() + " not found");
            return null;
        }
    }

    @Override
    protected long getResourceTimestampImpl(String resPath) throws IOException {
        File f = file(resPath);
        return f.lastModified();
    }

    @Override
    protected void putResourceImpl(String resPath, InputStream content, long ts) throws IOException {
        File f = file(resPath);
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            IOUtils.copy(content, out);
        } finally {
            IOUtils.closeQuietly(out);
        }

        f.setLastModified(ts);
    }

    @Override
    protected long checkAndPutResourceImpl(String resPath, byte[] content, long oldTS, long newTS) throws IOException, IllegalStateException {
        File f = file(resPath);
        if ((f.exists() && f.lastModified() != oldTS) || (f.exists() == false && oldTS != 0))
            throw new IllegalStateException("Overwriting conflict " + resPath + ", expect old TS " + oldTS + ", but found " + f.lastModified());

        putResourceImpl(resPath, new ByteArrayInputStream(content), newTS);

        // some FS lose precision on given time stamp
        return getResourceTimestamp(resPath);
    }

    @Override
    protected void deleteResourceImpl(String resPath) throws IOException {
        File f = file(resPath);
        f.delete();
    }

    @Override
    protected String getReadableResourcePathImpl(String resPath) {
        return file(resPath).toString();
    }

    private File file(String resPath) {
        if (resPath.equals("/"))
            return root;
        else
            return new File(root, resPath);
    }

}
