/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.importers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectMetadata;
import com.google.refine.importers.TreeImportUtilities.ImportColumnGroup;
import com.google.refine.importers.parsers.TreeParser;
import com.google.refine.importers.parsers.XmlParser;
import com.google.refine.model.Project;

public class XmlImporter implements StreamImporter {

    final static Logger logger = LoggerFactory.getLogger("XmlImporter");

    public static final int BUFFER_SIZE = 64 * 1024;

    @Override
    public void read(
        InputStream inputStream,
        Project project,
        ProjectMetadata metadata, Properties options
    ) throws ImportException {
        logger.trace("XmlImporter.read");
        PushbackInputStream pis = new PushbackInputStream(inputStream,BUFFER_SIZE);

        String[] recordPath = null;
        {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes_read = 0;
            try {//fill the buffer with data
                while (bytes_read < BUFFER_SIZE) {
                    int c = pis.read(buffer, bytes_read, BUFFER_SIZE - bytes_read);
                    if (c == -1) break;
                    bytes_read +=c ;
                }
                pis.unread(buffer, 0, bytes_read);
            } catch (IOException e) {
                throw new ImportException("Read error",e);
            }

            InputStream iStream = new ByteArrayInputStream(buffer, 0, bytes_read);
            TreeParser parser = new XmlParser(iStream);
            if (options.containsKey("importer-record-tag")) {
                try{
                    recordPath = XmlImportUtilities.detectPathFromTag(
                        parser,
                        options.getProperty("importer-record-tag"));
                }catch(Exception e){
                    // silent
                    // e.printStackTrace();
                }
            } else {
                recordPath = XmlImportUtilities.detectRecordElement(parser);
            }
        }

        if (recordPath == null)
            return;

        ImportColumnGroup rootColumnGroup = new ImportColumnGroup();
        XmlImportUtilities.importTreeData(new XmlParser(pis), project, recordPath, rootColumnGroup);
        XmlImportUtilities.createColumnsFromImport(project, rootColumnGroup);

        project.columnModel.update();
    }

    @Override
    public boolean canImportData(String contentType, String fileName) {
        if (contentType != null) {
            contentType = contentType.toLowerCase().trim();

            if("application/xml".equals(contentType) ||
                      "text/xml".equals(contentType) ||
                      "application/rss+xml".equals(contentType) ||
                      "application/atom+xml".equals(contentType)) {
                return true;
            }
        } else if (fileName != null) {
            fileName = fileName.toLowerCase();
            if (
                    fileName.endsWith(".xml") ||
                    fileName.endsWith(".atom") ||
                    fileName.endsWith(".rss")
                ) {
                return true;
            }
        }
        return false;
    }

}